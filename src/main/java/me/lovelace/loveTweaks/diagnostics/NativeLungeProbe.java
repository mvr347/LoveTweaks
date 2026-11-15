package me.lovelace.loveTweaks.diagnostics;

import io.netty.channel.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * Bounded, read-only probes for decoded native actions on an already connected player's channel.
 */
final class NativeLungeProbe implements AutoCloseable {
  private record Stage(
      String probe, String handler, String type, AtomicInteger stab, AtomicInteger swing) {}

  private final Channel channel;
  private final Function<Object, String> action;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final String prefix = "lovetweaks_lunge_" + UUID.randomUUID() + "_";
  private volatile List<Stage> stages = List.of();
  private volatile String status = "ожидает установки";

  static NativeLungeProbe open(Player player) throws ReflectiveOperationException {
    Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
    Object listener = field(serverPlayer, "connection");
    Object connection = field(listener, "connection");
    Channel channel = (Channel) field(connection, "channel");
    Method getAction =
        Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket")
            .getMethod("getAction");
    var probe =
        new NativeLungeProbe(
            channel,
            packet -> {
              String name = packet.getClass().getSimpleName();
              if (name.equals("ServerboundSwingPacket")) return "SWING";
              if (!name.equals("ServerboundPlayerActionPacket")) return "";
              try {
                return String.valueOf(getAction.invoke(packet));
              } catch (ReflectiveOperationException ignored) {
                return "";
              }
            });
    probe.install();
    return probe;
  }

  private static Object field(Object instance, String name) throws ReflectiveOperationException {
    for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(name);
  }

  NativeLungeProbe(Channel channel, Function<Object, String> action) {
    this.channel = channel;
    this.action = action;
  }

  void install() {
    if (!channel.isRegistered() || !channel.isOpen()) {
      status = "канал закрыт или не зарегистрирован";
      return;
    }
    try {
      channel
          .eventLoop()
          .execute(
              () -> {
                synchronized (NativeLungeProbe.this) {
                  if (closed.get() || !channel.isRegistered() || !channel.isOpen()) return;
                  var added = new ArrayList<Stage>();
                  try {
                    var selected =
                        new ArrayList<>(
                            channel.pipeline().toMap().entrySet().stream()
                                .filter(entry -> entry.getValue() instanceof ChannelInboundHandler)
                                .toList());
                    if (selected.size() > 24) {
                      var last = selected.getLast();
                      selected = new ArrayList<>(selected.subList(0, 23));
                      selected.add(last);
                    }
                    for (var entry : selected) {
                      var stage =
                          new Stage(
                              prefix + added.size(),
                              entry.getKey(),
                              entry.getValue().getClass().getName(),
                              new AtomicInteger(),
                              new AtomicInteger());
                      channel
                          .pipeline()
                          .addBefore(
                              entry.getKey(),
                              stage.probe(),
                              new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)
                                    throws Exception {
                                  if (!closed.get()) {
                                    try {
                                      String value = action.apply(msg);
                                      if ("STAB".equals(value)) stage.stab().incrementAndGet();
                                      else if ("SWING".equals(value))
                                        stage.swing().incrementAndGet();
                                    } catch (RuntimeException ignored) {
                                      /* Observation must never block a packet. */
                                    }
                                  }
                                  ctx.fireChannelRead(msg);
                                }
                              });
                      added.add(stage);
                    }
                    stages = List.copyOf(added);
                    status = "включена";
                  } catch (RuntimeException failure) {
                    stages = List.copyOf(added);
                    status = "ошибка установки: " + failure.getClass().getSimpleName();
                    remove();
                  }
                }
              });
    } catch (RejectedExecutionException closing) {
      status = "цикл канала остановлен";
    }
  }

  List<String> summary() {
    var lines = new ArrayList<String>();
    lines.add("Пакетная проверка STAB: " + status);
    for (Stage stage : stages) {
      lines.add(
          "Перед "
              + stage.handler()
              + " ("
              + stage.type()
              + "): STAB="
              + stage.stab().get()
              + ", взмахи="
              + stage.swing().get());
    }
    lines.add(
        "Нули до декодера нормальны. Исчезновение STAB между этапами сужает поиск; отсутствие STAB"
            + " везде не отличает клиента от раннего пакетного фильтра.");
    return List.copyOf(lines);
  }

  private void remove() {
    for (Stage stage : stages) {
      if (channel.pipeline().get(stage.probe()) != null) channel.pipeline().remove(stage.probe());
    }
  }

  @Override
  public synchronized void close() {
    if (!closed.compareAndSet(false, true)) return;
    // Netty schedules pipeline removal on its owning executor; no Bukkit calls on network threads.
    try {
      remove();
    } catch (RejectedExecutionException closing) {
      /* Closing channels discard their pipeline. */
    }
  }
}

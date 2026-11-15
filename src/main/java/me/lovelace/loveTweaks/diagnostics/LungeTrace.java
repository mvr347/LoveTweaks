package me.lovelace.loveTweaks.diagnostics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.entity.EntityLungeEvent;
import java.util.*;
import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Ten-second, read-only tracing. Probes run after existing listeners of each priority. */
@SuppressWarnings("UnstableApiUsage")
public final class LungeTrace implements Listener, AutoCloseable {
  private final LoveTweaks plugin;
  private final Map<UUID, Session> sessions = new HashMap<>();
  private static final List<Class<? extends Event>> TYPES =
      List.of(
          EntityLungeEvent.class,
          PlayerInteractEvent.class,
          PlayerVelocityEvent.class,
          PlayerMoveEvent.class,
          PlayerTeleportEvent.class);

  private static final class Session {
    final UUID player, recipient;
    final boolean console;
    final TraceWindow window = new TraceWindow(24);
    final List<String> lines = new ArrayList<>();
    BukkitTask expiry;
    NativeLungeProbe packets;
    int lunges, interactions, velocity, moves, cancelledLunges;

    Session(UUID player, CommandSender recipient) {
      this.player = player;
      this.recipient = recipient instanceof Player p ? p.getUniqueId() : null;
      console = !(recipient instanceof Player);
    }
  }

  public LungeTrace(LoveTweaks plugin) {
    this.plugin = plugin;
  }

  public void start(CommandSender recipient, Player target) {
    if (!recipient.hasPermission("lovetweaks.admin")) return;
    if (sessions.containsKey(target.getUniqueId())) finish(target.getUniqueId());
    if (sessions.size() >= 4) {
      recipient.sendMessage(plugin.getLoveTweaksConfig().message("lunge-limit").replace('&', '§'));
      return;
    }
    if (sessions.isEmpty()) {
      try {
        registerProbes();
      } catch (RuntimeException failure) {
        HandlerList.unregisterAll(this);
        throw failure;
      }
    }
    var session = new Session(target.getUniqueId(), recipient);
    sessions.put(target.getUniqueId(), session);
    recipient.sendMessage(
        plugin
            .getLoveTweaksConfig()
            .message("lunge-started")
            .replace("<player>", target.getName())
            .replace('&', '§'));
    try {
      session.expiry =
          plugin.getServer().getScheduler().runTaskLater(plugin, () -> finish(session.player), 200);
      initial(session, target);
      try {
        session.packets = NativeLungeProbe.open(target);
      } catch (ReflectiveOperationException | RuntimeException failure) {
        emit(session, "Пакетная диагностика недоступна: " + failure.getClass().getSimpleName());
      }
    } catch (RuntimeException | LinkageError failure) {
      emit(session, "Проверка не запущена: " + failure.getClass().getSimpleName());
      finish(session.player);
    }
  }

  private void registerProbes() {
    plugin
        .getServer()
        .getPluginManager()
        .registerEvent(
            PlayerQuitEvent.class,
            this,
            EventPriority.MONITOR,
            (listener, event) -> finish(((PlayerQuitEvent) event).getPlayer().getUniqueId()),
            plugin,
            false);
    for (var type : TYPES)
      for (var priority : EventPriority.values())
        plugin
            .getServer()
            .getPluginManager()
            .registerEvent(
                type, this, priority, (listener, event) -> observe(event, priority), plugin, false);
  }

  private void initial(Session session, Player p) {
    emit(session, "Сервер: " + plugin.getServer().getVersion());
    emit(
        session,
        "Чары запрещены целиком: "
            + yes(plugin.getLoveTweaksConfig().isDisableAllEnchantments())
            + "; Починка запрещена: "
            + yes(plugin.getLoveTweaksConfig().isDisableMending()));
    emit(
        session,
        "Режим: "
            + p.getGameMode()
            + "; голод: "
            + p.getFoodLevel()
            + "/20; вода: "
            + yes(p.isInWater())
            + "; транспорт: "
            + yes(p.isInsideVehicle())
            + "; элитры: "
            + yes(p.isGliding())
            + "; полёт="
            + yes(p.isFlying())
            + "; неуязвимость="
            + yes(p.isInvulnerable()));
    var speed = p.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
    emit(
        session,
        "Скорость атаки="
            + (speed == null ? "неизвестна" : speed.getValue())
            + "; готовность атаки="
            + p.getAttackCooldown()
            + "; скорость ходьбы="
            + p.getWalkSpeed());
    item(session, "Основная рука", p.getInventory().getItemInMainHand());
    item(session, "Вторая рука", p.getInventory().getItemInOffHand());
    for (var type : TYPES) {
      String listeners = handlers(type, null);
      emit(
          session,
          type.getSimpleName()
              + " — слушатели: "
              + (listeners.isEmpty() ? "нет, кроме диагностики" : listeners));
    }
  }

  private void item(Session session, String hand, ItemStack item) {
    int level =
        item.getEnchantments().entrySet().stream()
            .filter(e -> e.getKey().getKey().equals(NamespacedKey.minecraft("lunge")))
            .mapToInt(Map.Entry::getValue)
            .max()
            .orElse(0);
    emit(
        session,
        hand
            + ": "
            + item.getType()
            + "; настоящий minecraft:lunge="
            + level
            + "; piercing_weapon="
            + yes(item.hasData(DataComponentTypes.PIERCING_WEAPON))
            + "; kinetic_weapon="
            + yes(item.hasData(DataComponentTypes.KINETIC_WEAPON))
            + "; minimum_attack_charge="
            + item.getData(DataComponentTypes.MINIMUM_ATTACK_CHARGE));
  }

  private void observe(Event event, EventPriority priority) {
    Player player =
        event instanceof PlayerEvent p
            ? p.getPlayer()
            : event instanceof EntityLungeEvent l && l.getEntity() instanceof Player p ? p : null;
    if (player == null) return;
    Session session = sessions.get(player.getUniqueId());
    if (session == null) return;
    String state;
    if (event instanceof EntityLungeEvent l)
      state = "отменён=" + yes(l.isCancelled()) + ", сила=" + l.getLungePower();
    else if (event instanceof PlayerInteractEvent i)
      state = i.getAction() + ", предмет=" + i.useItemInHand() + ", блок=" + i.useInteractedBlock();
    else if (event instanceof PlayerVelocityEvent v)
      state =
          "отменён="
              + yes(v.isCancelled())
              + ", скорость²="
              + String.format(Locale.ROOT, "%.4f", v.getVelocity().lengthSquared());
    else if (event instanceof PlayerMoveEvent m)
      state =
          "отменён="
              + yes(m.isCancelled())
              + ", цель="
              + (m.getTo() == null
                  ? "нет"
                  : String.format(
                      Locale.ROOT,
                      "%.3f,%.3f,%.3f",
                      m.getTo().getX(),
                      m.getTo().getY(),
                      m.getTo().getZ()));
    else return;
    if (event instanceof PlayerTeleportEvent teleport)
      state = "причина=" + teleport.getCause() + ", " + state;
    boolean movement = event instanceof PlayerMoveEvent && !(event instanceof PlayerTeleportEvent);
    String change = session.window.observe(event, priority, state, !movement);
    if (change != null) {
      String candidates = handlers(event.getClass(), priority);
      emit(
          session,
          event.getEventName()
              + " ["
              + priority
              + "] "
              + change
              + (candidates.isEmpty() ? "" : "; слушатели этой группы: " + candidates));
    }
    if (priority == EventPriority.MONITOR) {
      if (event instanceof EntityLungeEvent l) {
        session.lunges++;
        if (l.isCancelled()) session.cancelledLunges++;
      } else if (event instanceof PlayerInteractEvent) session.interactions++;
      else if (event instanceof PlayerVelocityEvent) session.velocity++;
      else if (event instanceof PlayerMoveEvent) session.moves++;
    }
  }

  private String handlers(Class<? extends Event> type, EventPriority priority) {
    try {
      var list = (HandlerList) type.getMethod("getHandlerList").invoke(null);
      return Arrays.stream(list.getRegisteredListeners())
          .filter(r -> r.getListener() != this && (priority == null || r.getPriority() == priority))
          .map(
              r ->
                  r.getPlugin().getName()
                      + ":"
                      + r.getListener().getClass().getSimpleName()
                      + "["
                      + r.getPriority()
                      + "]")
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
    } catch (ReflectiveOperationException error) {
      return "недоступны: " + error.getClass().getSimpleName();
    }
  }

  private void emit(Session session, String line) {
    // Bound the complete report, including metadata and listener names, independently of event
    // rate.
    if (session.lines.size() >= 48) return;
    String bounded = line.length() > 2000 ? line.substring(0, 2000) + "…" : line;
    session.lines.add(bounded);
    plugin.getLogger().info("[Lunge:" + session.player.toString().substring(0, 8) + "] " + bounded);
    var recipient =
        session.console
            ? plugin.getServer().getConsoleSender()
            : plugin.getServer().getPlayer(session.recipient);
    if (recipient != null && recipient.hasPermission("lovetweaks.admin") && !session.console)
      recipient.sendMessage("§6[Lunge] §7" + bounded);
  }

  private void finish(UUID id) {
    Session session = sessions.remove(id);
    if (session == null) return;
    if (session.expiry != null) session.expiry.cancel();
    if (session.packets != null) session.packets.close();
    // Summary is always emitted, even when event traces hit their bound.
    session.lines.clear();
    emit(
        session,
        "Итог: события Lunge="
            + session.lunges
            + ", отменены="
            + session.cancelledLunges
            + ", взаимодействия="
            + session.interactions
            + ", импульсы скорости="
            + session.velocity
            + ", события перемещения="
            + session.moves);
    if (session.packets != null) {
      for (String line : session.packets.summary()) emit(session, line);
    }
    if (session.lunges == 0)
      emit(
          session,
          "Событие Lunge не возникло: проверьте показанные компоненты предмета, условия ванили и"
              + " обработчики взаимодействий/пакетов. Античит по одному этому выводу причиной не"
              + " считается.");
    emit(
        session,
        "Проверка завершена. Названия слушателей — кандидаты по приоритету, а не доказательство"
            + " виновности конкретного плагина.");
    if (sessions.isEmpty()) HandlerList.unregisterAll(this);
  }

  private static String yes(boolean value) {
    return value ? "да" : "нет";
  }

  @Override
  public void close() {
    for (var session : sessions.values()) {
      if (session.expiry != null) session.expiry.cancel();
      if (session.packets != null) session.packets.close();
    }
    sessions.clear();
    HandlerList.unregisterAll(this);
  }
}

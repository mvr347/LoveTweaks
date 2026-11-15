package me.lovelace.loveTweaks.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class NativeLungeProbeTest {
  @Test
  void observesWhereStabStopsWithoutChangingPacketDelivery() {
    var channel = new EmbeddedChannel();
    channel
        .pipeline()
        .addLast(
            "decode",
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                ctx.fireChannelRead(msg.equals("RAW") ? "STAB" : msg);
              }
            });
    channel
        .pipeline()
        .addLast(
            "filter",
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (!msg.equals("STAB")) ctx.fireChannelRead(msg);
              }
            });
    channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
    var original = channel.pipeline().names();
    var probe = new NativeLungeProbe(channel, msg -> (String) msg);
    try {
      probe.install();
      channel.runPendingTasks();
      assertFalse(channel.writeInbound("RAW"));
      assertTrue(channel.writeInbound("SWING"));
      assertEquals("SWING", channel.readInbound());
      var lines = probe.summary();
      assertTrue(
          lines.stream()
              .anyMatch(line -> line.startsWith("Перед decode") && line.contains("STAB=0")));
      assertTrue(
          lines.stream()
              .anyMatch(line -> line.startsWith("Перед filter") && line.contains("STAB=1")));
      assertTrue(
          lines.stream()
              .anyMatch(
                  line ->
                      line.startsWith("Перед packet_handler")
                          && line.contains("STAB=0")
                          && line.contains("взмахи=1")));
      probe.close();
      channel.runPendingTasks();
      assertEquals(original, channel.pipeline().names());
      assertFalse(channel.writeInbound("RAW"));
    } finally {
      probe.close();
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void classifierFailureDoesNotBlockPacket() {
    var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    var probe =
        new NativeLungeProbe(
            channel,
            msg -> {
              throw new IllegalArgumentException();
            });
    try {
      probe.install();
      channel.runPendingTasks();
      assertTrue(channel.writeInbound("test"));
      assertEquals("test", channel.readInbound());
      channel.checkException();
    } finally {
      probe.close();
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void stoppingBeforeInstallCannotResurrectProbe() {
    var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    var original = channel.pipeline().names();
    var probe = new NativeLungeProbe(channel, msg -> "STAB");
    try {
      probe.close();
      probe.install();
      channel.runPendingTasks();
      assertEquals(original, channel.pipeline().names());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void closedChannelIsIgnored() {
    var channel = new EmbeddedChannel();
    channel.close();
    var probe = new NativeLungeProbe(channel, msg -> "STAB");
    assertDoesNotThrow(probe::install);
    assertTrue(probe.summary().getFirst().contains("канал закрыт"));
    probe.close();
    channel.finishAndReleaseAll();
  }

  @Test
  void longPipelineKeepsLastStageAndBoundsProbeCount() {
    var channel = new EmbeddedChannel();
    for (int i = 0; i < 30; i++)
      channel.pipeline().addLast("stage" + i, new ChannelInboundHandlerAdapter());
    channel.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
    var original = channel.pipeline().names();
    var probe = new NativeLungeProbe(channel, msg -> "STAB");
    try {
      probe.install();
      channel.runPendingTasks();
      assertTrue(channel.writeInbound("STAB"));
      assertEquals("STAB", channel.readInbound());
      assertEquals(24, probe.summary().stream().filter(line -> line.startsWith("Перед ")).count());
      assertTrue(
          probe.summary().stream()
              .anyMatch(
                  line -> line.startsWith("Перед packet_handler") && line.contains("STAB=1")));
      probe.close();
      channel.runPendingTasks();
      assertEquals(original, channel.pipeline().names());
    } finally {
      probe.close();
      channel.finishAndReleaseAll();
    }
  }
}

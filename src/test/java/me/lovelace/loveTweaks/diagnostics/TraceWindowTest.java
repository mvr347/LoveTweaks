package me.lovelace.loveTweaks.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;

class TraceWindowTest {
  @Test
  void recordsCancellationTransitionsAtTheirObservedPriorityAndReleasesEvent() {
    var trace = new TraceWindow(24);
    Object event = new Object();
    assertNotNull(trace.observe(event, EventPriority.LOWEST, "allowed", true));
    assertNull(trace.observe(event, EventPriority.LOW, "allowed", true));
    assertEquals("allowed → denied", trace.observe(event, EventPriority.HIGH, "denied", true));
    assertNull(trace.observe(event, EventPriority.MONITOR, "denied", true));
    assertEquals(0, trace.inFlight());
  }

  @Test
  void ordinaryMovementDoesNotFillTheReport() {
    var trace = new TraceWindow(24);
    for (int n = 0; n < 1000; n++) {
      Object event = new Object();
      assertNull(trace.observe(event, EventPriority.LOWEST, "moving " + n, false));
      assertNull(trace.observe(event, EventPriority.MONITOR, "moving " + n, false));
    }
    Object denied = new Object();
    trace.observe(denied, EventPriority.LOWEST, "moving", false);
    assertEquals(
        "moving → cancelled", trace.observe(denied, EventPriority.HIGHEST, "cancelled", false));
    trace.observe(denied, EventPriority.MONITOR, "cancelled", false);
    assertEquals(0, trace.inFlight());
  }

  @Test
  void reportAndIncompleteDispatchesRemainBounded() {
    var trace = new TraceWindow(2);
    for (int n = 0; n < 1000; n++) {
      Object event = new Object();
      String result = trace.observe(event, EventPriority.LOWEST, "state", true);
      if (n < 2) assertNotNull(result);
      else assertNull(result);
      trace.observe(event, EventPriority.MONITOR, "state", true);
    }
    assertEquals(0, trace.inFlight());
    for (int n = 0; n < 1000; n++)
      trace.observe(new Object(), EventPriority.LOWEST, "never completed", true);
    assertEquals(64, trace.inFlight());
  }
}

package me.lovelace.loveTweaks.diagnostics;

import java.util.IdentityHashMap;
import org.bukkit.event.EventPriority;

/** Keeps only in-flight events and a fixed number of reports; no Entity/Player references. */
final class TraceWindow {
  private final IdentityHashMap<Object, String> previous = new IdentityHashMap<>();
  private final int limit;
  private int emitted;

  TraceWindow(int limit) {
    this.limit = limit;
  }

  String observe(Object event, EventPriority priority, String state, boolean reportInitial) {
    if (!previous.containsKey(event) && previous.size() >= 64) return null;
    String before = previous.put(event, state);
    if (priority == EventPriority.MONITOR) previous.remove(event);
    boolean changed = before != null && !before.equals(state);
    if (emitted >= limit || !(changed || before == null && reportInitial)) return null;
    emitted++;
    return before == null ? "начальное состояние: " + state : before + " → " + state;
  }

  int inFlight() {
    return previous.size();
  }
}

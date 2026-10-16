package me.lovelace.loveTweaks.scoreboard;

import java.util.Arrays;

/**
 * Remembers the last prefix text sent for each scoreboard line so unchanged lines
 * produce no packets. Pure logic, no Bukkit types.
 */
final class LinePrefixCache {

    private final String[] last;

    LinePrefixCache(int capacity) {
        this.last = new String[capacity];
    }

    /** Returns true (and remembers the text) when the line differs from what was last sent. */
    boolean update(int index, String text) {
        if (index < 0 || index >= last.length) return false;
        if (text.equals(last[index])) return false;
        last[index] = text;
        return true;
    }

    /** Forgets a line so the next update is always sent (used when a line leaves the board). */
    void invalidate(int index) {
        if (index >= 0 && index < last.length) last[index] = null;
    }

    void clear() {
        Arrays.fill(last, null);
    }
}

package me.lovelace.loveTweaks.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinePrefixCacheTest {

    @Test
    void unchangedLineIsSkipped() {
        LinePrefixCache c = new LinePrefixCache(3);
        assertTrue(c.update(0, "a"));
        assertFalse(c.update(0, "a"));
        assertTrue(c.update(0, "b"));
    }

    @Test
    void invalidateForcesResend() {
        LinePrefixCache c = new LinePrefixCache(3);
        c.update(1, "x");
        c.invalidate(1);
        assertTrue(c.update(1, "x"));
    }

    @Test
    void outOfRangeIsIgnored() {
        LinePrefixCache c = new LinePrefixCache(1);
        assertFalse(c.update(5, "x"));
        assertFalse(c.update(-1, "x"));
    }

    @Test
    void clearForgetsEverything() {
        LinePrefixCache c = new LinePrefixCache(2);
        c.update(0, "a");
        c.clear();
        assertTrue(c.update(0, "a"));
    }
}

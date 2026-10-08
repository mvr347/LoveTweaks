package me.lovelace.loveTweaks.enchantments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagnetMathTest {
    @Test
    void radiusByLevel() {
        assertEquals(3.0, MagnetMath.radius(1));
        assertEquals(5.0, MagnetMath.radius(2));
    }

    @Test
    void outOfRangeAndTooClose() {
        assertNull(MagnetMath.pull(10, 0, 0, 3));
        assertNull(MagnetMath.pull(0.2, 0, 0, 3));
    }

    @Test
    void pullPointsToPlayerAndCloserIsFaster() {
        double[] far = MagnetMath.pull(2.5, 0, 0, 3);
        double[] near = MagnetMath.pull(1.0, 0, 0, 3);
        assertTrue(far[0] > 0 && near[0] > far[0]);
        assertTrue(near[0] <= MagnetMath.MAX_SPEED);
    }
}

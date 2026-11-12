package me.lovelace.loveTweaks.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemDropLossRuleTest {

    @Test
    void neutralAndAboveNeverLose() {
        for (int level = ItemDropLossListener.NEUTRAL_LEVEL; level <= 6; level++) {
            assertEquals(0.0, ItemDropLossListener.lossMultiplier(level, 1.6), "level " + level);
        }
    }

    @Test
    void belowNeutralLosesWithTerribleBoost() {
        assertEquals(1.6, ItemDropLossListener.lossMultiplier(0, 1.6));
        assertEquals(1.0, ItemDropLossListener.lossMultiplier(1, 1.6));
        assertEquals(1.0, ItemDropLossListener.lossMultiplier(2, 1.6));
    }
}

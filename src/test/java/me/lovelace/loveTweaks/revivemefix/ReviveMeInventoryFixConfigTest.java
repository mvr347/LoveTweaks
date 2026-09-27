package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviveMeInventoryFixConfigTest {

    @Test
    @DisplayName("ReviveMeInventoryFixConfig falls back to safe defaults when unconfigured")
    void testDefaults() {
        ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();
        config.load(null);

        assertTrue(config.isEnabled());
        assertTrue(config.isRequireReviveMe());
        assertEquals(DeathDropHandler.DropMode.NATURAL, config.getDropMode());
        assertTrue(config.isRemoveOnRevive());
        assertFalse(config.isClearSnapshotOnQuit());
        assertTrue(config.isClearInventoryOnDeath());
        assertTrue(config.isForceDropOnDownedDeath());
        assertFalse(config.isDropExp());
        assertEquals(300, config.getSnapshotTimeoutSeconds());
        assertEquals(EventPriority.HIGHEST, config.getDeathEventPriority());
        assertFalse(config.isDebug());
    }

    @Test
    @DisplayName("ReviveMeInventoryFixConfig reads overridden values from a config section")
    void testCustomValues() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("revive-inventory-fix.enabled", false);
        root.set("revive-inventory-fix.require-reviveme", false);
        root.set("revive-inventory-fix.drop-mode", "event_drops");
        root.set("revive-inventory-fix.remove-on-revive", false);
        root.set("revive-inventory-fix.clear-snapshot-on-quit", true);
        root.set("revive-inventory-fix.clear-inventory-on-death", false);
        root.set("revive-inventory-fix.force-drop-on-downed-death", false);
        root.set("revive-inventory-fix.drop-exp", true);
        root.set("revive-inventory-fix.snapshot-timeout-seconds", 120);
        root.set("revive-inventory-fix.death-event-priority", "monitor");
        root.set("revive-inventory-fix.debug", true);

        ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();
        config.load(root.getConfigurationSection("revive-inventory-fix"));

        assertFalse(config.isEnabled());
        assertFalse(config.isRequireReviveMe());
        assertEquals(DeathDropHandler.DropMode.EVENT_DROPS, config.getDropMode());
        assertFalse(config.isRemoveOnRevive());
        assertTrue(config.isClearSnapshotOnQuit());
        assertFalse(config.isClearInventoryOnDeath());
        assertFalse(config.isForceDropOnDownedDeath());
        assertTrue(config.isDropExp());
        assertEquals(120, config.getSnapshotTimeoutSeconds());
        assertEquals(EventPriority.MONITOR, config.getDeathEventPriority());
        assertTrue(config.isDebug());
    }

    @Test
    @DisplayName("A negative snapshot-timeout-seconds is clamped to 0 rather than disabling expiry unpredictably")
    void testNegativeTimeoutClamped() {
        MemoryConfiguration root = new MemoryConfiguration();
        root.set("timeout.snapshot-timeout-seconds", -5);

        ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();
        config.load(root.getConfigurationSection("timeout"));

        assertEquals(0, config.getSnapshotTimeoutSeconds());
    }

    @Test
    @DisplayName("An unknown death-event-priority value falls back to HIGHEST instead of throwing")
    void testInvalidPriorityFallsBackToHighest() {
        MemoryConfiguration root = new MemoryConfiguration();
        root.set("bad.death-event-priority", "not-a-real-priority");

        ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();
        config.load(root.getConfigurationSection("bad"));

        assertEquals(EventPriority.HIGHEST, config.getDeathEventPriority());
    }
}

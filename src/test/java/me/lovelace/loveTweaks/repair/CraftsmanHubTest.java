package me.lovelace.loveTweaks.repair;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CraftsmanHubTest {

    @Test
    void sectionsAreCentredInTheWorkRowOfA27SlotMenu() {
        assertArrayEquals(new int[]{13}, CraftsmanHub.centered(1));
        assertArrayEquals(new int[]{12, 14}, CraftsmanHub.centered(2));
        assertArrayEquals(new int[]{11, 13, 15}, CraftsmanHub.centered(3));
        for (int count = 1; count <= 7; count++) {
            for (int slot : CraftsmanHub.centered(count)) {
                // work row only (9-17), never the side walls 9 and 17, never the header or the footer
                assertTrue(slot > 9 && slot < 17, "count " + count + " slot " + slot);
            }
        }
    }

    @Test
    void shippedConfigHasEveryHubTextAndTheTwoCommands() {
        var in = CraftsmanHubTest.class.getResourceAsStream("/config.yml");
        assertNotNull(in);
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String key : new String[]{"hub-title", "hub-npc", "hub-npc-lore", "hub-click", "hub-repair",
                "hub-repair-lore", "hub-anchors", "hub-anchors-lore", "hub-banner", "hub-banner-lore", "close"}) {
            assertNotNull(config.getString("blacksmith.text." + key), key);
        }
        assertEquals("ac master", config.getString("blacksmith.hub.anchors.command"));
        assertEquals("clan banner", config.getString("blacksmith.hub.banner.command"));
        assertTrue(config.getBoolean("blacksmith.hub.enabled"));
    }
}

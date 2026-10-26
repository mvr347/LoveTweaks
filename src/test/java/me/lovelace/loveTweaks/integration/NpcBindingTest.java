package me.lovelace.loveTweaks.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcBindingTest {

    @Test
    @DisplayName("Provider defaults to citizens when missing, blank or padded")
    void providerDefault() {
        assertEquals("citizens", NpcBinding.normalizeProvider(null));
        assertEquals("citizens", NpcBinding.normalizeProvider("  "));
        assertEquals("citizens", NpcBinding.normalizeProvider("global"));
        assertEquals("citizens", NpcBinding.normalizeProvider(" GLOBAL "));
        assertEquals("loveessential", NpcBinding.normalizeProvider(" LoveEssential "));
        assertEquals("citizens", NpcBinding.providerFrom(new YamlConfiguration()));

        YamlConfiguration root = new YamlConfiguration();
        root.set("integrations.npc-provider", "loveessential");
        assertEquals("loveessential", NpcBinding.providerFrom(root));
    }

    @Test
    @DisplayName("Legacy numeric npc-id is read as a String key")
    void legacyIdIsReadAsKey() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", 17);
        assertEquals("17", NpcBinding.readKey(herald));
    }

    @Test
    @DisplayName("Legacy npc-id -1 and empty sections mean unbound")
    void unbound() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", -1);
        assertEquals("", NpcBinding.readKey(herald));
        assertEquals("", NpcBinding.readKey(new YamlConfiguration()));
        assertEquals("", NpcBinding.readKey(null));
    }

    @Test
    @DisplayName("npc-key wins over a leftover npc-id")
    void keyWins() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", 5);
        herald.set("npc-key", "ext-1a2b3c4d");
        assertEquals("ext-1a2b3c4d", NpcBinding.readKey(herald));
    }

    @Test
    @DisplayName("Migration moves npc-id to npc-key, drops the old key and keeps the name")
    void migration() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", 42);
        herald.set("npc-name", "Роберт");

        assertTrue(NpcBinding.migrateLegacy(herald));
        assertEquals("42", herald.getString("npc-key"));
        assertFalse(herald.isSet("npc-id"));
        assertEquals("Роберт", herald.getString("npc-name"));
        assertEquals("42", NpcBinding.readKey(herald));
        // second run is a no-op
        assertFalse(NpcBinding.migrateLegacy(herald));
    }

    @Test
    @DisplayName("Migration of an unbound legacy id leaves an empty key and removes npc-id")
    void migrationUnbound() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", -1);
        assertTrue(NpcBinding.migrateLegacy(herald));
        assertEquals("", herald.getString("npc-key"));
        assertFalse(herald.isSet("npc-id"));
    }

    @Test
    @DisplayName("Migration never overwrites an already stored key")
    void migrationKeepsKey() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-key", "ext-deadbeef");
        herald.set("npc-id", 3);
        assertTrue(NpcBinding.migrateLegacy(herald));
        assertEquals("ext-deadbeef", herald.getString("npc-key"));
        assertFalse(herald.isSet("npc-id"));
    }

    @Test
    @DisplayName("Unbinding after migration cannot resurrect the legacy id")
    void unbindDoesNotResurrect() {
        YamlConfiguration herald = new YamlConfiguration();
        herald.set("npc-id", 9);
        NpcBinding.write(herald, "", "");
        assertEquals("", NpcBinding.readKey(herald));
        assertFalse(herald.isSet("npc-id"));
    }

    @Test
    @DisplayName("write stores key and name")
    void write() {
        YamlConfiguration herald = new YamlConfiguration();
        NpcBinding.write(herald, "ext-0011aabb", "Глашатай");
        assertEquals("ext-0011aabb", NpcBinding.readKey(herald));
        assertEquals("Глашатай", herald.getString("npc-name"));
    }

    @Test
    @DisplayName("Click filter compares provider id and key; foreign providers are ignored")
    void clickFilter() {
        assertTrue(NpcBinding.matches("citizens", "17", "citizens", "17"));
        // same key text, but the event comes from another provider
        assertFalse(NpcBinding.matches("citizens", "17", "loveessential", "17"));
        assertFalse(NpcBinding.matches("loveessential", "ext-1", "citizens", "ext-1"));
        assertTrue(NpcBinding.matches("loveessential", "ext-1", "loveessential", "ext-1"));
        assertFalse(NpcBinding.matches("citizens", "17", "citizens", "18"));
        assertFalse(NpcBinding.matches("citizens", "", "citizens", ""));
        assertFalse(NpcBinding.matches("citizens", "17", "citizens", null));
    }

    @Test
    @DisplayName("A key that cannot belong to the selected provider is flagged")
    void foreignKey() {
        assertFalse(NpcBinding.keyLooksForeign("citizens", "17"));
        assertTrue(NpcBinding.keyLooksForeign("citizens", "ext-1a2b3c4d"));
        assertFalse(NpcBinding.keyLooksForeign("loveessential", "17"));
        assertFalse(NpcBinding.keyLooksForeign("loveessential", "ext-1a2b3c4d"));
        assertFalse(NpcBinding.keyLooksForeign("citizens", ""));
    }

    @Test
    @DisplayName("Shipped config.yml defaults keep today's behaviour (citizens, reviveme, no legacy npc-id)")
    void shippedDefaults() throws Exception {
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in);
            YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals("citizens", NpcBinding.providerFrom(shipped));
            assertEquals("global", shipped.getString("revive.provider"));
            assertEquals("global", shipped.getString("integrations.npc-provider"));
            assertTrue(shipped.isSet("herald.npc-key"));
            assertFalse(shipped.isSet("herald.npc-id"));
            assertEquals("", NpcBinding.readKey(shipped.getConfigurationSection("herald")));
        }
    }
}

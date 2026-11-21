package me.lovelace.loveTweaks.integration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcBindingTest {

    @Test
    @DisplayName("The provider is always LoveEssential; leftover switch values are reported, never used")
    void providerIsAlwaysLoveEssential() {
        assertEquals("loveessential", NpcBinding.PROVIDER_ID);
        assertEquals("citizens", NpcBinding.ignoredProviderValue("citizens"));
        assertEquals("znpcs", NpcBinding.ignoredProviderValue(" znpcs "));
        assertNull(NpcBinding.ignoredProviderValue(null));
        assertNull(NpcBinding.ignoredProviderValue("  "));
        assertNull(NpcBinding.ignoredProviderValue(" GLOBAL "));
        assertNull(NpcBinding.ignoredProviderValue("LoveEssential"));
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
        assertTrue(NpcBinding.matches("17", "loveessential", "17"));
        assertTrue(NpcBinding.matches("ext-1", "LoveEssential", "ext-1"));
        // same key text, but the event comes from another provider
        assertFalse(NpcBinding.matches("17", "citizens", "17"));
        assertFalse(NpcBinding.matches("17", "loveessential", "18"));
        assertFalse(NpcBinding.matches("", "loveessential", ""));
        assertFalse(NpcBinding.matches("17", "loveessential", null));
        assertFalse(NpcBinding.matches("17", null, "17"));
    }

    @Test
    @DisplayName("Shipped config.yml has no provider switch and no legacy npc-id")
    void shippedDefaults() throws Exception {
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in);
            YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            assertNull(shipped.getString("integrations.npc-provider"));
            assertNull(shipped.getString("revive.provider"));
            assertFalse(shipped.isSet("revive-inventory-fix"));
            assertTrue(shipped.isSet("herald.npc-key"));
            assertFalse(shipped.isSet("herald.npc-id"));
            assertEquals("", NpcBinding.readKey(shipped.getConfigurationSection("herald")));
        }
    }
}

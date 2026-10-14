package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the shipped config.yml and checks the scoreboard section is internally consistent. */
class ScoreboardConfigFileTest {

    private static ScoreboardConfig cfg;

    @BeforeAll
    static void load() throws Exception {
        try (var in = ScoreboardConfigFileTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            cfg = new ScoreboardConfig();
            cfg.load(yaml);
        }
    }

    @Test
    @DisplayName("7 categories fit the category row, every placeholder belongs to a defined one")
    void categories() {
        assertEquals(7, cfg.getCategories().size());
        assertTrue(cfg.getCategories().size() <= ScoreboardLayout.CATEGORY_SLOTS.length);
        for (ScoreboardPlaceholder ph : cfg.getPlaceholders().values()) {
            assertTrue(cfg.getCategories().containsKey(ph.category()), ph.id());
        }
        for (String category : cfg.getCategories().keySet()) {
            assertFalse(cfg.placeholdersOf(category).isEmpty(), category);
            assertTrue(cfg.placeholdersOf(category).size() <= ScoreboardLayout.MAX_SUBMENU_ITEMS, category);
        }
    }

    @Test
    @DisplayName("presets reference existing ids and variants, fit the header and the line limit")
    void presets() {
        assertEquals(4, cfg.getPresets().size());
        assertTrue(cfg.getPresets().size() <= ScoreboardConfig.MAX_PRESETS);
        assertNotNull(cfg.getPresets().get(cfg.getDefaultPreset()));
        for (ScoreboardPreset preset : cfg.getPresets().values()) {
            assertTrue(preset.entries().size() <= cfg.getMaxPlaceholders(), preset.id());
            for (ScoreboardEntry e : preset.entries()) {
                ScoreboardPlaceholder ph = cfg.getPlaceholder(e.id());
                assertNotNull(ph, preset.id() + " -> " + e.id());
                assertTrue(e.variant() < ph.variantCount(), preset.id() + " -> " + e.id() + ":" + e.variant());
            }
        }
    }

    @Test
    @DisplayName("date/time is one placeholder with three variants; legacy ids are gone from config")
    void datetime() {
        ScoreboardPlaceholder dt = cfg.getPlaceholder(LegacyMigration.DATETIME_ID);
        assertNotNull(dt);
        assertEquals(3, dt.variantCount());
        for (String legacy : new String[]{"date", "time", "server_time"}) assertNull(cfg.getPlaceholder(legacy));
        assertFalse(dt.template(1).contains("hh") || dt.template(1).contains(" a%"), "24-hour time");
    }

    @Test
    @DisplayName("every line has a template and PAPI ids; broken or removed expansions are not referenced")
    void templates() {
        for (ScoreboardPlaceholder ph : cfg.getPlaceholders().values()) {
            assertFalse(ph.variants().isEmpty(), ph.id());
            for (ScoreboardPlaceholder.Variant v : ph.variants()) assertFalse(v.template().isBlank(), ph.id());
            assertFalse(ph.papiIds().isEmpty(), ph.id());
            for (ScoreboardPlaceholder.Variant v : ph.variants()) {
                assertFalse(v.template().contains("%kd_ratio%"), ph.id());
                assertFalse(v.template().contains("playtime_since_join"), ph.id());
            }
            if (ph.variantCount() > 1) {
                for (ScoreboardPlaceholder.Variant v : ph.variants()) assertFalse(v.name().isBlank(), ph.id());
            }
        }
        assertTrue(cfg.getPlaceholder("kd_ratio").template(0).contains("%lovetweaks_kd%"));
    }

    @Test
    @DisplayName("line limit is clamped to 8 and the GUI texts load")
    void limitsAndMessages() {
        assertEquals(8, cfg.getMaxPlaceholders());
        assertNotEquals("view-title", cfg.messages().get("view-title"));
        for (String key : ScoreboardMessages.defaultKeys()) {
            assertNotEquals(key, cfg.messages().get(key), key);
        }
    }
}

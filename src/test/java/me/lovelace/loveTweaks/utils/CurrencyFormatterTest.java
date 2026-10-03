package me.lovelace.loveTweaks.utils;

import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyFormatterTest {

    private static YamlConfiguration config() throws Exception {
        try (Reader r = new InputStreamReader(CurrencyFormatterTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    @Test
    void fallbackTableUsesTheNewStandardValues() {
        // No LoveCore in a unit test: the formatter falls back to its configured/default coins.
        CurrencyFormatter formatter = new CurrencyFormatter();
        String text = formatter.format(2_150);
        assertTrue(text.contains("img_gold_coin"), text);
        assertTrue(text.contains("img_iron_coin"), text);
        assertTrue(text.contains("img_copper_coin"), text);
        assertFalse(text.contains("img_diamond_coin"), text);
    }

    @Test
    void configFallbackValuesMatchLoveCoreStandard() throws Exception {
        YamlConfiguration cfg = config();
        CurrencyFormatter formatter = new CurrencyFormatter();
        formatter.load(cfg.getConfigurationSection("herald.gui.currency"));
        String text = formatter.format(20_000);
        assertTrue(text.contains("img_diamond_coin"), text);
        assertFalse(text.contains("img_gold_coin"), text);
    }

    @Test
    void heraldPricesParse() throws Exception {
        YamlConfiguration cfg = config();
        long min = MoneyParser.parse(String.valueOf(cfg.get("herald.min-cost")), MoneyParser.STANDARD);
        long max = MoneyParser.parse(String.valueOf(cfg.get("herald.max-cost")), MoneyParser.STANDARD);
        assertEquals(200L, min);
        assertEquals(1_500L, max);
    }
}

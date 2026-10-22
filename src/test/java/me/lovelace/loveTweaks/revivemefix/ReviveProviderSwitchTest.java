package me.lovelace.loveTweaks.revivemefix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ReviveProviderSwitchTest {

    @Test
    @DisplayName("revive.provider parses known ids, defaults to reviveme when blank or unknown")
    void parse() {
        assertEquals(ReviveProvider.REVIVEME, ReviveProvider.parse(null));
        assertEquals(ReviveProvider.REVIVEME, ReviveProvider.parse(""));
        assertEquals(ReviveProvider.REVIVEME, ReviveProvider.parse("typo"));
        assertEquals(ReviveProvider.REVIVEME, ReviveProvider.parse("reviveme"));
        assertEquals(ReviveProvider.LOVEESSENTIAL, ReviveProvider.parse(" LoveEssential "));
        assertTrue(ReviveProvider.isKnown(null));
        assertTrue(ReviveProvider.isKnown("loveessential"));
        assertFalse(ReviveProvider.isKnown("typo"));
    }

    @Test
    @DisplayName("Only the reviveme provider needs the ReviveMe fixes")
    void needsFixes() {
        assertTrue(ReviveProvider.REVIVEME.needsReviveMeFixes());
        assertFalse(ReviveProvider.LOVEESSENTIAL.needsReviveMeFixes());
    }

    @Test
    @DisplayName("loveessential: module registers nothing and logs exactly one INFO line")
    void loveEssentialSkipsFixes() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        List<LogRecord> records = new ArrayList<>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });

        // A null plugin proves the branch never touches Bukkit: registering a listener would NPE.
        ReviveMeInventoryModule module = new ReviveMeInventoryModule(null, logger);
        module.enable(null, ReviveProvider.LOVEESSENTIAL);

        assertFalse(module.isActive());
        assertNull(module.getInventoryManager());
        assertEquals(1, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("loveessential"));

        // disable() after a skipped enable must be safe, and a reload keeps it skipped.
        assertDoesNotThrow(module::disable);
        module.reload(null, ReviveProvider.LOVEESSENTIAL);
        assertFalse(module.isActive());
        assertEquals(2, records.size());
    }
}

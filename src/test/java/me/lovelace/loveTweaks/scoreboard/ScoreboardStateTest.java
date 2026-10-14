package me.lovelace.loveTweaks.scoreboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardStateTest {

    private static PlayerScoreboardState empty() {
        return new PlayerScoreboardState(true, false, List.of());
    }

    @Test
    @DisplayName("Slots are filled in selection order and removal shifts the rest up (no gaps)")
    void slotsFillInOrderAndShift() {
        PlayerScoreboardState s = empty();
        assertTrue(s.enable("kills", 8));
        assertTrue(s.enable("ping", 8));
        assertTrue(s.enable("coords", 8));
        assertEquals(List.of("kills", "ping", "coords"), s.getActivePlaceholders());

        assertTrue(s.disable("ping"));
        assertEquals(List.of("kills", "coords"), s.getActivePlaceholders());
        // the next selection takes the first free slot, i.e. the end of the list
        s.enable("deaths", 8);
        assertEquals(List.of("kills", "coords", "deaths"), s.getActivePlaceholders());
    }

    @Test
    @DisplayName("enable refuses duplicates and anything beyond the limit")
    void enableRespectsLimit() {
        PlayerScoreboardState s = empty();
        assertTrue(s.enable("a", 2));
        assertFalse(s.enable("a", 2));
        assertTrue(s.enable("b", 2));
        assertFalse(s.enable("c", 2));
        assertEquals(List.of("a", "b"), s.getActivePlaceholders());
    }

    @Test
    @DisplayName("disable clears the chosen variant, so a re-enabled line starts on the first format")
    void disableResetsVariant() {
        PlayerScoreboardState s = empty();
        s.enable("datetime", 8);
        s.cycleVariant("datetime", 3);
        assertEquals(1, s.getVariant("datetime"));
        s.disable("datetime");
        s.enable("datetime", 8);
        assertEquals(0, s.getVariant("datetime"));
    }

    @Test
    @DisplayName("cycleVariant wraps around and ignores single-format lines")
    void cycleVariantWraps() {
        PlayerScoreboardState s = empty();
        s.enable("datetime", 8);
        assertEquals(1, s.cycleVariant("datetime", 3));
        assertEquals(2, s.cycleVariant("datetime", 3));
        assertEquals(0, s.cycleVariant("datetime", 3));
        s.enable("ping", 8);
        assertEquals(0, s.cycleVariant("ping", 1));
    }

    @Test
    @DisplayName("replaceWith applies a whole layout, dropping duplicates")
    void replaceWith() {
        PlayerScoreboardState s = empty();
        s.enable("old", 8);
        s.replaceWith(List.of(new ScoreboardEntry("a", 0), new ScoreboardEntry("b", 2), new ScoreboardEntry("a", 1)));
        assertEquals(List.of("a", "b"), s.getActivePlaceholders());
        assertEquals(List.of(new ScoreboardEntry("a", 0), new ScoreboardEntry("b", 2)), s.entries());
    }

    @Test
    @DisplayName("clampTo cuts the tail when the configured limit was lowered")
    void clampTo() {
        PlayerScoreboardState s = empty();
        for (String id : List.of("a", "b", "c", "d")) s.enable(id, 8);
        s.setVariant("d", 1);
        s.clampTo(2);
        assertEquals(List.of("a", "b"), s.getActivePlaceholders());
        assertEquals(0, s.getVariant("d"));
    }

    @Test
    @DisplayName("move up / down report whether anything moved")
    void moves() {
        PlayerScoreboardState s = empty();
        s.enable("a", 8);
        s.enable("b", 8);
        assertFalse(s.movePlaceholderUp("a"));
        assertTrue(s.movePlaceholderUp("b"));
        assertEquals(List.of("b", "a"), s.getActivePlaceholders());
        assertFalse(s.movePlaceholderDown("a"));
        assertTrue(s.movePlaceholderDown("b"));
    }

    @Test
    @DisplayName("VariantCycler normalizes out-of-range indexes")
    void variantCycler() {
        assertEquals(0, VariantCycler.next(0, 1));
        assertEquals(0, VariantCycler.next(5, 0));
        assertEquals(1, VariantCycler.next(0, 3));
        assertEquals(0, VariantCycler.next(2, 3));
        assertEquals(1, VariantCycler.next(99, 3)); // stale index -> first variant -> next
        assertEquals(0, VariantCycler.normalize(-1, 3));
        assertEquals(2, VariantCycler.normalize(2, 3));
    }
}

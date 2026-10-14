package me.lovelace.loveTweaks.scoreboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PresetAndSortTest {

    private static final java.util.function.ToIntFunction<String> THREE_VARIANTS = id -> id.startsWith("x") ? 0 : 3;

    @Test
    @DisplayName("preset entries parse as id or id:variant")
    void parse() {
        assertEquals(List.of(new ScoreboardEntry("coords", 0), new ScoreboardEntry("datetime", 2),
                        new ScoreboardEntry("bad", 0)),
                PresetApplier.parse(List.of("coords", " datetime:2 ", "bad:zz", "")));
    }

    @Test
    @DisplayName("resolve skips unavailable and unknown rows, drops duplicates, clamps variants")
    void resolveSkips() {
        List<ScoreboardEntry> preset = PresetApplier.parse(
                List.of("clan_tag", "kills", "xunknown", "kills", "datetime:9", "ping"));
        List<ScoreboardEntry> out = PresetApplier.resolve(preset, id -> !id.equals("clan_tag"), THREE_VARIANTS, 8);
        assertEquals(List.of(new ScoreboardEntry("kills", 0), new ScoreboardEntry("datetime", 0),
                new ScoreboardEntry("ping", 0)), out);
    }

    @Test
    @DisplayName("resolve never exceeds the line limit")
    void resolveLimit() {
        List<ScoreboardEntry> preset = PresetApplier.parse(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
        assertEquals(8, PresetApplier.resolve(preset, id -> true, THREE_VARIANTS, 8).size());
        assertEquals(3, PresetApplier.resolve(preset, id -> true, THREE_VARIANTS, 3).size());
        assertEquals("c", PresetApplier.resolve(preset, id -> true, THREE_VARIANTS, 3).get(2).id());
    }

    @Test
    @DisplayName("a preset with nothing usable resolves to an empty list")
    void resolveNothing() {
        assertTrue(PresetApplier.resolve(PresetApplier.parse(List.of("clan_tag", "clan_role")),
                id -> false, THREE_VARIANTS, 8).isEmpty());
    }

    @Test
    @DisplayName("applying a preset replaces the player's current set")
    void applyReplaces() {
        PlayerScoreboardState s = new PlayerScoreboardState(true, false, List.of(new ScoreboardEntry("old")));
        List<ScoreboardEntry> resolved = PresetApplier.resolve(PresetApplier.parse(List.of("a", "datetime:1")),
                id -> true, THREE_VARIANTS, 8);
        s.replaceWith(resolved);
        assertEquals(List.of("a", "datetime"), s.getActivePlaceholders());
        assertEquals(1, s.getVariant("datetime"));
        assertTrue(PresetApplier.sameSet(s.entries(), resolved));
    }

    @Test
    @DisplayName("sameSet ignores order but not variants")
    void sameSet() {
        assertTrue(PresetApplier.sameSet(
                List.of(new ScoreboardEntry("a"), new ScoreboardEntry("b", 1)),
                List.of(new ScoreboardEntry("b", 1), new ScoreboardEntry("a"))));
        assertFalse(PresetApplier.sameSet(List.of(new ScoreboardEntry("b", 1)), List.of(new ScoreboardEntry("b", 0))));
        assertFalse(PresetApplier.sameSet(List.of(new ScoreboardEntry("a")), List.of()));
    }

    @Test
    @DisplayName("flat view order: enabled (board order) -> available A-Z -> unavailable A-Z")
    void flatSort() {
        List<PlaceholderSorter.Row> rows = List.of(
                new PlaceholderSorter.Row("zeta", "Ёж", -1, true),
                new PlaceholderSorter.Row("beta", "Бета", -1, true),
                new PlaceholderSorter.Row("act2", "Яблоко", 1, true),
                new PlaceholderSorter.Row("blocked_b", "Банан", -1, false),
                new PlaceholderSorter.Row("act1", "Арбуз", 0, true),
                new PlaceholderSorter.Row("alpha", "Альфа", -1, true),
                new PlaceholderSorter.Row("blocked_a", "Аист", -1, false),
                // enabled but currently unavailable: stays in the enabled group so it can be removed
                new PlaceholderSorter.Row("act3", "Ананас", 2, false));
        assertEquals(List.of("act1", "act2", "act3", "alpha", "beta", "zeta", "blocked_a", "blocked_b"),
                PlaceholderSorter.sort(rows));
    }
}

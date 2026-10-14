package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Bukkit-free logic of quick presets ("Стандартная", "Боевая"...). */
public final class PresetApplier {

    private PresetApplier() {}

    /** Parses {@code id} or {@code id:variant}; malformed variant numbers fall back to 0. */
    public static List<ScoreboardEntry> parse(List<String> raw) {
        List<ScoreboardEntry> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            String trimmed = s.trim();
            int colon = trimmed.lastIndexOf(':');
            if (colon > 0) {
                int variant = 0;
                try {
                    variant = Math.max(0, Integer.parseInt(trimmed.substring(colon + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // keep 0
                }
                out.add(new ScoreboardEntry(trimmed.substring(0, colon).trim(), variant));
            } else {
                out.add(new ScoreboardEntry(trimmed, 0));
            }
        }
        return out;
    }

    /**
     * Resolves a preset into the entries that will actually be enabled: unusable rows (requirement
     * not met / plugin missing / unknown id) are skipped, duplicates dropped, variants clamped, and
     * the result is cut at {@code max} lines.
     *
     * @param variantCount number of variants of the given id (0 for unknown ids)
     */
    public static List<ScoreboardEntry> resolve(List<ScoreboardEntry> preset, Predicate<String> usable,
                                                ToIntFunction<String> variantCount, int max) {
        List<ScoreboardEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScoreboardEntry e : preset) {
            if (out.size() >= max) break;
            if (variantCount.applyAsInt(e.id()) <= 0) continue;
            if (!usable.test(e.id())) continue;
            if (!seen.add(e.id())) continue;
            out.add(new ScoreboardEntry(e.id(), VariantCycler.normalize(e.variant(), variantCount.applyAsInt(e.id()))));
        }
        return out;
    }

    /** True when both lists hold the same id+variant pairs (order is the player's own business). */
    public static boolean sameSet(List<ScoreboardEntry> a, List<ScoreboardEntry> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }
}

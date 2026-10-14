package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Migration of ids stored in the DB by older plugin versions. */
public final class LegacyMigration {

    public static final String DATETIME_ID = "datetime";

    /** date/time/server_time were three separate placeholders; they are now variants of one. */
    private static final Map<String, ScoreboardEntry> LEGACY_IDS = Map.of(
            "date", new ScoreboardEntry(DATETIME_ID, 0),
            "time", new ScoreboardEntry(DATETIME_ID, 1),
            "server_time", new ScoreboardEntry(DATETIME_ID, 2)
    );

    private LegacyMigration() {}

    /** Renames legacy ids and collapses duplicates (the first occurrence wins, order is kept). */
    public static List<ScoreboardEntry> migrate(List<ScoreboardEntry> entries) {
        List<ScoreboardEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScoreboardEntry e : entries) {
            ScoreboardEntry mapped = LEGACY_IDS.getOrDefault(e.id(), e);
            if (seen.add(mapped.id())) out.add(mapped);
        }
        return out;
    }

    /** Old numeric {@code sort-group} -> new category id (used when a config still has no {@code category}). */
    public static String categoryForSortGroup(int sortGroup) {
        return switch (sortGroup) {
            case 5, 20 -> "reputation";
            case 10 -> "clans";
            case 30 -> "combat";
            default -> "general"; // 0 and 35 (date/time)
        };
    }
}

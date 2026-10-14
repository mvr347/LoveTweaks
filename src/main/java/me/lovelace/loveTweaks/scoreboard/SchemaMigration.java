package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Computes the ALTER TABLE statements needed to bring an existing table up to date. */
public final class SchemaMigration {

    public static final String PLAYERS_TABLE = "scoreboard_players";
    public static final String PLACEHOLDERS_TABLE = "scoreboard_placeholders";

    /** column -> definition appended to ALTER TABLE ... ADD COLUMN. */
    public static final Map<String, String> PLAYERS_COLUMNS = Map.of(
            "view_all", "INTEGER NOT NULL DEFAULT 0");
    public static final Map<String, String> PLACEHOLDERS_COLUMNS = Map.of(
            "variant", "INTEGER NOT NULL DEFAULT 0");

    private SchemaMigration() {}

    /** @param existingColumns result of {@code PRAGMA table_info(table)}, compared case-insensitively */
    public static List<String> missingColumnStatements(String table, Set<String> existingColumns,
                                                       Map<String, String> required) {
        Set<String> lower = new java.util.HashSet<>();
        for (String c : existingColumns) lower.add(c.toLowerCase(Locale.ROOT));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(required).entrySet()) {
            if (!lower.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                out.add("ALTER TABLE " + table + " ADD COLUMN " + e.getKey() + " " + e.getValue());
            }
        }
        return out;
    }
}

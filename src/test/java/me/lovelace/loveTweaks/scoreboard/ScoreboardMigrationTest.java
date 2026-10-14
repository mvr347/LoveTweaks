package me.lovelace.loveTweaks.scoreboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardMigrationTest {

    @Test
    @DisplayName("date / time / server_time become datetime variants 0 / 1 / 2")
    void legacyDateTimeIds() {
        assertEquals(List.of(new ScoreboardEntry("datetime", 0)),
                LegacyMigration.migrate(List.of(new ScoreboardEntry("date"))));
        assertEquals(List.of(new ScoreboardEntry("datetime", 1)),
                LegacyMigration.migrate(List.of(new ScoreboardEntry("time"))));
        assertEquals(List.of(new ScoreboardEntry("datetime", 2)),
                LegacyMigration.migrate(List.of(new ScoreboardEntry("server_time"))));
    }

    @Test
    @DisplayName("duplicates collapse (first wins), order and other ids are kept")
    void duplicatesCollapse() {
        List<ScoreboardEntry> in = List.of(
                new ScoreboardEntry("kills"),
                new ScoreboardEntry("time"),
                new ScoreboardEntry("ping"),
                new ScoreboardEntry("date"),
                new ScoreboardEntry("server_time"),
                new ScoreboardEntry("kills"));
        assertEquals(List.of(
                new ScoreboardEntry("kills", 0),
                new ScoreboardEntry("datetime", 1),
                new ScoreboardEntry("ping", 0)), LegacyMigration.migrate(in));
    }

    @Test
    @DisplayName("an already migrated datetime keeps its stored variant")
    void migratedIdUntouched() {
        assertEquals(List.of(new ScoreboardEntry("datetime", 2)),
                LegacyMigration.migrate(List.of(new ScoreboardEntry("datetime", 2))));
    }

    @Test
    @DisplayName("sort-group maps to a category per the plan table")
    void sortGroupToCategory() {
        assertEquals("general", LegacyMigration.categoryForSortGroup(0));
        assertEquals("reputation", LegacyMigration.categoryForSortGroup(5));
        assertEquals("clans", LegacyMigration.categoryForSortGroup(10));
        assertEquals("reputation", LegacyMigration.categoryForSortGroup(20));
        assertEquals("combat", LegacyMigration.categoryForSortGroup(30));
        assertEquals("general", LegacyMigration.categoryForSortGroup(35));
        assertEquals("general", LegacyMigration.categoryForSortGroup(123));
    }

    @Test
    @DisplayName("old tables get both new columns, up-to-date tables get nothing")
    void schemaMigration() {
        assertEquals(List.of("ALTER TABLE scoreboard_players ADD COLUMN view_all INTEGER NOT NULL DEFAULT 0"),
                SchemaMigration.missingColumnStatements(SchemaMigration.PLAYERS_TABLE,
                        Set.of("uuid", "enabled"), SchemaMigration.PLAYERS_COLUMNS));
        assertEquals(List.of("ALTER TABLE scoreboard_placeholders ADD COLUMN variant INTEGER NOT NULL DEFAULT 0"),
                SchemaMigration.missingColumnStatements(SchemaMigration.PLACEHOLDERS_TABLE,
                        Set.of("uuid", "position", "placeholder_id"), SchemaMigration.PLACEHOLDERS_COLUMNS));
        assertTrue(SchemaMigration.missingColumnStatements(SchemaMigration.PLAYERS_TABLE,
                Set.of("uuid", "enabled", "VIEW_ALL"), SchemaMigration.PLAYERS_COLUMNS).isEmpty());
        assertTrue(SchemaMigration.missingColumnStatements("t", Set.of(), Map.of()).isEmpty());
    }

    @Test
    @DisplayName("PAPI identifiers are taken from every %id_param% token")
    void papiIds() {
        assertEquals(Set.of("server"), PapiIds.extract("&7▸ &f%server_time_dd.MM.yyyy HH:mm% &7дата"));
        assertEquals(Set.of("lovebrew"), PapiIds.extract("%lovebrew_exp%/%lovebrew_req_exp%"));
        assertEquals(Set.of("lovehunt", "other"), PapiIds.extract("%LoveHunt_rank% 100% %other_x%"));
        assertTrue(PapiIds.extract("no placeholders, 50% off").isEmpty());
        assertTrue(PapiIds.extract(null).isEmpty());
    }

    @Test
    @DisplayName("ScoreboardPlaceholder collects PAPI ids from templates and requirement, and falls back on bad variants")
    void placeholderVariants() {
        ScoreboardPlaceholder ph = ScoreboardPlaceholder.create("datetime", "Дата", List.of(
                        new ScoreboardPlaceholder.Variant("Дата", "%server_time_dd%"),
                        new ScoreboardPlaceholder.Variant("Время", "%lovetweaks_x%")),
                List.of(), new ScoreboardRequirement("%clans_in_clan%", "true", ""), "general");
        assertEquals(Set.of("server", "lovetweaks", "clans"), ph.papiIds());
        assertEquals(2, ph.variantCount());
        assertEquals("%lovetweaks_x%", ph.template(1));
        assertEquals("%server_time_dd%", ph.template(7));
        assertEquals("Время", ph.variantName(1));
    }

    @Test
    @DisplayName("message placeholders are substituted")
    void messageFill() {
        assertEquals("Слот 3 пусто", ScoreboardMessages.fill("Слот <n> пусто", "n", 3));
        assertEquals("Сейчас: Все", new ScoreboardMessages().get("view-current", "current", "Все").replace("&7", "").replace("&f", ""));
    }
}

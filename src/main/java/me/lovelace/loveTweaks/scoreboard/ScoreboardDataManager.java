package me.lovelace.loveTweaks.scoreboard;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardDataManager {

    private final LoveTweaks plugin;
    private Connection connection;
    private final Map<UUID, PlayerScoreboardState> cache = new HashMap<>();

    public ScoreboardDataManager(LoveTweaks plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cache.clear();
        openConnection();
        migrateLegacyYaml();
    }

    private void openConnection() {
        closeQuietly();
        try {
            Class.forName("org.sqlite.JDBC");
            plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "lovetweaks.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS scoreboard_players (" +
                        "uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 1)");
                st.execute("CREATE TABLE IF NOT EXISTS scoreboard_placeholders (" +
                        "uuid TEXT NOT NULL, position INTEGER NOT NULL, placeholder_id TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, position))");
            }
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Failed to open scoreboard SQLite database: " + e.getMessage());
            connection = null;
        }
    }

    private void migrateLegacyYaml() {
        if (connection == null) return;
        File legacy = new File(plugin.getDataFolder(), "scoreboard_data.yml");
        if (!legacy.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(legacy);
        int migrated = 0;
        for (String key : data.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean enabled = data.getBoolean(key + ".enabled", true);
            List<String> placeholders = data.getStringList(key + ".placeholders");
            if (placeholders.isEmpty()) {
                placeholders = data.getStringList(key + ".sections");
            }
            savePlayerRaw(uuid, enabled, placeholders);
            migrated++;
        }

        File renamed = new File(plugin.getDataFolder(), "scoreboard_data.yml.bak");
        if (legacy.renameTo(renamed)) {
            plugin.getLogger().info("Migrated " + migrated + " scoreboard profiles from scoreboard_data.yml into SQLite.");
        }
    }

    public PlayerScoreboardState getState(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDb);
    }

    private PlayerScoreboardState loadFromDb(UUID uuid) {
        List<String> known = plugin.getScoreboardConfig().getPlaceholderOrder();
        if (connection == null) return defaultState(known);

        try {
            boolean enabled = true;
            boolean found = false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT enabled FROM scoreboard_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        enabled = rs.getInt("enabled") != 0;
                        found = true;
                    }
                }
            }
            if (!found) return defaultState(known);

            List<String> placeholders = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT placeholder_id FROM scoreboard_placeholders WHERE uuid = ? ORDER BY position ASC")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) placeholders.add(rs.getString("placeholder_id"));
                }
            }
            placeholders.retainAll(known);
            return new PlayerScoreboardState(enabled, placeholders);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load scoreboard state for " + uuid + ": " + e.getMessage());
            return defaultState(known);
        }
    }

    private PlayerScoreboardState defaultState(List<String> known) {
        int max = plugin.getScoreboardConfig().getMaxPlaceholders();
        List<String> defaults = new ArrayList<>(known.subList(0, Math.min(max, known.size())));
        return new PlayerScoreboardState(true, defaults);
    }

    public void savePlayer(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) return;
        savePlayerRaw(uuid, state.isScoreboardEnabled(), state.getActivePlaceholders());
    }

    private void savePlayerRaw(UUID uuid, boolean enabled, List<String> placeholders) {
        if (connection == null) return;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO scoreboard_players (uuid, enabled) VALUES (?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, enabled ? 1 : 0);
                ps.executeUpdate();
            }
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM scoreboard_placeholders WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT INTO scoreboard_placeholders (uuid, position, placeholder_id) VALUES (?, ?, ?)")) {
                int pos = 0;
                for (String id : placeholders) {
                    ins.setString(1, uuid.toString());
                    ins.setInt(2, pos++);
                    ins.setString(3, id);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save scoreboard state for " + uuid + ": " + e.getMessage());
            try { connection.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            savePlayer(uuid);
        }
    }

    public void unload(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    public void close() {
        saveAll();
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }
}

package me.lovelace.loveTweaks.scoreboard;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Асинхронный менеджер данных скорборда с пулом соединений HikariCP и потокобезопасным кэшем.
 * Все запросы к БД выполняются строго асинхронно, соединения не удерживаются между тиками.
 */
public class ScoreboardDataManager {

    private final LoveTweaks plugin;
    private HikariDataSource dataSource;

    // Потокобезопасный кэш активных состояний скорборда игроков
    private final Map<UUID, PlayerScoreboardState> cache = new ConcurrentHashMap<>();

    public ScoreboardDataManager(LoveTweaks plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        cache.clear();
        initDataSource();
        initSchemaAndMigrate();
    }

    private void initDataSource() {
        closeDataSource();
        try {
            plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "lovetweaks.db");

            HikariConfig config = new HikariConfig();
            config.setPoolName("LoveTweaks-ScoreboardPool");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(30000);
            config.setMaxLifetime(60000);
            config.setConnectionTimeout(10000);
            config.setLeakDetectionThreshold(10000); // 10s leak detection

            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize HikariCP database pool: " + e.getMessage(), e);
            dataSource = null;
        }
    }

    private void initSchemaAndMigrate() {
        if (dataSource == null) return;
        CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 Statement st = connection.createStatement()) {

                // Schema versioning
                st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)");
                st.execute("CREATE TABLE IF NOT EXISTS scoreboard_players (" +
                        "uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 1)");
                st.execute("CREATE TABLE IF NOT EXISTS scoreboard_placeholders (" +
                        "uuid TEXT NOT NULL, position INTEGER NOT NULL, placeholder_id TEXT NOT NULL, " +
                        "PRIMARY KEY (uuid, position))");

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize scoreboard SQLite tables", e);
            }

            migrateLegacyYaml();
        });
    }

    private void migrateLegacyYaml() {
        if (dataSource == null) return;
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
            savePlayerSync(uuid, enabled, placeholders);
            migrated++;
        }

        File renamed = new File(plugin.getDataFolder(), "scoreboard_data.yml.bak");
        if (legacy.renameTo(renamed)) {
            plugin.getLogger().info("Migrated " + migrated + " scoreboard profiles from scoreboard_data.yml into SQLite.");
        }
    }

    /**
     * Возвращает состояние скорборда из кэша (или синхронный fallback/default).
     */
    public PlayerScoreboardState getState(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) {
            state = loadFromDbSync(uuid);
            cache.put(uuid, state);
        }
        return state;
    }

    /**
     * Асинхронно предзагружает состояние игрока при входе.
     */
    public CompletableFuture<PlayerScoreboardState> loadPlayerAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerScoreboardState state = loadFromDbSync(uuid);
            cache.put(uuid, state);
            return state;
        });
    }

    private PlayerScoreboardState loadFromDbSync(UUID uuid) {
        List<String> known = plugin.getScoreboardConfig().getPlaceholderOrder();
        if (dataSource == null) return defaultState(known);

        try (Connection connection = dataSource.getConnection()) {
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
            plugin.getLogger().log(Level.WARNING, "Failed to load scoreboard state for " + uuid + ": " + e.getMessage(), e);
            return defaultState(known);
        }
    }

    private PlayerScoreboardState defaultState(List<String> known) {
        int max = plugin.getScoreboardConfig().getMaxPlaceholders();
        List<String> defaults = new ArrayList<>(known.subList(0, Math.min(max, known.size())));
        return new PlayerScoreboardState(true, defaults);
    }

    /**
     * Асинхронно сохраняет профиль игрока.
     */
    public CompletableFuture<Void> savePlayerAsync(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) return CompletableFuture.completedFuture(null);

        boolean enabled = state.isScoreboardEnabled();
        List<String> placeholders = new ArrayList<>(state.getActivePlaceholders());

        return CompletableFuture.runAsync(() -> savePlayerSync(uuid, enabled, placeholders));
    }

    public void savePlayer(UUID uuid) {
        savePlayerAsync(uuid);
    }

    private void savePlayerSync(UUID uuid, boolean enabled, List<String> placeholders) {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
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
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.FINE, "Failed to rollback scoreboard transaction: " + rollbackEx.getMessage());
                }
                throw e;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save scoreboard state for " + uuid + ": " + e.getMessage(), e);
        }
    }

    public void saveAll() {
        try {
            for (Map.Entry<UUID, PlayerScoreboardState> entry : cache.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerScoreboardState state = entry.getValue();
                if (uuid != null && state != null) {
                    try {
                        savePlayerSync(uuid, state.isScoreboardEnabled(), state.getActivePlaceholders());
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed saving state for player " + uuid + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Error during scoreboard saveAll: " + t.getMessage(), t);
        }
    }

    public void unload(UUID uuid) {
        PlayerScoreboardState state = cache.remove(uuid);
        if (state != null) {
            CompletableFuture.runAsync(() -> savePlayerSync(uuid, state.isScoreboardEnabled(), state.getActivePlaceholders()));
        }
    }

    public synchronized void close() {
        try {
            saveAll();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Error saving scoreboard data on close: " + t.getMessage(), t);
        }
        closeDataSource();
        try {
            cache.clear();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Cache clear on close: " + t.getMessage());
        }
    }

    private void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error while closing HikariDataSource: " + e.getMessage(), e);
            } finally {
                dataSource = null;
            }
        }
    }
}

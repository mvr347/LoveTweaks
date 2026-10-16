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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Асинхронный менеджер данных скорборда с пулом соединений HikariCP и потокобезопасным кэшем.
 * Все запросы к БД выполняются строго асинхронно, соединения не удерживаются между тиками.
 */
public class ScoreboardDataManager {

    private final LoveTweaks plugin;
    private volatile HikariDataSource dataSource;

    // Потокобезопасный кэш активных состояний скорборда игроков
    private final Map<UUID, PlayerScoreboardState> cache = new ConcurrentHashMap<>();

    // Players whose state may live in the cache (joined/authenticated and not yet unloaded).
    // A load finishing after unload must not re-add its entry (that leaked the state forever).
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    // Dedicated single thread: loads and saves of one player stay ordered (load -> save -> unload)
    // and never compete with the shared ForkJoinPool.commonPool.
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LoveTweaks-ScoreboardIO");
        t.setDaemon(true);
        return t;
    });

    private volatile List<ScoreboardEntry> defaultEntries;

    public ScoreboardDataManager(LoveTweaks plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        cache.clear();
        defaultEntries = null;
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

    /**
     * The schema is created and migrated synchronously: player loads start right after enable, and a
     * load racing an async ALTER TABLE would hit a missing column and fall back to defaults (and then
     * overwrite the saved profile). Only the legacy YAML import stays asynchronous.
     */
    private void initSchemaAndMigrate() {
        HikariDataSource ds = dataSource;
        if (ds == null) return;
        try (Connection connection = ds.getConnection();
             Statement st = connection.createStatement()) {

            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)");
            st.execute("CREATE TABLE IF NOT EXISTS scoreboard_players (" +
                    "uuid TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE IF NOT EXISTS scoreboard_placeholders (" +
                    "uuid TEXT NOT NULL, position INTEGER NOT NULL, placeholder_id TEXT NOT NULL, " +
                    "PRIMARY KEY (uuid, position))");

            // Columns added after the first release: view_all (menu view) and variant (line format).
            addMissingColumns(connection, SchemaMigration.PLAYERS_TABLE, SchemaMigration.PLAYERS_COLUMNS);
            addMissingColumns(connection, SchemaMigration.PLACEHOLDERS_TABLE, SchemaMigration.PLACEHOLDERS_COLUMNS);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize scoreboard SQLite tables", e);
        }

        CompletableFuture.runAsync(this::migrateLegacyYaml, io);
    }

    private void addMissingColumns(Connection connection, String table, Map<String, String> required) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) existing.add(rs.getString("name"));
        }
        for (String sql : SchemaMigration.missingColumnStatements(table, existing, required)) {
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
            }
        }
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
            List<ScoreboardEntry> entries = new ArrayList<>();
            for (String id : placeholders) entries.add(new ScoreboardEntry(id));
            savePlayerSync(uuid, enabled, false, entries);
            migrated++;
        }

        File renamed = new File(plugin.getDataFolder(), "scoreboard_data.yml.bak");
        if (legacy.renameTo(renamed)) {
            plugin.getLogger().info("Migrated " + migrated + " scoreboard profiles from scoreboard_data.yml into SQLite.");
        }
    }

    /**
     * Возвращает состояние скорборда из кэша. Никогда не блокирует вызывающий поток:
     * при промахе кэша отдаёт безопасный default и асинхронно догружает реальное
     * состояние из БД, кладя его в кэш для последующих вызовов.
     */
    public PlayerScoreboardState getState(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) {
            // Only (re)load for players that are still tracked; otherwise a stale caller would
            // re-create a cache entry that nobody ever removes.
            if (active.contains(uuid) && loading.add(uuid)) {
                loadPlayerAsync(uuid);
            }
            state = defaultState(plugin.getScoreboardConfig().getPlaceholderOrder());
        }
        return state;
    }

    /**
     * Асинхронно предзагружает состояние игрока при входе.
     */
    public CompletableFuture<PlayerScoreboardState> loadPlayerAsync(UUID uuid) {
        active.add(uuid);
        loading.add(uuid);
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerScoreboardState state = loadFromDbSync(uuid);
                if (!active.contains(uuid)) return state; // player left while loading: do not cache
                PlayerScoreboardState prev = cache.putIfAbsent(uuid, state);
                return prev != null ? prev : state;
            } finally {
                loading.remove(uuid);
            }
        }, io);
    }

    private PlayerScoreboardState loadFromDbSync(UUID uuid) {
        ScoreboardConfig cfg = plugin.getScoreboardConfig();
        HikariDataSource ds = dataSource;
        if (ds == null) return defaultState(cfg.getPlaceholderOrder());

        try (Connection connection = ds.getConnection()) {
            boolean enabled = true;
            boolean viewAll = false;
            boolean found = false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT enabled, view_all FROM scoreboard_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        enabled = rs.getInt("enabled") != 0;
                        viewAll = rs.getInt("view_all") != 0;
                        found = true;
                    }
                }
            }
            if (!found) return defaultState(cfg.getPlaceholderOrder());

            List<ScoreboardEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT placeholder_id, variant FROM scoreboard_placeholders WHERE uuid = ? ORDER BY position ASC")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) entries.add(new ScoreboardEntry(rs.getString("placeholder_id"), rs.getInt("variant")));
                }
            }
            return new PlayerScoreboardState(enabled, viewAll, sanitize(entries, cfg));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load scoreboard state for " + uuid + ": " + e.getMessage(), e);
            return defaultState(cfg.getPlaceholderOrder());
        }
    }

    /**
     * Brings stored rows up to date: legacy ids renamed (date/time/server_time -> datetime) BEFORE
     * unknown ids are dropped, variants clamped, lines whose PAPI expansion is not installed removed
     * and the count cut to the configured limit.
     */
    private List<ScoreboardEntry> sanitize(List<ScoreboardEntry> stored, ScoreboardConfig cfg) {
        List<ScoreboardEntry> out = new ArrayList<>();
        for (ScoreboardEntry e : LegacyMigration.migrate(stored)) {
            ScoreboardPlaceholder ph = cfg.getPlaceholder(e.id());
            if (ph == null || !PlaceholderIntegration.isInstalled(ph)) continue;
            out.add(new ScoreboardEntry(e.id(), VariantCycler.normalize(e.variant(), ph.variantCount())));
            if (out.size() >= cfg.getMaxPlaceholders()) break;
        }
        return out;
    }

    /** New players start with the configured default preset (only installed lines count). */
    private PlayerScoreboardState defaultState(List<String> known) {
        List<ScoreboardEntry> cached = defaultEntries;
        if (cached != null) return new PlayerScoreboardState(true, false, new ArrayList<>(cached));
        ScoreboardConfig cfg = plugin.getScoreboardConfig();
        ScoreboardPreset preset = cfg.getPresets().get(cfg.getDefaultPreset());
        List<ScoreboardEntry> entries;
        if (preset != null) {
            entries = PresetApplier.resolve(preset.entries(),
                    id -> cfg.getPlaceholder(id) != null && PlaceholderIntegration.isInstalled(cfg.getPlaceholder(id)),
                    id -> cfg.getPlaceholder(id) != null ? cfg.getPlaceholder(id).variantCount() : 0,
                    cfg.getMaxPlaceholders());
        } else {
            entries = new ArrayList<>();
            for (String id : known) {
                if (entries.size() >= cfg.getMaxPlaceholders()) break;
                if (cfg.getPlaceholder(id) != null && PlaceholderIntegration.isInstalled(cfg.getPlaceholder(id))) {
                    entries.add(new ScoreboardEntry(id));
                }
            }
        }
        defaultEntries = List.copyOf(entries);
        return new PlayerScoreboardState(true, false, entries);
    }

    /**
     * Асинхронно сохраняет профиль игрока.
     */
    public CompletableFuture<Void> savePlayerAsync(UUID uuid) {
        PlayerScoreboardState state = cache.get(uuid);
        if (state == null) return CompletableFuture.completedFuture(null);

        boolean enabled = state.isScoreboardEnabled();
        boolean viewAll = state.isViewAll();
        List<ScoreboardEntry> entries = state.entries();

        return CompletableFuture.runAsync(() -> savePlayerSync(uuid, enabled, viewAll, entries), io);
    }

    public void savePlayer(UUID uuid) {
        savePlayerAsync(uuid);
    }

    private void savePlayerSync(UUID uuid, boolean enabled, boolean viewAll, List<ScoreboardEntry> entries) {
        HikariDataSource ds = dataSource;
        if (ds == null) return;
        try (Connection connection = ds.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO scoreboard_players (uuid, enabled, view_all) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled, view_all = excluded.view_all")) {
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, enabled ? 1 : 0);
                    ps.setInt(3, viewAll ? 1 : 0);
                    ps.executeUpdate();
                }
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM scoreboard_placeholders WHERE uuid = ?")) {
                    del.setString(1, uuid.toString());
                    del.executeUpdate();
                }
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO scoreboard_placeholders (uuid, position, placeholder_id, variant) VALUES (?, ?, ?, ?)")) {
                    int pos = 0;
                    for (ScoreboardEntry entry : entries) {
                        ins.setString(1, uuid.toString());
                        ins.setInt(2, pos++);
                        ins.setString(3, entry.id());
                        ins.setInt(4, entry.variant());
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
                        savePlayerSync(uuid, state.isScoreboardEnabled(), state.isViewAll(), state.entries());
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
        active.remove(uuid);
        PlayerScoreboardState state = cache.remove(uuid);
        if (state != null) {
            CompletableFuture.runAsync(() -> savePlayerSync(uuid, state.isScoreboardEnabled(), state.isViewAll(), state.entries()), io);
        }
    }

    public synchronized void close() {
        io.shutdown(); // queued loads/saves finish first, keeping per-player ordering
        try {
            io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

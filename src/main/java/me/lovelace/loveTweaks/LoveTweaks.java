package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.commands.LoveTweaksAdminCommand;
import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.integration.ChatFilterIntegration;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.placeholder.PlaytimeExpansion;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.listeners.CoordinateTeleportScrollListener;
import me.lovelace.loveTweaks.listeners.EnchantmentListener;
import me.lovelace.loveTweaks.listeners.EnderChestListener;
import me.lovelace.loveTweaks.listeners.FirstJoinItemsListener;
import me.lovelace.loveTweaks.listeners.HeraldListener;
import me.lovelace.loveTweaks.listeners.HungerListener;
import me.lovelace.loveTweaks.listeners.ItemDropLossListener;
import me.lovelace.loveTweaks.listeners.MilkListener;
import me.lovelace.loveTweaks.listeners.ScoreboardListener;
import me.lovelace.loveTweaks.listeners.TeleportScrollListener;
import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollManager;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDataManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public final class LoveTweaks extends JavaPlugin {

    private static LoveTweaks instance;

    private LoveTweaksConfig loveTweaksConfig;
    private NamespacedKey enderChestKey;
    private TeleportScrollManager teleportScrollManager;
    private CoordinateTeleportScrollManager coordTeleportScrollManager;

    private ScoreboardDataManager scoreboardDataManager;
    private ScoreboardDisplayManager scoreboardDisplayManager;
    private BukkitTask scoreboardTask;

    private CitizensIntegration citizensIntegration;
    private ChatFilterIntegration chatFilterIntegration;
    private HeraldManager heraldManager;
    private BukkitTask heraldBroadcastTask;

    @Override
    public void onEnable() {
        instance = this;

        loveTweaksConfig = new LoveTweaksConfig(this);
        getLogger().info("LoveTweaks config loaded.");

        enderChestKey = new NamespacedKey(this, "ender_chest_inventory");

        warnAboutLegacyTeleportScrollFolder();

        TeleportScroll.init(this);
        teleportScrollManager = new TeleportScrollManager(this);
        getLogger().info("TeleportScroll manager initialized.");

        CoordinateTeleportScroll.init(this);
        coordTeleportScrollManager = new CoordinateTeleportScrollManager(this);
        getLogger().info("CoordinateTeleportScroll manager initialized.");

        // Scoreboard
        scoreboardDataManager = new ScoreboardDataManager(this);
        scoreboardDisplayManager = new ScoreboardDisplayManager(this, scoreboardDataManager, getScoreboardConfig());
        getLogger().info("Scoreboard system initialized.");

        // Плейсхолдеры времени игры (%playtime_since_join% и др.) — их использует скорборд.
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaytimeExpansion(this).register();
            getLogger().info("Playtime placeholders registered.");
        } else {
            getLogger().warning("PlaceholderAPI не найден — плейсхолдеры %playtime_*% работать не будут.");
        }

        // Королевский Глашатай
        citizensIntegration = new CitizensIntegration();
        chatFilterIntegration = new ChatFilterIntegration();
        heraldManager = new HeraldManager(this, chatFilterIntegration);
        getLogger().info("Herald manager initialized.");

        // Listeners
        getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer().getPluginManager().registerEvents(new HungerListener(this), this);
        getServer().getPluginManager().registerEvents(new MilkListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemDropLossListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportScrollListener(this, teleportScrollManager), this);
        getServer().getPluginManager().registerEvents(new CoordinateTeleportScrollListener(this, coordTeleportScrollManager), this);
        getServer().getPluginManager().registerEvents(new FirstJoinItemsListener(this), this);
        getServer().getPluginManager().registerEvents(
                new HeraldListener(this, heraldManager, citizensIntegration), this);

        ScoreboardListener scoreboardListener = new ScoreboardListener(this, scoreboardDataManager, scoreboardDisplayManager);
        getServer().getPluginManager().registerEvents(scoreboardListener, this);
        getCommand("scoreboard").setExecutor(scoreboardListener);

        // Единая административная команда: /lovetweaksadmin (алиас /lovetweaks — см. plugin.yml)
        LoveTweaksAdminCommand adminCommand = new LoveTweaksAdminCommand(this);
        getCommand("lovetweaksadmin").setExecutor(adminCommand);
        getCommand("lovetweaksadmin").setTabCompleter(adminCommand);

        // Hunger exhaustion task
        if (loveTweaksConfig.getExtraExhaustionPerSecond() > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                            player.setExhaustion(player.getExhaustion() + loveTweaksConfig.getExtraExhaustionPerSecond());
                        }
                    }
                }
            }.runTaskTimer(this, 20L, 20L);
        }

        // Scoreboard update task
        startScoreboardTask();

        // Herald broadcast task
        startHeraldBroadcastTask();
    }

    /**
     * Все данные и конфиг свитков телепортации (обычного и координатного) всегда жили и живут
     * внутри {@code plugins/LoveTweaks/} — в {@code config.yml}, секции {@code teleport-scroll}
     * и {@code coord-teleport-scroll}. Отдельная папка {@code plugins/LoveTeleportScroll/}
     * никогда не создавалась кодом этого плагина. Если она всё же существует на диске — это
     * след старой отдельной установки, предшествовавшей переносу фичи в LoveTweaks; сам плагин
     * её не трогает (ничего не удаляет и не читает оттуда), только предупреждает в консоли,
     * чтобы админ мог убрать её вручную и не путаться, откуда берётся конфиг.
     */
    private void warnAboutLegacyTeleportScrollFolder() {
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder == null) {
            return;
        }
        File legacyFolder = new File(pluginsFolder, "LoveTeleportScroll");
        if (legacyFolder.isDirectory()) {
            getLogger().warning("Найдена папка " + legacyFolder.getPath() + " — она не используется LoveTweaks. "
                    + "Весь конфиг свитков телепортации хранится в " + getDataFolder().getPath()
                    + "/config.yml (секции teleport-scroll / coord-teleport-scroll). "
                    + "Эту папку можно безопасно удалить после сверки её содержимого.");
        }
    }

    private void startScoreboardTask() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        int interval = getScoreboardConfig().getUpdateInterval();
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                scoreboardDisplayManager.updateAll();
            }
        }.runTaskTimer(this, interval, interval);
    }

    private void startHeraldBroadcastTask() {
        if (heraldBroadcastTask != null) heraldBroadcastTask.cancel();
        long intervalTicks = 20L * 60L;
        heraldBroadcastTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (loveTweaksConfig.isHeraldEnabled()) {
                    heraldManager.tickBroadcast();
                }
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (scoreboardDisplayManager != null) scoreboardDisplayManager.removeAll();
        if (scoreboardDataManager != null) scoreboardDataManager.close();
        getLogger().info("LoveTweaks disabled.");
    }

    /**
     * Полная перезагрузка плагина: конфиг, состояние скорборда, PAPI-плейсхолдеры скорборда,
     * число слотов Глашатая и оба повторяющихся таска. Используется командой
     * {@code /lovetweaksadmin reload} — см. {@link me.lovelace.loveTweaks.commands.LoveTweaksAdminCommand}.
     */
    public void reloadAll() {
        loveTweaksConfig.loadConfig();
        scoreboardDataManager.reload();
        scoreboardDisplayManager.refreshPAPI();
        heraldManager.resize(loveTweaksConfig.getHeraldSlots());
        startScoreboardTask();
        startHeraldBroadcastTask();
    }

    public static LoveTweaks getInstance() { return instance; }
    public LoveTweaksConfig getLoveTweaksConfig() { return loveTweaksConfig; }
    public ScoreboardConfig getScoreboardConfig() { return loveTweaksConfig.getScoreboardConfig(); }
    public ScoreboardDataManager getScoreboardDataManager() { return scoreboardDataManager; }
    public ScoreboardDisplayManager getScoreboardDisplayManager() { return scoreboardDisplayManager; }
    public NamespacedKey getEnderChestKey() { return enderChestKey; }
    public TeleportScrollManager getTeleportScrollManager() { return teleportScrollManager; }
    public CitizensIntegration getCitizensIntegration() { return citizensIntegration; }
    public HeraldManager getHeraldManager() { return heraldManager; }
}

package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.integration.ChatFilterIntegration;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.placeholder.PlaytimeExpansion;
import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.listeners.EnchantmentListener;
import me.lovelace.loveTweaks.listeners.EnderChestListener;
import me.lovelace.loveTweaks.listeners.FirstJoinItemsListener;
import me.lovelace.loveTweaks.listeners.HeraldListener;
import me.lovelace.loveTweaks.listeners.HungerListener;
import me.lovelace.loveTweaks.listeners.ItemDropLossListener;
import me.lovelace.loveTweaks.listeners.MilkListener;
import me.lovelace.loveTweaks.listeners.ScoreboardListener;
import me.lovelace.loveTweaks.listeners.TeleportScrollListener;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDataManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class LoveTweaks extends JavaPlugin {

    private static LoveTweaks instance;

    private LoveTweaksConfig loveTweaksConfig;
    private NamespacedKey enderChestKey;
    private TeleportScrollManager teleportScrollManager;

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
        getServer().getPluginManager().registerEvents(new FirstJoinItemsListener(this), this);
        getServer().getPluginManager().registerEvents(
                new HeraldListener(this, heraldManager, citizensIntegration), this);

        ScoreboardListener scoreboardListener = new ScoreboardListener(this, scoreboardDataManager, scoreboardDisplayManager);
        getServer().getPluginManager().registerEvents(scoreboardListener, this);
        getCommand("scoreboard").setExecutor(scoreboardListener);

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
     * Все данные и конфиг свитков телепортации всегда жили и живут внутри
     * {@code plugins/LoveTweaks/} — в {@code config.yml}, секции {@code teleport-scroll}.
     * Отдельная папка {@code plugins/LoveTeleportScroll/} никогда не создавалась кодом этого
     * плагина. Если она всё же существует на диске — это след старой отдельной установки,
     * предшествовавшей переносу фичи в LoveTweaks; сам плагин её не трогает (ничего не удаляет
     * и не читает оттуда), только предупреждает в консоли, чтобы админ мог убрать её вручную и
     * не путаться, откуда берётся конфиг.
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
                    + "/config.yml (секция teleport-scroll). "
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
        if (scoreboardDisplayManager != null) scoreboardDisplayManager.removeAll();
        if (scoreboardDataManager != null) scoreboardDataManager.close();
        getLogger().info("LoveTweaks disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("lovetweaks")) return false;

        if (args.length == 0) {
            sender.sendMessage(msg("usage-main"));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                loveTweaksConfig.loadConfig();
                scoreboardDataManager.reload();
                scoreboardDisplayManager.refreshPAPI();
                heraldManager.resize(loveTweaksConfig.getHeraldSlots());
                startScoreboardTask();
                startHeraldBroadcastTask();
                sender.sendMessage(msg("reload-done"));
                yield true;
            }
            case "herald" -> handleHeraldCommand(sender, args);
            case "givescroll" -> {
                if (!sender.hasPermission("lovetweaks.admin")) {
                    sender.sendMessage(msg("no-permission"));
                    yield true;
                }
                if (args.length < 2) {
                    sender.sendMessage(msg("usage-givescroll"));
                    yield true;
                }
                Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(msg("player-not-found", "<player>", args[1]));
                    yield true;
                }
                target.getInventory().addItem(TeleportScroll.create());
                sender.sendMessage(msg("scroll-given-sender", "<player>", target.getName()));
                target.sendMessage(msg("scroll-given-target"));
                yield true;
            }
            default -> {
                sender.sendMessage(msg("usage-main"));
                yield false;
            }
        };
    }

    private boolean handleHeraldCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovetweaks.admin")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg("usage-herald"));
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "bind" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg("players-only"));
                    yield true;
                }
                if (!citizensIntegration.isAvailable()) {
                    sender.sendMessage(msg("citizens-missing"));
                    yield true;
                }
                CitizensIntegration.NpcRef npc = citizensIntegration.bindTarget(player, 6.0);
                if (npc == null) {
                    sender.sendMessage(msg("npc-not-selected"));
                    yield true;
                }
                loveTweaksConfig.setHeraldNpc(npc.id(), npc.name());
                sender.sendMessage(msg("herald-npc-bound", "<npc>", npc.name()));
                yield true;
            }
            case "unbind" -> {
                loveTweaksConfig.setHeraldNpc(-1, "");
                sender.sendMessage(msg("herald-npc-unbound"));
                yield true;
            }
            case "clear" -> {
                heraldManager.clear();
                sender.sendMessage(msg("herald-cleared"));
                yield true;
            }
            default -> {
                sender.sendMessage(msg("usage-herald"));
                yield true;
            }
        };
    }

    private String msg(String key) {
        return loveTweaksConfig.message(key).replace('&', '§');
    }

    private String msg(String key, String placeholder, String value) {
        return loveTweaksConfig.message(key).replace(placeholder, value).replace('&', '§');
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

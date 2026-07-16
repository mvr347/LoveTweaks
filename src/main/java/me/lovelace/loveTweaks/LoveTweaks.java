package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.listeners.EnchantmentListener;
import me.lovelace.loveTweaks.listeners.EnderChestListener;
import me.lovelace.loveTweaks.listeners.HungerListener;
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

public final class LoveTweaks extends JavaPlugin {

    private static LoveTweaks instance;

    private LoveTweaksConfig loveTweaksConfig;
    private NamespacedKey enderChestKey;
    private TeleportScrollManager teleportScrollManager;

    private ScoreboardDataManager scoreboardDataManager;
    private ScoreboardDisplayManager scoreboardDisplayManager;
    private BukkitTask scoreboardTask;

    @Override
    public void onEnable() {
        instance = this;

        loveTweaksConfig = new LoveTweaksConfig(this);
        getLogger().info("LoveTweaks config loaded.");

        enderChestKey = new NamespacedKey(this, "ender_chest_inventory");

        TeleportScroll.init(this);
        teleportScrollManager = new TeleportScrollManager(this);
        getLogger().info("TeleportScroll manager initialized.");

        // Scoreboard
        scoreboardDataManager = new ScoreboardDataManager(this);
        scoreboardDisplayManager = new ScoreboardDisplayManager(this, scoreboardDataManager, getScoreboardConfig());
        getLogger().info("Scoreboard system initialized.");

        // Listeners
        getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer().getPluginManager().registerEvents(new HungerListener(this), this);
        getServer().getPluginManager().registerEvents(new MilkListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportScrollListener(this, teleportScrollManager), this);

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
            sender.sendMessage("§eИспользование: §f/lovetweaks <reload|givescroll>");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                loveTweaksConfig.loadConfig();
                scoreboardDataManager.reload();
                scoreboardDisplayManager.refreshPAPI();
                startScoreboardTask();
                sender.sendMessage("§aLoveTweaks конфиг перезагружен!");
                yield true;
            }
            case "givescroll" -> {
                if (!sender.hasPermission("lovetweaks.admin")) {
                    sender.sendMessage("§cНедостаточно прав.");
                    yield true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§eИспользование: §f/lovetweaks givescroll <игрок>");
                    yield true;
                }
                Player target = getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + "§c не найден или не в сети.");
                    yield true;
                }
                target.getInventory().addItem(TeleportScroll.create());
                sender.sendMessage("§aСвиток телепортации выдан игроку §e" + target.getName() + "§a.");
                target.sendMessage("§6Вы получили §eСвиток телепортации§6!");
                yield true;
            }
            default -> {
                sender.sendMessage("§eИспользование: §f/lovetweaks <reload|givescroll>");
                yield false;
            }
        };
    }

    public static LoveTweaks getInstance() { return instance; }
    public LoveTweaksConfig getLoveTweaksConfig() { return loveTweaksConfig; }
    public ScoreboardConfig getScoreboardConfig() { return loveTweaksConfig.getScoreboardConfig(); }
    public ScoreboardDataManager getScoreboardDataManager() { return scoreboardDataManager; }
    public ScoreboardDisplayManager getScoreboardDisplayManager() { return scoreboardDisplayManager; }
    public NamespacedKey getEnderChestKey() { return enderChestKey; }
    public TeleportScrollManager getTeleportScrollManager() { return teleportScrollManager; }
}

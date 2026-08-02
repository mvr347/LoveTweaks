package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.listeners.EnchantmentListener;
import me.lovelace.loveTweaks.listeners.EnderChestListener;
import me.lovelace.loveTweaks.listeners.FirstJoinItemsListener;
import me.lovelace.loveTweaks.listeners.HeraldListener;
import me.lovelace.loveTweaks.listeners.HungerListener;
import me.lovelace.loveTweaks.listeners.ItemDropLossListener;
import me.lovelace.loveTweaks.listeners.MilkListener;
import me.lovelace.loveTweaks.listeners.PostListener;
import me.lovelace.loveTweaks.listeners.ScoreboardListener;
import me.lovelace.loveTweaks.listeners.TeleportScrollListener;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import me.lovelace.loveTweaks.post.PostManager;
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

    private CitizensIntegration citizensIntegration;
    private HeraldManager heraldManager;
    private BukkitTask heraldBroadcastTask;

    private PostManager postManager;
    private BukkitTask postFlightTask;

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

        // Королевский Глашатай
        citizensIntegration = new CitizensIntegration();
        heraldManager = new HeraldManager(this);
        getLogger().info("Herald manager initialized.");

        // Королевская Почта
        postManager = new PostManager(this);
        getLogger().info("Post manager initialized.");

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
        getServer().getPluginManager().registerEvents(new PostListener(this, postManager, citizensIntegration), this);

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

        // Post pigeon flight tick task
        postFlightTask = new BukkitRunnable() {
            @Override
            public void run() {
                postManager.tickFlights();
            }
        }.runTaskTimer(this, 2L, 2L);
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
        long intervalTicks = loveTweaksConfig.getHeraldBroadcastIntervalHours() * 60L * 60L * 20L;
        heraldBroadcastTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (loveTweaksConfig.isHeraldEnabled()) {
                    heraldManager.broadcast();
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
            sender.sendMessage("§eИспользование: §f/lovetweaks <reload|givescroll|herald|post>");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                loveTweaksConfig.loadConfig();
                scoreboardDataManager.reload();
                scoreboardDisplayManager.refreshPAPI();
                startScoreboardTask();
                startHeraldBroadcastTask();
                sender.sendMessage("§aLoveTweaks конфиг перезагружен!");
                yield true;
            }
            case "herald" -> handleHeraldCommand(sender, args);
            case "post" -> handlePostCommand(sender, args);
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
                sender.sendMessage("§eИспользование: §f/lovetweaks <reload|givescroll|herald|post>");
                yield false;
            }
        };
    }

    private boolean handlePostCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovetweaks.admin")) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: §f/lovetweaks post <bind|unbind>");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "bind" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cЭта команда доступна только игрокам.");
                    yield true;
                }
                if (!citizensIntegration.isAvailable()) {
                    sender.sendMessage("§cCitizens не установлен или не включён.");
                    yield true;
                }
                CitizensIntegration.NpcRef npc = citizensIntegration.bindTarget(player, 6.0);
                if (npc == null) {
                    sender.sendMessage("§cВыберите NPC (§e/npc select§c) или посмотрите на него и повторите команду.");
                    yield true;
                }
                loveTweaksConfig.setPostNpc(npc.id(), npc.name());
                sender.sendMessage("§aNPC Почтмейстера привязан: §e" + npc.name());
                yield true;
            }
            case "unbind" -> {
                loveTweaksConfig.setPostNpc(-1, "");
                sender.sendMessage("§aNPC Почтмейстера отвязан.");
                yield true;
            }
            default -> {
                sender.sendMessage("§eИспользование: §f/lovetweaks post <bind|unbind>");
                yield true;
            }
        };
    }

    private boolean handleHeraldCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovetweaks.admin")) {
            sender.sendMessage("§cНедостаточно прав.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eИспользование: §f/lovetweaks herald <bind|unbind|clear>");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "bind" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cЭта команда доступна только игрокам.");
                    yield true;
                }
                if (!citizensIntegration.isAvailable()) {
                    sender.sendMessage("§cCitizens не установлен или не включён.");
                    yield true;
                }
                CitizensIntegration.NpcRef npc = citizensIntegration.bindTarget(player, 6.0);
                if (npc == null) {
                    sender.sendMessage("§cВыберите NPC (§e/npc select§c) или посмотрите на него и повторите команду.");
                    yield true;
                }
                loveTweaksConfig.setHeraldNpc(npc.id(), npc.name());
                sender.sendMessage("§aNPC Глашатая привязан: §e" + npc.name());
                yield true;
            }
            case "unbind" -> {
                loveTweaksConfig.setHeraldNpc(-1, "");
                sender.sendMessage("§aNPC Глашатая отвязан.");
                yield true;
            }
            case "clear" -> {
                heraldManager.clear();
                sender.sendMessage("§aГолос Королевства сброшен.");
                yield true;
            }
            default -> {
                sender.sendMessage("§eИспользование: §f/lovetweaks herald <bind|unbind|clear>");
                yield true;
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
    public CitizensIntegration getCitizensIntegration() { return citizensIntegration; }
    public HeraldManager getHeraldManager() { return heraldManager; }
    public PostManager getPostManager() { return postManager; }
}

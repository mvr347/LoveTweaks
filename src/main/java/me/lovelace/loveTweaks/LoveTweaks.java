package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.achievements.CustomAchievementManager;
import me.lovelace.loveTweaks.achievements.data.PlayerAchievementData;
import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.listeners.AchievementListener;
import me.lovelace.loveTweaks.listeners.AchievementProgressListener;
import me.lovelace.loveTweaks.listeners.EnderChestListener;
import me.lovelace.loveTweaks.listeners.EnchantmentListener;
import me.lovelace.loveTweaks.listeners.HungerListener;
import me.lovelace.loveTweaks.listeners.MilkListener;
import me.lovelace.loveTweaks.listeners.TeleportScrollListener;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public final class LoveTweaks extends JavaPlugin {

    private static LoveTweaks instance;

    private LoveTweaksConfig loveTweaksConfig;
    private CustomAchievementManager customAchievementManager;
    private PlayerAchievementData playerAchievementData;
    private NamespacedKey enderChestKey;
    private TeleportScrollManager teleportScrollManager;

    @Override
    public void onEnable() {
        instance = this;

        // Конфиг → менеджеры → listeners (строгий порядок)
        this.loveTweaksConfig = new LoveTweaksConfig(this);
        getLogger().info("LoveTweaks config loaded.");

        this.customAchievementManager = new CustomAchievementManager(this);
        getLogger().info("Custom Achievement Manager initialized.");

        this.playerAchievementData = new PlayerAchievementData(this);
        getLogger().info("Player Achievement Data loaded.");

        this.enderChestKey = new NamespacedKey(this, "ender_chest_inventory");

        // Инициализируем ключ PDC для свитка телепортации
        TeleportScroll.init(this);
        this.teleportScrollManager = new TeleportScrollManager(this);
        getLogger().info("TeleportScroll manager initialized.");

        // Register listeners
        getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer().getPluginManager().registerEvents(new AchievementListener(this), this);
        getServer().getPluginManager().registerEvents(new AchievementProgressListener(this), this);
        getServer().getPluginManager().registerEvents(new HungerListener(this), this);
        getServer().getPluginManager().registerEvents(new MilkListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportScrollListener(this, teleportScrollManager), this);

        // Дополнительное истощение голода для игроков в SURVIVAL/ADVENTURE (усложнение игры)
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
            }.runTaskTimer(LoveTweaks.this, 20L, 20L);
        }
    }

    @Override
    public void onDisable() {
        if (playerAchievementData != null) {
            playerAchievementData.saveData();
        }
        getLogger().info("LoveTweaks disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("lovetweaks")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage("§eИспользование: §f/lovetweaks <reload|givescroll>");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                loveTweaksConfig.loadConfig();
                customAchievementManager.loadAchievements();
                playerAchievementData.loadData();
                sender.sendMessage("§aLoveTweaks config reloaded!");
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

    public static LoveTweaks getInstance() {
        return instance;
    }

    public LoveTweaksConfig getLoveTweaksConfig() {
        return loveTweaksConfig;
    }

    public CustomAchievementManager getCustomAchievementManager() {
        return customAchievementManager;
    }

    public PlayerAchievementData getPlayerAchievementData() {
        return playerAchievementData;
    }

    public NamespacedKey getEnderChestKey() {
        return enderChestKey;
    }

    public TeleportScrollManager getTeleportScrollManager() {
        return teleportScrollManager;
    }
}

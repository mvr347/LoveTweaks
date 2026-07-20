package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.items.FirstJoinItem;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LoveTweaksConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Ender Chests
    private boolean enderChestsAsNormalChests;

    // Enchantments
    private boolean disableMending;
    private boolean disableAllEnchantments;

    // Hunger
    private float extraExhaustionPerSecond;
    private float saturationMultiplier;

    // Milk
    private boolean disableMilk;

    // Потеря/поломка предметов при выбрасывании
    private boolean itemDropLossEnabled;
    private double itemDropLossChance;
    private double itemDropBreakChance;

    // Стартовый набор при первом заходе на сервер
    private boolean firstJoinEnabled;
    private final List<FirstJoinItem> firstJoinItems = new ArrayList<>();

    // Scoreboard (delegated to ScoreboardConfig)
    private final ScoreboardConfig scoreboardConfig = new ScoreboardConfig();

    public LoveTweaksConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        enderChestsAsNormalChests = config.getBoolean("ender-chests.as-normal-chests", false);
        disableMending = config.getBoolean("enchantments.disable-mending", false);
        disableAllEnchantments = config.getBoolean("enchantments.disable-all-enchantments", false);
        extraExhaustionPerSecond = (float) config.getDouble("hunger.extra-exhaustion-per-second", 0.25);
        saturationMultiplier = (float) config.getDouble("hunger.saturation-multiplier", 0.5);
        disableMilk = config.getBoolean("milk.disable-milk", true);

        itemDropLossEnabled = config.getBoolean("item-drop-loss.enabled", false);
        itemDropLossChance = config.getDouble("item-drop-loss.lose-chance", 0.22);
        itemDropBreakChance = config.getDouble("item-drop-loss.break-chance", 0.33);

        loadFirstJoinItems(config);

        scoreboardConfig.load(config);
    }

    private void loadFirstJoinItems(FileConfiguration config) {
        firstJoinEnabled = config.getBoolean("first-join.enabled", false);

        firstJoinItems.clear();
        for (Map<?, ?> raw : config.getMapList("first-join.items")) {
            Object materialObj = raw.get("material");
            Material material = materialObj != null
                    ? Material.matchMaterial(String.valueOf(materialObj).toUpperCase())
                    : null;
            if (material == null) material = Material.PAPER;

            int amount = 1;
            if (raw.get("amount") instanceof Number n) amount = Math.max(1, n.intValue());

            boolean teleportScroll = Boolean.TRUE.equals(raw.get("teleport-scroll"));

            String displayName = raw.get("display-name") != null ? String.valueOf(raw.get("display-name")) : null;

            List<String> lore = new ArrayList<>();
            if (raw.get("lore") instanceof List<?> loreList) {
                for (Object line : loreList) lore.add(String.valueOf(line));
            }

            firstJoinItems.add(new FirstJoinItem(material, amount, teleportScroll, displayName, lore));
        }
    }

    public boolean isEnderChestsAsNormalChests() { return enderChestsAsNormalChests; }
    public boolean isDisableMending() { return disableMending; }
    public boolean isDisableAllEnchantments() { return disableAllEnchantments; }
    public float getExtraExhaustionPerSecond() { return extraExhaustionPerSecond; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isDisableMilk() { return disableMilk; }
    public boolean isItemDropLossEnabled() { return itemDropLossEnabled; }
    public double getItemDropLossChance() { return itemDropLossChance; }
    public double getItemDropBreakChance() { return itemDropBreakChance; }
    public boolean isFirstJoinEnabled() { return firstJoinEnabled; }
    public List<FirstJoinItem> getFirstJoinItems() { return firstJoinItems; }
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
}

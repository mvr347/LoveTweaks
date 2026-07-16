package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

        scoreboardConfig.load(config);
    }

    public boolean isEnderChestsAsNormalChests() { return enderChestsAsNormalChests; }
    public boolean isDisableMending() { return disableMending; }
    public boolean isDisableAllEnchantments() { return disableAllEnchantments; }
    public float getExtraExhaustionPerSecond() { return extraExhaustionPerSecond; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isDisableMilk() { return disableMilk; }
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
}

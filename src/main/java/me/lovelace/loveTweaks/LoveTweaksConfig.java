package me.lovelace.loveTweaks;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class LoveTweaksConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Ender Chests
    private boolean enderChestsAsNormalChests;

    // Enchantments
    private boolean disableMending;
    private boolean disableAllEnchantments; // More general control

    // Achievements
    private boolean disableVanillaAchievements;
    private List<String> customAchievementIds; // Placeholder for custom achievements

    // Hunger
    private float extraExhaustionPerSecond;
    private float saturationMultiplier;

    // Milk
    private boolean disableMilk;

    public LoveTweaksConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig(); // Creates config.yml if it doesn't exist
        config = plugin.getConfig();

        // Ender Chests
        enderChestsAsNormalChests = config.getBoolean("ender-chests.as-normal-chests", false);

        // Enchantments
        disableMending = config.getBoolean("enchantments.disable-mending", false);
        disableAllEnchantments = config.getBoolean("enchantments.disable-all-enchantments", false);

        // Achievements
        disableVanillaAchievements = config.getBoolean("achievements.disable-vanilla-achievements", false);
        customAchievementIds = config.getStringList("achievements.custom-achievement-ids");
        if (customAchievementIds == null) {
            customAchievementIds = Collections.emptyList();
        }

        // Hunger
        extraExhaustionPerSecond = (float) config.getDouble("hunger.extra-exhaustion-per-second", 0.25);
        saturationMultiplier = (float) config.getDouble("hunger.saturation-multiplier", 0.5);

        // Milk
        disableMilk = config.getBoolean("milk.disable-milk", true);

        // Save comments and default values if they were just created
        plugin.saveConfig();
    }

    public boolean isEnderChestsAsNormalChests() {
        return enderChestsAsNormalChests;
    }

    public boolean isDisableMending() {
        return disableMending;
    }

    public boolean isDisableAllEnchantments() {
        return disableAllEnchantments;
    }

    public boolean isDisableVanillaAchievements() {
        return disableVanillaAchievements;
    }

    public List<String> getCustomAchievementIds() {
        return customAchievementIds;
    }

    public float getExtraExhaustionPerSecond() {
        return extraExhaustionPerSecond;
    }

    public float getSaturationMultiplier() {
        return saturationMultiplier;
    }

    public boolean isDisableMilk() {
        return disableMilk;
    }

    // Potentially add methods to set values and save config if needed for in-game commands
}

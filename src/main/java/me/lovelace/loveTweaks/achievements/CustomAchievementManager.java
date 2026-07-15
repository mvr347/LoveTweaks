package me.lovelace.loveTweaks.achievements;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class CustomAchievementManager {

    private final LoveTweaks plugin;
    private final Map<String, CustomAchievement> achievements = new HashMap<>();

    public CustomAchievementManager(LoveTweaks plugin) {
        this.plugin = plugin;
        loadAchievements();
    }

    public void loadAchievements() {
        achievements.clear();
        List<String> achievementIds = plugin.getLoveTweaksConfig().getCustomAchievementIds();

        for (String id : achievementIds) {
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-achievements." + id);
            if (section == null) {
                plugin.getLogger().log(Level.WARNING, "Custom achievement section not found for ID: " + id);
                continue;
            }

            String name = section.getString("name", id);
            String description = section.getString("description", "No description provided.");
            Material icon = Material.matchMaterial(section.getString("icon", "STONE"));
            List<String> criteriaStrings = section.getStringList("criteria");
            List<AchievementCriterion> parsedCriteria = new ArrayList<>();

            if (icon == null) {
                plugin.getLogger().log(Level.WARNING, "Invalid icon material for achievement " + id + ". Using STONE.");
                icon = Material.STONE;
            }

            for (String criterionString : criteriaStrings) {
                AchievementCriterion criterion = parseCriterion(criterionString, id);
                if (criterion != null) {
                    parsedCriteria.add(criterion);
                }
            }

            CustomAchievement achievement = new CustomAchievement(id, name, description, icon, parsedCriteria);
            achievements.put(id, achievement);
            plugin.getLogger().log(Level.INFO, "Loaded custom achievement: " + id);
        }
    }

    private AchievementCriterion parseCriterion(String criterionString, String achievementId) {
        String[] parts = criterionString.split(":");
        if (parts.length < 2) {
            plugin.getLogger().log(Level.WARNING, "Invalid criterion format for achievement " + achievementId + ": " + criterionString);
            return null;
        }

        try {
            AchievementCriterion.CriterionType type = AchievementCriterion.CriterionType.valueOf(parts[0].toUpperCase());
            Material material = null;
            EntityType entityType = null;
            int amount = 1; // Default amount

            switch (type) {
                case BREAK_BLOCK:
                case CRAFT_ITEM:
                    material = Material.matchMaterial(parts[1].toUpperCase());
                    if (material == null) {
                        plugin.getLogger().log(Level.WARNING, "Invalid material for criterion " + criterionString + " in achievement " + achievementId);
                        return null;
                    }
                    if (parts.length > 2) {
                        amount = Integer.parseInt(parts[2]);
                    }
                    break;
                case KILL_ENTITY:
                    entityType = EntityType.valueOf(parts[1].toUpperCase());
                    if (parts.length > 2) {
                        amount = Integer.parseInt(parts[2]);
                    }
                    break;
                // Add more cases for other criterion types
                default:
                    plugin.getLogger().log(Level.WARNING, "Unknown criterion type: " + type + " for achievement " + achievementId);
                    return null;
            }
            return new AchievementCriterion(type, material, entityType, amount);

        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Error parsing criterion '" + criterionString + "' for achievement " + achievementId + ": " + e.getMessage());
            return null;
        }
    }

    public CustomAchievement getAchievement(String id) {
        return achievements.get(id);
    }

    public Map<String, CustomAchievement> getAchievements() {
        return achievements;
    }

    public void grantAchievement(Player player, String achievementId) {
        if (!hasAchievement(player, achievementId)) {
            plugin.getPlayerAchievementData().markAchievementCompleted(player.getUniqueId(), achievementId);
            plugin.getLogger().info("Achievement '" + achievementId + "' granted to " + player.getName());
            CustomAchievement achievement = achievements.get(achievementId);
            if (achievement != null) {
                player.sendMessage("§aПоздравляем! Вы получили достижение: " + achievement.name());
            }
        }
    }

    public boolean hasAchievement(Player player, String achievementId) {
        return plugin.getPlayerAchievementData().hasCompletedAchievement(player.getUniqueId(), achievementId);
    }
}

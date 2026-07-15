package me.lovelace.loveTweaks.achievements.data;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.achievements.AchievementCriterion;
import me.lovelace.loveTweaks.achievements.CustomAchievement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class PlayerAchievementData {

    private final LoveTweaks plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;
    // Map: Player UUID -> Achievement ID -> Criterion String -> Current Progress
    private final Map<UUID, Map<String, Map<String, Integer>>> playerAchievementProgress = new HashMap<>();

    public PlayerAchievementData(LoveTweaks plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "achievements_data.yml");
        loadData();
    }

    public void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create achievements_data.yml", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        playerAchievementProgress.clear();
        for (String uuidString : dataConfig.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(uuidString);
                ConfigurationSection playerSection = dataConfig.getConfigurationSection(uuidString);
                if (playerSection == null) continue;

                Map<String, Map<String, Integer>> achievementsForPlayer = new HashMap<>();
                for (String achievementId : playerSection.getKeys(false)) {
                    ConfigurationSection achievementSection = playerSection.getConfigurationSection(achievementId);
                    if (achievementSection == null) continue;

                    Map<String, Integer> criteriaProgress = new HashMap<>();
                    for (String criterionString : achievementSection.getKeys(false)) {
                        criteriaProgress.put(criterionString, achievementSection.getInt(criterionString));
                    }
                    achievementsForPlayer.put(achievementId, criteriaProgress);
                }
                playerAchievementProgress.put(playerUuid, achievementsForPlayer);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid UUID found in achievements_data.yml: " + uuidString, e);
            }
        }
        plugin.getLogger().info("Loaded player achievement data.");
    }

    public void saveData() {
        dataConfig = new YamlConfiguration(); // Clear existing config to rewrite
        for (Map.Entry<UUID, Map<String, Map<String, Integer>>> playerEntry : playerAchievementProgress.entrySet()) {
            String uuidString = playerEntry.getKey().toString();
            ConfigurationSection playerSection = dataConfig.createSection(uuidString);

            for (Map.Entry<String, Map<String, Integer>> achievementEntry : playerEntry.getValue().entrySet()) {
                String achievementId = achievementEntry.getKey();
                ConfigurationSection achievementSection = playerSection.createSection(achievementId);

                for (Map.Entry<String, Integer> criterionEntry : achievementEntry.getValue().entrySet()) {
                    achievementSection.set(criterionEntry.getKey(), criterionEntry.getValue());
                }
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save achievements_data.yml", e);
        }
        plugin.getLogger().info("Saved player achievement data.");
    }

    /**
     * Returns the progress for a specific criterion of an achievement for a player.
     *
     * @param playerUuid The UUID of the player.
     * @param achievementId The ID of the achievement.
     * @param criterionString The string representation of the criterion.
     * @return The current progress, or 0 if not found.
     */
    public int getCriterionProgress(UUID playerUuid, String achievementId, String criterionString) {
        return playerAchievementProgress
                .getOrDefault(playerUuid, Collections.emptyMap())
                .getOrDefault(achievementId, Collections.emptyMap())
                .getOrDefault(criterionString, 0);
    }

    /**
     * Increments the progress for a specific criterion of an achievement for a player.
     *
     * @param playerUuid The UUID of the player.
     * @param achievementId The ID of the achievement.
     * @param criterionString The string representation of the criterion.
     * @param amount The amount to increment by.
     */
    public void incrementCriterionProgress(UUID playerUuid, String achievementId, String criterionString, int amount) {
        playerAchievementProgress
                .computeIfAbsent(playerUuid, k -> new HashMap<>())
                .computeIfAbsent(achievementId, k -> new HashMap<>())
                .merge(criterionString, amount, Integer::sum);
        // Saving should be handled by a scheduled task or on plugin disable.
    }

    /**
     * Sets the progress for a specific criterion of an achievement for a player.
     *
     * @param playerUuid The UUID of the player.
     * @param achievementId The ID of the achievement.
     * @param criterionString The string representation of the criterion.
     * @param progress The new progress value.
     */
    public void setCriterionProgress(UUID playerUuid, String achievementId, String criterionString, int progress) {
        playerAchievementProgress
                .computeIfAbsent(playerUuid, k -> new HashMap<>())
                .computeIfAbsent(achievementId, k -> new HashMap<>())
                .put(criterionString, progress);
    }

    /**
     * Checks if a player has completed a specific achievement.
     *
     * @param playerUuid The UUID of the player.
     * @param achievementId The ID of the achievement.
     * @return True if the achievement is completed, false otherwise.
     */
    public boolean hasCompletedAchievement(UUID playerUuid, String achievementId) {
        CustomAchievement achievement = plugin.getCustomAchievementManager().getAchievement(achievementId);
        if (achievement == null) {
            return false; // Achievement not found
        }

        Map<String, Integer> playerProgressForAchievement = playerAchievementProgress
                .getOrDefault(playerUuid, Collections.emptyMap())
                .getOrDefault(achievementId, Collections.emptyMap());

        for (AchievementCriterion criterion : achievement.criteria()) {
            String criterionString = criterion.type().name() + ":" +
                                     (criterion.material() != null ? criterion.material().name() : "") +
                                     (criterion.entityType() != null ? criterion.entityType().name() : "");
            // Clean up criterion string for map key, remove trailing colons if material/entityType is null
            criterionString = criterionString.replaceAll(":+", ":").replaceAll(":$", "");

            int currentProgress = playerProgressForAchievement.getOrDefault(criterionString, 0);
            if (currentProgress < criterion.amount()) {
                return false; // Not all criteria met
            }
        }
        return true; // All criteria met
    }

    /**
     * Marks an achievement as completed for a player. This is used when an achievement is granted directly.
     *
     * @param playerUuid The UUID of the player.
     * @param achievementId The ID of the achievement.
     */
    public void markAchievementCompleted(UUID playerUuid, String achievementId) {
        CustomAchievement achievement = plugin.getCustomAchievementManager().getAchievement(achievementId);
        if (achievement == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to mark non-existent achievement as completed: " + achievementId);
            return;
        }

        Map<String, Map<String, Integer>> playerAchievements = playerAchievementProgress.computeIfAbsent(playerUuid, k -> new HashMap<>());
        Map<String, Integer> achievementProgress = playerAchievements.computeIfAbsent(achievementId, k -> new HashMap<>());

        for (AchievementCriterion criterion : achievement.criteria()) {
            String criterionString = criterion.type().name() + ":" +
                                     (criterion.material() != null ? criterion.material().name() : "") +
                                     (criterion.entityType() != null ? criterion.entityType().name() : "");
            criterionString = criterionString.replaceAll(":+", ":").replaceAll(":$", "");
            achievementProgress.put(criterionString, criterion.amount()); // Set progress to required amount
        }
        // Saving should be handled by a scheduled task or on plugin disable.
    }
}

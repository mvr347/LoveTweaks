package me.lovelace.loveTweaks.achievements;

import org.bukkit.Material;

import java.util.List;

public record CustomAchievement(
        String id,
        String name,
        String description,
        Material icon,
        List<AchievementCriterion> criteria
) {
}

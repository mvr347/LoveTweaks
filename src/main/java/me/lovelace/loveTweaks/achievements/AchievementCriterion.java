package me.lovelace.loveTweaks.achievements;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record AchievementCriterion(
        CriterionType type,
        Material material, // For BREAK_BLOCK, CRAFT_ITEM, etc.
        EntityType entityType, // For KILL_ENTITY
        int amount // For criteria requiring a certain count
) {
    public enum CriterionType {
        BREAK_BLOCK,
        CRAFT_ITEM,
        KILL_ENTITY,
        // Add more types as needed
    }
}

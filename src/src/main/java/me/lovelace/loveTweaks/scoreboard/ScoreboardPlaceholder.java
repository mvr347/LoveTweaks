package me.lovelace.loveTweaks.scoreboard;

import java.util.List;

public record ScoreboardPlaceholder(
    String id,
    String displayName,
    String template,
    List<String> lore,
    ScoreboardRequirement requirement,
    int sortGroup
) {
    public boolean isUnlocked(org.bukkit.entity.Player player) {
        return requirement == null || requirement.isMet(player);
    }
}

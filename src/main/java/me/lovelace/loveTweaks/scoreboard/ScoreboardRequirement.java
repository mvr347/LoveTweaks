package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.entity.Player;

/**
 * A config-driven unlock condition for a scoreboard placeholder, evaluated through PAPI.
 * {@code equals} set: the expanded placeholder must match it exactly (case-insensitive).
 * {@code equals} unset: the placeholder just has to expand to a non-empty, resolved value.
 */
public record ScoreboardRequirement(String placeholder, String equals, String reason) {

    public boolean isMet(Player player) {
        if (placeholder == null || placeholder.isBlank()) return true;

        String expanded = PlaceholderIntegration.resolve(player, placeholder);
        if (equals != null && !equals.isBlank()) {
            return expanded.equalsIgnoreCase(equals);
        }
        return !expanded.isBlank() && !expanded.equals(placeholder);
    }
}

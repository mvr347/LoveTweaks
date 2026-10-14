package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.entity.Player;

/** Why (and whether) a scoreboard line can be used by a player right now. */
public final class ScoreboardAvailability {

    public enum Status { AVAILABLE, MISSING_PLUGIN, REQUIREMENT }

    private ScoreboardAvailability() {}

    public static Status check(Player player, ScoreboardPlaceholder placeholder) {
        // The plugin check goes first: a requirement on a missing expansion could never be met anyway.
        if (!PlaceholderIntegration.isInstalled(placeholder)) return Status.MISSING_PLUGIN;
        if (!placeholder.isUnlocked(player)) return Status.REQUIREMENT;
        return Status.AVAILABLE;
    }

    public static boolean isAvailable(Player player, ScoreboardPlaceholder placeholder) {
        return check(player, placeholder) == Status.AVAILABLE;
    }

    /** Reason line for a locked item (legacy color codes), or empty. */
    public static String reason(Status status, ScoreboardPlaceholder placeholder, ScoreboardConfig config) {
        return switch (status) {
            case AVAILABLE -> "";
            case MISSING_PLUGIN -> config.messages().get("reason-plugin");
            case REQUIREMENT -> placeholder.requirement() != null ? placeholder.requirement().reason() : "";
        };
    }
}

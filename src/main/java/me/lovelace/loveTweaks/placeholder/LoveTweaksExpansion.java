package me.lovelace.loveTweaks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Own PlaceholderAPI expansion {@code %lovetweaks_*%} for player statistics and current location.
 *
 * <p>It replaces {@code %kd_ratio%} (no such expansion exists on the server, so the line stayed raw)
 * and the dependency on the third-party Statistic/Player expansions. Every value is read from the
 * vanilla statistics / the online player directly, so the call is cheap and safe on the main thread.
 */
public final class LoveTweaksExpansion extends PlaceholderExpansion {

    private final LoveTweaks plugin;

    public LoveTweaksExpansion(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lovetweaks";
    }

    @Override
    public @NotNull String getAuthor() {
        var authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "Lovelace" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Lives together with the plugin and survives /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;

        Player online = player.getPlayer();
        return switch (params.toLowerCase()) {
            case "kills" -> String.valueOf(stat(player, Statistic.PLAYER_KILLS));
            case "deaths" -> String.valueOf(stat(player, Statistic.DEATHS));
            case "mob_kills" -> String.valueOf(stat(player, Statistic.MOB_KILLS));
            case "kd" -> KdRatio.format(stat(player, Statistic.PLAYER_KILLS), stat(player, Statistic.DEATHS));
            case "ping" -> online != null ? String.valueOf(online.getPing()) : "0";
            case "x" -> online != null ? String.valueOf(online.getLocation().getBlockX()) : "0";
            case "y" -> online != null ? String.valueOf(online.getLocation().getBlockY()) : "0";
            case "z" -> online != null ? String.valueOf(online.getLocation().getBlockZ()) : "0";
            case "world" -> online != null ? online.getWorld().getName() : "";
            default -> null;
        };
    }

    private int stat(OfflinePlayer player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // The player has no statistics yet (never joined).
            return 0;
        }
    }
}

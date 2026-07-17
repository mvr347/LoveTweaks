package me.lovelace.loveTweaks.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaceholderIntegration {

    public String apply(Player player, String text) {
        return resolve(player, text);
    }

    /**
     * Expands a raw PAPI expression (e.g. "%clans_in_clan%"). Returns the input unchanged
     * if PlaceholderAPI is not present or the expansion fails, so callers can detect a
     * no-op expansion by comparing the result to the input.
     */
    public static String resolve(Player player, String text) {
        if (text == null || text.isBlank()) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            return text;
        }
    }

    public static boolean has(Player player, String placeholder) {
        String test = "%" + placeholder + "%";
        String expanded = resolve(player, test);
        return !expanded.equals(test) && !expanded.isBlank();
    }
}

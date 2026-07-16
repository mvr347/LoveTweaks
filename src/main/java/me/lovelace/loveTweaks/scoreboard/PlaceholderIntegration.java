package me.lovelace.loveTweaks.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PlaceholderIntegration {
    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public static boolean has(Player player, String placeholder) {
        try {
            String test = "%" + placeholder + "%";
            String expanded = PlaceholderAPI.setPlaceholders(player, test);
            return !expanded.equals(test);
        } catch (Exception e) {
            return false;
        }
    }
}

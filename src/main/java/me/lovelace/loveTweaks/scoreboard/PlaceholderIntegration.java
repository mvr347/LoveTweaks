package me.lovelace.loveTweaks.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

public class PlaceholderIntegration {
    public String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

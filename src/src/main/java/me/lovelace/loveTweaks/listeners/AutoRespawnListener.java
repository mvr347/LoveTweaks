package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AutoRespawnListener implements Listener {

    private final LoveTweaks plugin;

    public AutoRespawnListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getLoveTweaksConfig().isAutoRespawnEnabled()) {
            return;
        }

        Player player = event.getEntity();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getLoveTweaksConfig().isAutoRespawnEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isDead()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            }, 1L);
        }
    }
}

package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles death drops, inventory interactions while downed, and quit cleanup.
 * {@link #onPlayerDeath(PlayerDeathEvent)} is registered dynamically by {@link ReviveMeInventoryModule}
 * to honor the configured {@code death-event-priority}.
 */
final class ReviveMeInventoryListener implements Listener {

    private final ReviveMeInventoryModule module;
    private final DownedInventoryManager inventoryManager;
    private final ReviveMeInventoryFixConfig config;
    private final Logger logger;

    ReviveMeInventoryListener(ReviveMeInventoryModule module, DownedInventoryManager inventoryManager,
                              ReviveMeInventoryFixConfig config, Logger logger) {
        this.module = module;
        this.inventoryManager = inventoryManager;
        this.config = config;
        this.logger = logger;
    }

    void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        module.handleDownedDeath(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Player downedPlayer) {
            UUID uuid = downedPlayer.getUniqueId();
            if (inventoryManager.contains(uuid)) {
                inventoryManager.update(downedPlayer);
                if (config.isDebug()) {
                    logger.log(Level.INFO, "[ReviveMeInventoryFix] Updated snapshot for " + downedPlayer.getName()
                            + " after inventory close");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Player downedPlayer) {
            UUID uuid = downedPlayer.getUniqueId();
            if (inventoryManager.contains(uuid)) {
                // If items are modified (e.g. theft), schedule an update for end of tick
                downedPlayer.getServer().getScheduler().runTask(module.getPlugin(), () -> {
                    if (downedPlayer.isOnline() && inventoryManager.contains(uuid)) {
                        inventoryManager.update(downedPlayer);
                    }
                });
            }
        }
    }

    @EventHandler
    void onPlayerQuit(PlayerQuitEvent event) {
        if (!config.isClearSnapshotOnQuit()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (inventoryManager.contains(uuid)) {
            inventoryManager.remove(uuid);
            if (config.isDebug()) {
                logger.log(Level.INFO, "[ReviveMeInventoryFix] Cleared Downed snapshot on quit for "
                        + event.getPlayer().getName());
            }
        }
    }
}

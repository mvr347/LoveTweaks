package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reacts to a Downed player's final death by consuming their saved snapshot and dropping it -
 * see {@link DeathDropHandler}. Registered manually (not via {@code @EventHandler}) so the
 * configured {@code death-event-priority} can be honored at runtime; see
 * {@link ReviveMeInventoryModule}.
 */
final class ReviveMeInventoryListener implements Listener {

    private final DownedInventoryManager inventoryManager;
    private final ReviveMeInventoryFixConfig config;
    private final Logger logger;

    ReviveMeInventoryListener(DownedInventoryManager inventoryManager, ReviveMeInventoryFixConfig config, Logger logger) {
        this.inventoryManager = inventoryManager;
        this.config = config;
        this.logger = logger;
    }

    void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        // take(), not get(): a second death event for the same player (shouldn't normally
        // happen, but other plugins can re-fire or re-process death) must find nothing left to
        // drop instead of duplicating the first drop.
        InventorySnapshot snapshot = inventoryManager.take(uuid);
        if (snapshot == null) {
            return;
        }

        event.setKeepInventory(false);
        event.getDrops().clear();
        DeathDropHandler.apply(event, snapshot);

        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] Dropped saved Downed inventory for " + player.getName());
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
                logger.log(Level.INFO, "[ReviveMeInventoryFix] Cleared Downed snapshot on quit for " + event.getPlayer().getName());
            }
        }
    }
}

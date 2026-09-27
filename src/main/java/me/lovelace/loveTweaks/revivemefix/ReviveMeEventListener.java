package me.lovelace.loveTweaks.revivemefix;

import net.kokoricraft.reviveme.events.DownedDeathEvent;
import net.kokoricraft.reviveme.events.DownedSuicideEvent;
import net.kokoricraft.reviveme.events.PlayerDownedEvent;
import net.kokoricraft.reviveme.events.PlayerDropDownedEvent;
import net.kokoricraft.reviveme.events.PlayerPickupDownedEvent;
import net.kokoricraft.reviveme.events.PlayerRelivingEvent;
import net.kokoricraft.reviveme.events.PlayerReviveEvent;
import net.kokoricraft.reviveme.events.PlayerStartRelivingEvent;
import net.kokoricraft.reviveme.events.PlayerStopRelivingEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Direct compile-time listener for ReviveMe events from ReviveMe-API.
 * Only registered when ReviveMe is verified active on the server.
 */
public final class ReviveMeEventListener implements Listener {

    private final ReviveMeInventoryModule module;
    private final DownedInventoryManager inventoryManager;
    private final ReviveMeInventoryFixConfig config;
    private final Logger logger;

    public ReviveMeEventListener(ReviveMeInventoryModule module, DownedInventoryManager inventoryManager,
                                 ReviveMeInventoryFixConfig config, Logger logger) {
        this.module = module;
        this.inventoryManager = inventoryManager;
        this.config = config;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDowned(PlayerDownedEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        inventoryManager.save(player);
        if (config.isDebug()) {
            String enemy = event.getEnemy() != null ? event.getEnemy().getName() : "none";
            String cause = event.getCause() != null ? event.getCause().name() : "unknown";
            logger.log(Level.INFO, "[ReviveMeInventoryFix] Snapshot captured on PlayerDownedEvent for "
                    + player.getName() + " (Enemy: " + enemy + ", Cause: " + cause + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRevive(PlayerReviveEvent event) {
        Player player = event.getPlayer();
        if (player == null || !config.isRemoveOnRevive()) {
            return;
        }
        if (inventoryManager.contains(player.getUniqueId())) {
            inventoryManager.remove(player.getUniqueId());
            if (config.isDebug()) {
                String reviver = event.getReviver() != null ? event.getReviver().getName() : "self/timer";
                logger.log(Level.INFO, "[ReviveMeInventoryFix] Snapshot removed on PlayerReviveEvent for "
                        + player.getName() + " (Revived by: " + reviver + ")");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDownedDeath(DownedDeathEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] DownedDeathEvent received for " + player.getName());
        }
        module.handleDownedDeath(player, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDownedSuicide(DownedSuicideEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] DownedSuicideEvent for " + player.getName());
        }
        // If snapshot wasn't taken earlier for any reason, take or refresh it now before death fires
        inventoryManager.save(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupDowned(PlayerPickupDownedEvent event) {
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] PlayerPickupDownedEvent triggered.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropDowned(PlayerDropDownedEvent event) {
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] PlayerDropDownedEvent triggered.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerStartReliving(PlayerStartRelivingEvent event) {
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] PlayerStartRelivingEvent triggered.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRelivingDowned(PlayerRelivingEvent event) {
        // Reliving in progress (tick/interval event)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerStopReliving(PlayerStopRelivingEvent event) {
        if (config.isDebug()) {
            logger.log(Level.INFO, "[ReviveMeInventoryFix] PlayerStopRelivingEvent triggered.");
        }
    }
}

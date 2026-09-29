package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Lifecycle manager for the ReviveMe inventory drop fix module.
 * <p>
 * Ensures that items reliably drop when a downed player dies (fixing the issue where items vanish
 * into the void with {@code downed_keep_inventory: false}, or when external plugins clear drops/enforce keepInventory).
 */
public final class ReviveMeInventoryModule {

    private final JavaPlugin plugin;
    private final ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();

    private DownedInventoryManager inventoryManager;
    private ReviveMeInventoryListener inventoryListener;
    private ReviveMeEventListener reviveMeEventListener;
    private DownedQuitGuard quitGuard;
    private ReflectiveReviveMeDetector detector;
    private BukkitTask cleanupTask;

    public ReviveMeInventoryModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public DownedInventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public ReviveMeInventoryFixConfig getConfig() {
        return config;
    }

    public void enable(ConfigurationSection configSection) {
        config.load(configSection);
        if (!config.isEnabled()) {
            return;
        }

        inventoryManager = new DownedInventoryManager();
        detector = new ReflectiveReviveMeDetector(plugin, config.isDebug());

        Plugin reviveMePlugin = Bukkit.getPluginManager().getPlugin("ReviveMe");
        boolean reviveMeActive = reviveMePlugin != null && reviveMePlugin.isEnabled();

        if (config.isRequireReviveMe() && !reviveMeActive) {
            plugin.getLogger().warning("[ReviveMeInventoryFix] ReviveMe plugin was not found or is disabled! "
                    + "Module will remain dormant (require-reviveme: true in config).");
            return;
        }

        // Register inventory listener (handles death event, inventory interactions, quit)
        inventoryListener = new ReviveMeInventoryListener(this, inventoryManager, config, plugin.getLogger());
        Bukkit.getPluginManager().registerEvents(inventoryListener, plugin);

        EventExecutor deathExecutor = (l, event) -> {
            if (event instanceof PlayerDeathEvent deathEvent) {
                inventoryListener.onPlayerDeath(deathEvent);
            }
        };
        Bukkit.getPluginManager().registerEvent(PlayerDeathEvent.class, inventoryListener,
                config.getDeathEventPriority(), deathExecutor, plugin, false);

        // Register official ReviveMe API event listener if ReviveMe is loaded
        if (reviveMeActive) {
            try {
                reviveMeEventListener = new ReviveMeEventListener(this, inventoryManager, config, plugin.getLogger());
                Bukkit.getPluginManager().registerEvents(reviveMeEventListener, plugin);
                if (config.isDebug()) {
                    plugin.getLogger().info("[ReviveMeInventoryFix] Registered ReviveMeEventListener successfully.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[ReviveMeInventoryFix] Could not register ReviveMeEventListener: "
                        + t.getMessage(), t);
            }
        }

        // Needs ReviveMeAPI#hasDowned, so it only makes sense with ReviveMe actually running
        if (reviveMeActive && config.isQuitGuardEnabled()) {
            quitGuard = new DownedQuitGuard(plugin, config);
            Bukkit.getPluginManager().registerEvents(quitGuard, plugin);
        }

        startCleanupTask();
        plugin.getLogger().info("[ReviveMeInventoryFix] Module enabled (DropMode: " + config.getDropMode()
                + ", Priority: " + config.getDeathEventPriority() + ")");
    }

    public void reload(ConfigurationSection configSection) {
        disable();
        enable(configSection);
    }

    public void disable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (reviveMeEventListener != null) {
            HandlerList.unregisterAll(reviveMeEventListener);
            reviveMeEventListener = null;
        }
        if (inventoryListener != null) {
            HandlerList.unregisterAll(inventoryListener);
            inventoryListener = null;
        }
        if (quitGuard != null) {
            HandlerList.unregisterAll(quitGuard);
            quitGuard.clear();
            quitGuard = null;
        }
        if (detector != null) {
            detector.shutdown();
            detector = null;
        }
        if (inventoryManager != null) {
            inventoryManager.clear();
            inventoryManager = null;
        }
    }

    /**
     * Atomically executes the death drop for a player who died while downed.
     * Can be invoked from either {@link net.kokoricraft.reviveme.events.DownedDeathEvent} or {@link PlayerDeathEvent}.
     */
    public void handleDownedDeath(Player player, PlayerDeathEvent deathEvent) {
        if (player == null || inventoryManager == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        // take() is atomic: removes the snapshot immediately so only one execution drops items
        InventorySnapshot snapshot = inventoryManager.take(uuid);

        // If snapshot wasn't present (e.g. reload while downed), fallback to current player inventory if downed
        if (snapshot == null && config.isForceDropOnDownedDeath() && ReviveMeApiBridge.hasDowned(player)) {
            snapshot = InventorySnapshot.capture(player);
            if (config.isDebug()) {
                plugin.getLogger().info("[ReviveMeInventoryFix] Captured fallback snapshot at death for downed player "
                        + player.getName());
            }
        }

        if (snapshot == null) {
            return;
        }

        DeathDropHandler.dropSnapshot(player, player.getLocation(), snapshot, deathEvent,
                config.getDropMode(), config.isClearInventoryOnDeath(), config.isDropExp());

        if (config.isDebug()) {
            plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Successfully dropped inventory for "
                    + player.getName() + " using mode " + config.getDropMode());
        }
    }

    public void onPlayerDowned(Player player) {
        if (inventoryManager == null || player == null) {
            return;
        }
        inventoryManager.save(player);
        if (config.isDebug()) {
            plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Snapshot created for " + player.getName());
        }
    }

    public void onPlayerRevived(Player player) {
        if (inventoryManager == null || player == null || !config.isRemoveOnRevive()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (inventoryManager.contains(uuid)) {
            inventoryManager.remove(uuid);
            if (config.isDebug()) {
                plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Snapshot removed after revive for "
                        + player.getName());
            }
        }
    }

    private void startCleanupTask() {
        long intervalTicks = 20L * 60L; // once a minute
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (inventoryManager == null) {
                return;
            }
            inventoryManager.cleanupExpired(config.getSnapshotTimeoutSeconds(), uuid -> {
                if (config.isDebug()) {
                    plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Snapshot expired: " + uuid);
                }
            });
        }, intervalTicks, intervalTicks);
    }
}

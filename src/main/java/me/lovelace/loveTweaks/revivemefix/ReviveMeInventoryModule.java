package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Lifecycle owner for the ReviveMe inventory-drop fix (see module spec: without it,
 * {@code downed_keep_inventory: false} makes a Downed player's items vanish on final death
 * instead of dropping, because ReviveMe can already have cleared/replaced the inventory by the
 * time {@link PlayerDeathEvent} fires).
 * <p>
 * The fix: take an independent snapshot the moment ReviveMe reports the player Downed (not on
 * death), then on final death consume that snapshot and drop it ourselves - see
 * {@link DownedInventoryManager} for why {@code take()} rather than {@code get()} matters, and
 * {@link ReflectiveReviveMeDetector} for how ReviveMe's events are found without a compile-time
 * dependency on a specific ReviveMe build.
 */
public final class ReviveMeInventoryModule {

    private final JavaPlugin plugin;
    private final ReviveMeInventoryFixConfig config = new ReviveMeInventoryFixConfig();

    private DownedInventoryManager inventoryManager;
    private ReviveMeInventoryListener listener;
    private ReflectiveReviveMeDetector detector;
    private BukkitTask cleanupTask;

    public ReviveMeInventoryModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable(ConfigurationSection configSection) {
        config.load(configSection);
        if (!config.isEnabled()) {
            return;
        }

        inventoryManager = new DownedInventoryManager();
        listener = new ReviveMeInventoryListener(inventoryManager, config, plugin.getLogger());

        // Only PlayerQuitEvent has an @EventHandler annotation on the listener - PlayerDeathEvent
        // is registered separately below so its priority can come from config instead of being
        // hardcoded on the annotation.
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        EventExecutor deathExecutor = (l, event) -> {
            if (event instanceof PlayerDeathEvent deathEvent) {
                listener.onPlayerDeath(deathEvent);
            }
        };
        Bukkit.getPluginManager().registerEvent(PlayerDeathEvent.class, listener,
                config.getDeathEventPriority(), deathExecutor, plugin, false);

        detector = new ReflectiveReviveMeDetector(plugin, config.isDebug(),
                this::onPlayerDowned,
                this::onPlayerRevived);

        if (config.isRequireReviveMe() && !detector.isAvailable()) {
            plugin.getLogger().warning("[ReviveMeInventoryFix] ReviveMe hook unavailable - the module "
                    + "is registered but will never trigger (require-reviveme: true in config.yml).");
        }

        startCleanupTask();
    }

    /**
     * Full teardown + rebuild against a freshly-loaded config section. A player who is Downed at
     * the exact moment of a {@code /lovetweaksadmin reload} loses their pending snapshot (same
     * trade-off {@code reloadAll()} already makes elsewhere, e.g. cancelling teleport-scroll
     * tasks) - acceptable since reload during that narrow a window is a rare coincidence, and the
     * alternative (partial reload that can't safely change death-event-priority without
     * re-registering) is meaningfully more complex for a corner case this narrow.
     */
    public void reload(ConfigurationSection configSection) {
        disable();
        enable(configSection);
    }

    public void disable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
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

    private void onPlayerDowned(Player player) {
        if (inventoryManager == null) {
            return;
        }
        inventoryManager.save(player);
        if (config.isDebug()) {
            plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Snapshot created for " + player.getName());
        }
    }

    private void onPlayerRevived(Player player) {
        if (inventoryManager == null || !config.isRemoveOnRevive()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (inventoryManager.contains(uuid)) {
            inventoryManager.remove(uuid);
            if (config.isDebug()) {
                plugin.getLogger().log(Level.INFO, "[ReviveMeInventoryFix] Snapshot removed after revive for " + player.getName());
            }
        }
    }

    private void startCleanupTask() {
        long intervalTicks = 20L * 60L; // once a minute - snapshot count is tiny, no need for finer granularity
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

package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Keeps ReviveMe's "downed" state from outliving the session of a player who disconnects while downed.
 * <p>
 * While downed, ReviveMe makes the player untouchable (invulnerable), and vanilla mob targeting skips
 * invulnerable players. The {@code Invulnerable} flag is written into the player data, so a player who
 * quits while downed and is not properly cleaned up by ReviveMe comes back alive, immortal and ignored
 * by hostile mobs. The cleanup therefore has to happen at quit time, before the player data is saved:
 * <ol>
 *   <li>{@code LOWEST} quit handler: remember that the player was downed (ReviveMe's own quit handling may
 *       drop its record before we look);</li>
 *   <li>{@code MONITOR} quit handler: drop the invulnerable flag, optionally kill the player (the same
 *       outcome ReviveMe's disconnect kill is meant to give, which also releases the inventory snapshot
 *       through the normal downed-death path) and leave a marker in the player data;</li>
 *   <li>join handler: if the marker is present, clear the flag once more. It runs at {@code LOWEST} so it
 *       is done before LoveAuth's limbo freezes the player and snapshots that flag for later restore.</li>
 * </ol>
 */
final class DownedQuitGuard implements Listener {

    private final JavaPlugin plugin;
    private final ReviveMeInventoryFixConfig config;
    private final NamespacedKey markerKey;
    private final Set<UUID> downedAtQuit = ConcurrentHashMap.newKeySet();

    DownedQuitGuard(JavaPlugin plugin, ReviveMeInventoryFixConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.markerKey = new NamespacedKey(plugin, "quit_while_downed");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onQuitEarly(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (ReviveMeApiBridge.hasDowned(player)) {
            downedAtQuit.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onQuitLate(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!downedAtQuit.remove(player.getUniqueId())) {
            return;
        }
        try {
            // Order matters: clear the flag first, so it is not persisted even if the kill below throws.
            player.setInvulnerable(false);
            player.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            if (config.isKillOnQuitWhileDowned() && !player.isDead()) {
                player.setHealth(0.0);
            }
            if (config.isDebug()) {
                plugin.getLogger().info("[ReviveMeInventoryFix] Player " + player.getName()
                        + " left while downed: invulnerability cleared, kill="
                        + (config.isKillOnQuitWhileDowned()));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[ReviveMeInventoryFix] Could not clean up downed state of "
                    + player.getName() + " on quit: " + t.getMessage(), t);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!pdc.has(markerKey, PersistentDataType.BYTE)) {
            return;
        }
        pdc.remove(markerKey);
        // A player who is genuinely downed again (ReviveMe restored it) must keep its protection.
        if (!player.isDead() && !ReviveMeApiBridge.hasDowned(player) && player.isInvulnerable()) {
            player.setInvulnerable(false);
            if (config.isDebug()) {
                plugin.getLogger().info("[ReviveMeInventoryFix] Cleared leftover invulnerability of "
                        + player.getName() + " on join");
            }
        }
    }

    void clear() {
        downedAtQuit.clear();
    }
}

package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Resilient detector and hook for ReviveMe.
 * Checks for ReviveMe plugin presence and delegates to {@link ReviveMeApiBridge}.
 */
final class ReflectiveReviveMeDetector implements ReviveMeDetector {

    private final JavaPlugin ownerPlugin;
    private final boolean debug;
    private boolean available;

    ReflectiveReviveMeDetector(JavaPlugin ownerPlugin, boolean debug) {
        this.ownerPlugin = ownerPlugin;
        this.debug = debug;
        init();
    }

    private void init() {
        Plugin reviveMe = Bukkit.getPluginManager().getPlugin("ReviveMe");
        if (reviveMe == null || !reviveMe.isEnabled()) {
            available = false;
            if (debug) {
                ownerPlugin.getLogger().info("[ReviveMeInventoryFix] ReviveMe plugin is not installed or enabled.");
            }
            return;
        }

        ReviveMeApiBridge.init(reviveMe);
        available = true;
        if (debug) {
            ownerPlugin.getLogger().info("[ReviveMeInventoryFix] Successfully connected to ReviveMe plugin.");
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isDowned(Player player) {
        if (!available || player == null) {
            return false;
        }
        return ReviveMeApiBridge.hasDowned(player);
    }

    void shutdown() {
        available = false;
    }
}

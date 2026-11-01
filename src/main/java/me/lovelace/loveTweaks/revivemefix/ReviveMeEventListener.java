package me.lovelace.loveTweaks.revivemefix;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Optional compatibility bridge; building or running LoveTweaks does not require ReviveMe. */
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

    void register(Plugin owner, Plugin upstream) throws ReflectiveOperationException {
        for (String name : new String[]{"PlayerDownedEvent", "PlayerReviveEvent", "DownedDeathEvent", "DownedSuicideEvent"}) {
            Class<? extends Event> type = Class.forName("net.kokoricraft.reviveme.events." + name,
                    false, upstream.getClass().getClassLoader()).asSubclass(Event.class);
            Method getPlayer = type.getMethod("getPlayer");
            if (!Player.class.isAssignableFrom(getPlayer.getReturnType()))
                throw new NoSuchMethodException(type.getName() + ".getPlayer() does not return Player");
            boolean death = name.equals("DownedDeathEvent");
            Bukkit.getPluginManager().registerEvent(type, this,
                    death ? EventPriority.HIGHEST : EventPriority.MONITOR, (listener, event) -> {
                        if (!type.isInstance(event)) return;
                        try {
                            Player player = (Player) getPlayer.invoke(event);
                            if (player == null) return;
                            switch (name) {
                                case "PlayerDownedEvent", "DownedSuicideEvent" -> inventoryManager.save(player);
                                case "PlayerReviveEvent" -> {
                                    if (config.isRemoveOnRevive()) inventoryManager.remove(player.getUniqueId());
                                }
                                case "DownedDeathEvent" -> module.handleDownedDeath(player, null);
                                default -> { }
                            }
                            if (config.isDebug()) logger.info("[ReviveMeInventoryFix] " + name + ": " + player.getName());
                        } catch (ReflectiveOperationException error) {
                            throw new org.bukkit.event.EventException(error);
                        }
                    }, owner, !death);
        }
    }
}

package me.lovelace.loveTweaks.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Обрабатывает использование координатного свитка телепортации.
 */
public class CoordinateTeleportScrollListener implements Listener {

    private final LoveTweaks plugin;
    private final CoordinateTeleportScrollManager manager;

    public CoordinateTeleportScrollListener(LoveTweaks plugin, CoordinateTeleportScrollManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * ПКМ со свитком в руке — запускаем ввод координат.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { /* обрабатываем ниже */ }
            default -> { return; }
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!CoordinateTeleportScroll.isScroll(item)) {
            return;
        }

        event.setCancelled(true);
        manager.startInputPhase(player);
    }

    /**
     * Перехватываем ввод координат в чат.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!manager.isWaitingForInput(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        plugin.getServer().getScheduler().runTask(plugin, () ->
                manager.handleChatInput(player, message)
        );
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player.getUniqueId())) {
            return;
        }
        if (CoordinateTeleportScroll.isScroll(event.getItemDrop().getItemStack())) {
            manager.cancelSession(player.getUniqueId(), "reason-dropped");
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player.getUniqueId())) {
            return;
        }
        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
        if (CoordinateTeleportScroll.isScroll(prevItem)) {
            manager.cancelSession(player.getUniqueId(), "reason-dropped");
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player.getUniqueId())) {
            return;
        }
        manager.cancelSession(player.getUniqueId(), "reason-dropped");
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (manager.hasSession(player.getUniqueId())) {
            manager.cancelSession(player.getUniqueId(), null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Player player = event.getPlayer();
            if (manager.hasSession(player.getUniqueId())) {
                // See TeleportScrollListener#onPlayerTeleport / #isNegligibleTeleport - the
                // server's own "moved wrongly"/"moved too quickly" position correction fires a
                // non-PLUGIN teleport even while the player is standing still, and was wrongly
                // killing the countdown outright. Only a real displacement should cancel it.
                if (isNegligibleTeleport(event.getFrom(), event.getTo())) {
                    return;
                }
                manager.cancelSession(player.getUniqueId(), "reason-teleported");
            }
        }
    }

    private static boolean isNegligibleTeleport(org.bukkit.Location from, org.bukkit.Location to) {
        if (from == null || to == null) {
            return false;
        }
        org.bukkit.World fromWorld = from.getWorld();
        org.bukkit.World toWorld = to.getWorld();
        if (fromWorld == null || toWorld == null || !fromWorld.equals(toWorld)) {
            return false;
        }
        return from.distanceSquared(to) < 1.0;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer().getUniqueId());
    }
}

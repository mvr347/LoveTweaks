package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollManager;
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
 * Обрабатывает использование координатного свитка телепортации. Устроен по образцу
 * {@link TeleportScrollListener}: ПКМ запускает отсчёт, смена предмета в руке/выброс
 * отменяют его, выход с сервера подчищает сессию.
 */
public class CoordinateTeleportScrollListener implements Listener {

    private final CoordinateTeleportScrollManager manager;

    public CoordinateTeleportScrollListener(LoveTweaks plugin, CoordinateTeleportScrollManager manager) {
        this.manager = manager;
    }

    /**
     * ПКМ с координатным свитком в основной руке — запускаем отсчёт телепортации.
     * Проверяем EquipmentSlot.HAND, чтобы не срабатывало дважды (ПКМ вызывает два события: HAND и OFF_HAND).
     */
    @EventHandler(priority = EventPriority.NORMAL)
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

        String scrollId = CoordinateTeleportScroll.getScrollId(item);
        if (scrollId == null) {
            return;
        }

        event.setCancelled(true);
        manager.startCast(player, scrollId);
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

    /**
     * При выходе игрока с сервера — отменяем его активный отсчёт, чтобы не висели
     * задачи-"призраки" в scheduler'е.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer().getUniqueId());
    }
}

package me.lovelace.loveTweaks.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.TeleportScroll;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class TeleportScrollListener implements Listener {

    private final LoveTweaks plugin;
    private final TeleportScrollManager manager;

    public TeleportScrollListener(LoveTweaks plugin, TeleportScrollManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * ПКМ со свитком в основной руке — запускаем фазу ввода ника.
     * Проверяем EquipmentSlot.HAND, чтобы не срабатывало дважды (ПКМ вызывает два события: HAND и OFF_HAND).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Фильтруем: только ПКМ, только основная рука, только со свитком
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> { /* обрабатываем ниже */ }
            default -> { return; }
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!TeleportScroll.isScroll(item)) {
            return;
        }

        // Отменяем стандартное взаимодействие (чтобы не открывать блок и т.п.)
        event.setCancelled(true);

        manager.startInputPhase(player);
    }

    /**
     * Перехватываем ввод в чат для игроков, ожидающих ввода ника.
     * Приоритет LOWEST — обрабатываем раньше других плагинов, чтобы сообщение не ушло в чат.
     * Вызывается АСИНХРОННО — не трогаем никакой Bukkit world-state здесь,
     * только читаем данные и передаём в manager который сам запустит синхронный task.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!manager.isWaitingForInput(player.getUniqueId())) {
            return;
        }

        // Отменяем публикацию сообщения в чат
        event.setCancelled(true);

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Переходим на главный поток для работы с Bukkit API в менеджере
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
        if (TeleportScroll.isScroll(event.getItemDrop().getItemStack())) {
            var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            manager.cancelSession(player.getUniqueId(),
                    cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-dropped")),
                    cfg.message("cancelled-target"));
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player.getUniqueId())) {
            return;
        }
        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
        if (TeleportScroll.isScroll(prevItem)) {
            var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            manager.cancelSession(player.getUniqueId(),
                    cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-dropped")),
                    cfg.message("cancelled-target"));
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSession(player.getUniqueId())) {
            return;
        }
        var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
        manager.cancelSession(player.getUniqueId(),
                cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-dropped")),
                cfg.message("cancelled-target"));
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (manager.hasSession(player.getUniqueId())) {
            manager.cancelSession(player.getUniqueId(), null, null);
        }
        manager.cancelSessionsTargeting(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Player player = event.getPlayer();
            if (manager.hasSession(player.getUniqueId())) {
                // Не тихая отмена: любой не-PLUGIN телепорт инициатора (в т.ч. серверная
                // коррекция позиции "moved wrongly"/"moved too quickly", которая может
                // сработать даже когда игрок физически не двигался) должен объясняться —
                // иначе отсчёт молча истекает без телепорта и без единого сообщения игроку.
                var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
                manager.cancelSession(player.getUniqueId(),
                        cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-teleported")),
                        cfg.message("cancelled-target"));
            }
        }
    }

    // Обновляем время последнего движения только при смене блочной позиции (не поворот камеры)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            manager.updateMoveTime(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.registerJoin(event.getPlayer().getUniqueId());
    }

    /**
     * При выходе игрока с сервера — отменяем его активную сессию,
     * чтобы не висели задачи-"призраки" в scheduler'е.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.hasSession(player.getUniqueId())) {
            manager.cancelSession(player.getUniqueId(), null, null);
        }
        // Игрок мог быть целью (игрок 2) чужой сессии — отменяем и её, не дожидаясь
        // следующей секундной проверки в отсчёте.
        manager.cancelSessionsTargeting(player.getUniqueId());
        manager.removePlayer(player.getUniqueId());
    }
}

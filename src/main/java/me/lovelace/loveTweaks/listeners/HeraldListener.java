package me.lovelace.loveTweaks.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.herald.HeraldGUI;
import me.lovelace.loveTweaks.herald.HeraldGUIHolder;
import me.lovelace.loveTweaks.herald.HeraldGuiConfig;
import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.herald.HeraldPurchaseGUI;
import me.lovelace.loveTweaks.herald.HeraldPurchaseHolder;
import me.lovelace.loveTweaks.herald.HeraldSlot;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import me.lovelace.loveTweaks.utils.Notify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeraldListener implements Listener {

    /** Pending chat-input session — which slot/duration the player was purchasing when they clicked "write in chat". */
    private record ChatSession(int slotIndex, int durationMinutes) {}

    private final LoveTweaks plugin;
    private final HeraldManager manager;
    private final CitizensIntegration citizens;
    // ConcurrentHashMap обязателен: onPlayerChat читает этот map из асинхронного потока чата
    // (AsyncChatEvent), пока главный поток параллельно пишет в него (put при открытии чат-ввода,
    // remove при обработке ответа/выходе игрока) — обычный HashMap в таких условиях даёт
    // неопределённое поведение, ровно как описано в TeleportScrollManager.sessions.
    private final Map<UUID, ChatSession> pendingChatInput = new ConcurrentHashMap<>();

    public HeraldListener(LoveTweaks plugin, HeraldManager manager, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.manager = manager;
        this.citizens = citizens;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.getLoveTweaksConfig().isHeraldEnabled()) {
            return;
        }
        int boundNpcId = plugin.getLoveTweaksConfig().getHeraldNpcId();
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }
        event.setCancelled(true);
        HeraldGUI.open(event.getPlayer(), manager, plugin);
    }

    /** Гвардируем NPC от урона, чтобы его нельзя было случайно атаковать вместо открытия меню. */
    @EventHandler(ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getLoveTweaksConfig().isHeraldEnabled()) {
            return;
        }
        int boundNpcId = plugin.getLoveTweaksConfig().getHeraldNpcId();
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getEntity());
        if (npcId != null && npcId == boundNpcId) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof HeraldGUIHolder
                || event.getInventory().getHolder() instanceof HeraldPurchaseHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof HeraldGUIHolder) {
            handleOverviewClick(event);
        } else if (event.getInventory().getHolder() instanceof HeraldPurchaseHolder holder) {
            handlePurchaseClick(event, holder);
        }
    }

    private void handleOverviewClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();
        int slot = event.getRawSlot();
        if (slot == HeraldGUI.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        for (int i = 0; i < HeraldGUI.SLOT_POSITIONS.length; i++) {
            if (HeraldGUI.SLOT_POSITIONS[i] != slot || i >= manager.slotCount()) {
                continue;
            }
            HeraldSlot heraldSlot = manager.getSlot(i);
            if (heraldSlot.isActive()) {
                deny(player, colorize(player, heraldGui.message("slot-occupied")));
                return;
            }
            // Every reason a free slot cannot be bought (order lock, 2026-09-26 one-slot-per-player
            // limit, no economy, cannot afford even the cheapest announcement) is evaluated by the
            // manager, the same way the GUI draws the inactive button. purchase() re-checks all of it.
            HeraldManager.Unavailability blocked = manager.unavailability(player, i);
            if (blocked != HeraldManager.Unavailability.NONE) {
                String text = blocked == HeraldManager.Unavailability.OWNS_SLOT
                        ? heraldGui.message("already-owns-slot")
                        : "&c" + HeraldGUI.reasonText(heraldGui, plugin, blocked, i);
                deny(player, colorize(player, text));
                return;
            }
            int initialDuration = plugin.getLoveTweaksConfig().getHeraldMinDurationMinutes();
            HeraldPurchaseHolder purchaseHolder = new HeraldPurchaseHolder(i, initialDuration);
            HeraldPurchaseGUI.open(player, plugin, purchaseHolder);
            return;
        }
    }

    private void handlePurchaseClick(InventoryClickEvent event, HeraldPurchaseHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == HeraldPurchaseGUI.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == HeraldPurchaseGUI.SLOT_BACK) {
            player.closeInventory();
            HeraldGUI.open(player, manager, plugin);
            return;
        }
        if (slot == HeraldPurchaseGUI.SLOT_BOOK) {
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.CHAT) {
                Notify.event(player, colorize(heraldGui.message("source-chat-active")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                return;
            }
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.BOOK && event.isRightClick()) {
                holder.setPendingMessage(null);
                holder.setTextSource(HeraldPurchaseHolder.TextSource.NONE);
                Notify.event(player, colorize(heraldGui.message("text-reset")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                HeraldPurchaseGUI.refresh(player, event.getInventory(), plugin, holder);
                return;
            }
            handleBookDrop(event, player, holder, heraldGui);
            return;
        }
        if (slot == HeraldPurchaseGUI.SLOT_WRITE_CHAT) {
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.BOOK) {
                Notify.event(player, colorize(heraldGui.message("source-book-active")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                return;
            }
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.CHAT && event.isRightClick()) {
                holder.setPendingMessage(null);
                holder.setTextSource(HeraldPurchaseHolder.TextSource.NONE);
                Notify.event(player, colorize(heraldGui.message("text-reset")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                HeraldPurchaseGUI.refresh(player, event.getInventory(), plugin, holder);
                return;
            }
            player.closeInventory();
            pendingChatInput.put(player.getUniqueId(), new ChatSession(holder.slotIndex(), holder.durationMinutes()));
            int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
            player.sendMessage(colorize(heraldGui.message("chat-prompt").replace("<max>", String.valueOf(maxLength))));
            return;
        }
        if (slot == HeraldPurchaseGUI.SLOT_DURATION) {
            cycleDuration(event, holder);
            HeraldPurchaseGUI.refresh(player, event.getInventory(), plugin, holder);
            return;
        }
        if (slot == HeraldPurchaseGUI.SLOT_CONFIRM) {
            handleConfirm(player, holder, heraldGui);
        }
    }

    private void handleBookDrop(InventoryClickEvent event, Player player, HeraldPurchaseHolder holder, HeraldGuiConfig heraldGui) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }
        if (cursor.getType() != Material.WRITTEN_BOOK) {
            Notify.event(player, colorize(heraldGui.message("wrong-book")));
            return;
        }
        String text = extractBookText(cursor);
        if (text.isEmpty()) {
            Notify.event(player, colorize(heraldGui.message("empty-message")));
            return;
        }
        holder.setPendingMessage(text);
        holder.setTextSource(HeraldPurchaseHolder.TextSource.BOOK);
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        } catch (Throwable ignored) {}
        HeraldPurchaseGUI.refresh(player, event.getInventory(), plugin, holder);
    }

    private void cycleDuration(InventoryClickEvent event, HeraldPurchaseHolder holder) {
        var cfg = plugin.getLoveTweaksConfig();
        int min = cfg.getHeraldMinDurationMinutes();
        int max = cfg.getHeraldMaxDurationMinutes();
        int step = cfg.getHeraldDurationStepMinutes();

        int next;
        if (event.isRightClick()) {
            next = holder.durationMinutes() - step;
            if (next < min) next = max;
        } else {
            next = holder.durationMinutes() + step;
            if (next > max) next = min;
        }
        holder.setDurationMinutes(next);
    }

    private void handleConfirm(Player player, HeraldPurchaseHolder holder, HeraldGuiConfig heraldGui) {
        String message = holder.pendingMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        long cost = manager.computeCost(holder.durationMinutes(), message.length());
        var economy = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.economy.LoveEconomy.class);
        if (economy.isEmpty()) {
            deny(player, colorize(player, "&c" + heraldGui.message("reason-no-economy")));
            return;
        }
        if (!economy.get().has(player, cost)) {
            deny(player, colorize(player, heraldGui.message("insufficient-funds").replace("<cost>", heraldGui.formatCost(cost))));
            return;
        }
        applyPurchase(player, holder.slotIndex(), holder.durationMinutes(), message, heraldGui);
    }

    private void applyPurchase(Player player, int slotIndex, int durationMinutes, String message, HeraldGuiConfig heraldGui) {
        HeraldManager.PurchaseResult result = manager.purchase(player, slotIndex, durationMinutes, message);
        int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
        long cost = manager.computeCost(durationMinutes, message.length());

        switch (result) {
            case SUCCESS -> {
                // Money was just charged: critical, always mirrored to chat.
                Notify.important(player, colorize(player, heraldGui.message("published").replace("<minutes>", String.valueOf(durationMinutes))));
                player.closeInventory();
            }
            case SLOT_TAKEN -> {
                Notify.event(player, colorize(player, heraldGui.message("slot-occupied")));
                player.closeInventory();
            }
            case SLOT_LOCKED -> {
                Notify.event(player, colorize(player, heraldGui.message("slot-locked").replace("<prev>", String.valueOf(slotIndex))));
                player.closeInventory();
            }
            case ALREADY_OWNS_SLOT -> {
                Notify.event(player, colorize(player, heraldGui.message("already-owns-slot")));
                player.closeInventory();
            }
            case EMPTY_MESSAGE -> Notify.event(player, colorize(player, heraldGui.message("empty-message")));
            case TOO_LONG -> Notify.event(player, colorize(player, heraldGui.message("too-long")
                    .replace("<max>", String.valueOf(maxLength))
                    .replace("<length>", String.valueOf(message.length()))));
            case INSUFFICIENT_FUNDS -> Notify.event(player, colorize(player, heraldGui.message("insufficient-funds")
                    .replace("<cost>", heraldGui.formatCost(cost))));
            case REJECTED_PROFANITY -> {
                Notify.event(player, colorize(player, heraldGui.message("rejected-profanity")));
                player.closeInventory();
            }
        }
    }

    /** Refusal feedback for a click on an unavailable button: denial sound plus the reason on the action bar. */
    private static void deny(Player player, Component reason) {
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
        Notify.event(player, reason);
    }

    private static String extractBookText(ItemStack book) {
        if (!(book.getItemMeta() instanceof BookMeta meta)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Component page : meta.pages()) {
            String text = PlainTextComponentSerializer.plainText().serialize(page).replace('\n', ' ').trim();
            if (text.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(text);
        }
        return builder.toString().trim();
    }

    private static Component colorize(Player player, String text) {
        return GuiItemUtil.colorize(player, text);
    }

    private static Component colorize(String text) {
        return GuiItemUtil.colorize(text);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        ChatSession session = pendingChatInput.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingChatInput.remove(player.getUniqueId());
            HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

            if (message.equalsIgnoreCase("отмена")) {
                Notify.event(player, colorize(heraldGui.message("chat-cancelled")));
                return;
            }

            HeraldPurchaseHolder holder = new HeraldPurchaseHolder(session.slotIndex(), session.durationMinutes());
            holder.setPendingMessage(message);
            holder.setTextSource(HeraldPurchaseHolder.TextSource.CHAT);
            HeraldPurchaseGUI.open(player, plugin, holder);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingChatInput.remove(event.getPlayer().getUniqueId());
    }
}

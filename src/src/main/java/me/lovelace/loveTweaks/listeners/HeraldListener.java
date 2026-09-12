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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeraldListener implements Listener {

    /** Pending chat-input session — which slot/duration the player was purchasing when they clicked "write in chat". */
    private record ChatSession(int slotIndex, int durationMinutes) {}

    private final LoveTweaks plugin;
    private final HeraldManager manager;
    private final CitizensIntegration citizens;
    private final Map<UUID, ChatSession> pendingChatInput = new java.util.concurrent.ConcurrentHashMap<>();

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
                player.sendMessage(colorize(heraldGui.message("no-free-slots")));
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
                player.sendMessage(colorize(heraldGui.message("source-chat-active")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                return;
            }
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.BOOK && event.isRightClick()) {
                holder.setPendingMessage(null);
                holder.setTextSource(HeraldPurchaseHolder.TextSource.NONE);
                player.sendMessage(colorize(heraldGui.message("text-reset")));
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
                player.sendMessage(colorize(heraldGui.message("source-book-active")));
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } catch (Throwable ignored) {}
                return;
            }
            if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.CHAT && event.isRightClick()) {
                holder.setPendingMessage(null);
                holder.setTextSource(HeraldPurchaseHolder.TextSource.NONE);
                player.sendMessage(colorize(heraldGui.message("text-reset")));
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
            player.sendMessage(colorize(heraldGui.message("wrong-book")));
            return;
        }
        String text = extractBookText(cursor);
        if (text.isEmpty()) {
            player.sendMessage(colorize(heraldGui.message("empty-message")));
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
        if (economy.isPresent() && !economy.get().has(player, cost)) {
            player.sendMessage(colorize(player, heraldGui.message("insufficient-funds").replace("<cost>", heraldGui.formatCost(cost))));
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
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
                player.sendMessage(colorize(player, heraldGui.message("published").replace("<minutes>", String.valueOf(durationMinutes))));
                player.closeInventory();
            }
            case SLOT_TAKEN -> {
                player.sendMessage(colorize(player, heraldGui.message("no-free-slots")));
                player.closeInventory();
            }
            case EMPTY_MESSAGE -> player.sendMessage(colorize(player, heraldGui.message("empty-message")));
            case TOO_LONG -> player.sendMessage(colorize(player, heraldGui.message("too-long")
                    .replace("<max>", String.valueOf(maxLength))
                    .replace("<length>", String.valueOf(message.length()))));
            case INSUFFICIENT_FUNDS -> player.sendMessage(colorize(player, heraldGui.message("insufficient-funds")
                    .replace("<cost>", heraldGui.formatCost(cost))));
            case REJECTED_PROFANITY -> {
                player.sendMessage(colorize(player, heraldGui.message("rejected-profanity")));
                player.closeInventory();
            }
        }
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
                player.sendMessage(colorize(heraldGui.message("chat-cancelled")));
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

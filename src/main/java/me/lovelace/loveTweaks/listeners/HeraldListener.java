package me.lovelace.loveTweaks.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.economy.ItemsAdderEconomyService;
import me.lovelace.loveTweaks.herald.HeraldGUI;
import me.lovelace.loveTweaks.herald.HeraldGUIHolder;
import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class HeraldListener implements Listener {

    private final LoveTweaks plugin;
    private final HeraldManager manager;
    private final CitizensIntegration citizens;
    private final ItemsAdderEconomyService economy;

    public HeraldListener(LoveTweaks plugin, HeraldManager manager, CitizensIntegration citizens,
                           ItemsAdderEconomyService economy) {
        this.plugin = plugin;
        this.manager = manager;
        this.citizens = citizens;
        this.economy = economy;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
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
        HeraldGUI.open(event.getPlayer(), manager, plugin.getLoveTweaksConfig());
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HeraldGUIHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        if (event.getRawSlot() != HeraldGUI.SLOT_BUY) {
            return;
        }
        if (manager.isActive()) {
            return;
        }

        String currencyItem = plugin.getLoveTweaksConfig().getHeraldCurrencyItem();
        long cost = plugin.getLoveTweaksConfig().getHeraldCost();

        if (!economy.hasItem(player, currencyItem, cost)) {
            player.sendMessage("§cУ вас недостаточно монет для покупки голоса Королевства.");
            return;
        }

        economy.withdraw(player, currencyItem, cost);
        player.closeInventory();
        manager.startAnnouncementInput(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.isAwaitingInput(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.handleChatInput(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.cancelInput(event.getPlayer().getUniqueId());
    }
}

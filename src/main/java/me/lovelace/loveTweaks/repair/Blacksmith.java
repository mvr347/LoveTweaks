package me.lovelace.loveTweaks.repair;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import dev.lovelace.lovecore.api.npc.*;
import me.lovelace.loveTweaks.integration.NpcIntegration;
import me.lovelace.loveTweaks.utils.CurrencyFormatter;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Persistent blacksmith service, bound to a LoveEssential NPC. */
public final class Blacksmith implements Listener, AutoCloseable {
    static final int[] SLOTS = java.util.stream.IntStream.range(9, 27).toArray();
    static final int PAY = 30, COLLECT = 32, CLOSE = 35;
    private final JavaPlugin plugin;
    private final NpcIntegration npcs;
    private final Supplier<String> heraldKey;
    private final RepairStore store;
    private final RepairService service;
    private final BukkitTask task;
    private final CurrencyFormatter currency = new CurrencyFormatter();
    private boolean closed;

    public static final class Menu implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        Menu(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
    public Blacksmith(JavaPlugin plugin, NpcIntegration npcs, Supplier<String> heraldKey) throws Exception {
        this(plugin, npcs, heraldKey, new RepairStore(plugin.getDataFolder().toPath().resolve("blacksmith.db")), System::currentTimeMillis);
    }
    Blacksmith(JavaPlugin plugin, NpcIntegration npcs, Supplier<String> heraldKey,
               RepairStore store, LongSupplier clock) throws Exception {
        this.plugin = plugin; this.npcs = npcs; this.heraldKey = heraldKey; this.store = store;
        try { service = new RepairService(store, this::settings, () -> LoveCore.service(LoveEconomy.class), clock, plugin); }
        catch (Exception failure) { store.close(); throw failure; }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu)
                    refresh(player, menu);
            }
        }, 20, 20);
    }
    public RepairSettings settings() { return new RepairSettings(plugin.getConfig()); }
    RepairService service() { return service; }
    private void message(CommandSender player, String key) { player.sendMessage(GuiItemUtil.colorize(settings().text(key))); }
    private void failure(Player player, Exception failure) {
        plugin.getLogger().log(Level.SEVERE, "Не удалось изменить заказ кузнеца " + player.getUniqueId(), failure);
        message(player, "storage-error");
    }
    public void open(Player player) {
        if (closed) return;
        if (!settings().enabled() && service.order(player.getUniqueId()).emptyItems()) { message(player, "disabled"); return; }
        // Close first: InventoryCloseEvent returns any old draft before the new view is rendered.
        player.closeInventory();
        Menu menu = new Menu(player.getUniqueId());
        menu.inventory = Bukkit.createInventory(menu, 36, GuiItemUtil.colorize(player, settings().text("title")));
        refresh(player, menu);
        player.openInventory(menu.inventory);
    }
    private ItemStack button(Player player, String material, String name, String... lore) {
        return GuiItemUtil.buildItem(player, material, name, Arrays.asList(lore));
    }
    private String text(String key, String... replacements) {
        String value = settings().text(key);
        for (int i = 0; i < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i+1]);
        return value;
    }
    private void refresh(Player player, Menu menu) {
        if (!menu.owner.equals(player.getUniqueId())) return;
        Inventory inv = menu.inventory;
        RepairOrder order = service.order(menu.owner);
        var items = order.items();
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack item = items.get(i);
            inv.setItem(SLOTS[i], item != null && service.ready(menu.owner) ? RepairSettings.repaired(item) : item);
        }
        ItemStack filler = button(player, "GRAY_STAINED_GLASS_PANE", " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 27; i < 36; i++) inv.setItem(i, filler);
        inv.setItem(0, button(player, "ANVIL", text("info"), text("hint-place"), text("hint-lock"), text("hint-close")));
        inv.setItem(CLOSE, button(player, "BARRIER", text("close")));
        if (order.paid()) {
            boolean ready = service.ready(menu.owner);
            inv.setItem(PAY, button(player, ready ? "LIME_DYE" : "CLOCK", text(ready ? "ready" : "working"),
                    text("time-left", "<seconds>", String.valueOf(service.secondsLeft(menu.owner)))));
            inv.setItem(COLLECT, button(player, ready ? "CHEST" : "GRAY_DYE", text(ready ? "collect" : "locked")));
            return;
        }
        inv.setItem(COLLECT, button(player, "CHEST", text("return")));
        if (order.emptyItems()) { inv.setItem(PAY, button(player, "GRAY_DYE", text("empty"))); return; }
        if (!settings().enabled()) { inv.setItem(PAY, button(player, "GRAY_DYE", text("disabled"))); return; }
        var economy = service.economy();
        if (economy.isEmpty()) { inv.setItem(PAY, button(player, "GRAY_DYE", text("no-economy"))); return; }
        try {
            var quote = service.quote(menu.owner);
            boolean has = economy.get().has(player, quote.price());
            inv.setItem(PAY, button(player, has ? "LIME_DYE" : "GRAY_DYE", text(has ? "pay" : "no-money"),
                    text("price", "<price>", currency.format(quote.price())),
                    text("duration", "<seconds>", String.valueOf(quote.seconds())), text("hint-lock")));
        } catch (RuntimeException badConfig) {
            inv.setItem(PAY, button(player, "GRAY_DYE", text("invalid-config")));
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void npcClick(NpcClickEvent event) {
        if (!npcs.matches(settings().npcKey(), event.npc())) return;
        event.setCancelled(true);
        if (event.click() == NpcClick.RIGHT) open(event.getPlayer());
    }
    @EventHandler(ignoreCancelled = true)
    public void protectNpc(EntityDamageByEntityEvent event) {
        if (npcs.isBound(settings().npcKey(), event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId())) { event.setCancelled(true); return; }
        boolean top = event.getRawSlot() >= 0 && event.getRawSlot() < menu.inventory.getSize();
        // Double click can collect the displayed copies even when the click began in the player inventory.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) { event.setCancelled(true); return; }
        if (!top && !event.isShiftClick()) return;
        event.setCancelled(true);
        try {
            RepairOrder order = service.order(menu.owner);
            if (top && event.getRawSlot() == CLOSE) { player.closeInventory(); return; }
            if (top && event.getRawSlot() == PAY) {
                if (event.getClick() != ClickType.LEFT) return;
                switch (service.pay(player)) {
                    case STARTED -> message(player, "started");
                    case NO_MONEY -> message(player, "no-money");
                    case NO_ECONOMY -> message(player, "no-economy");
                    case DISABLED -> message(player, "disabled");
                    default -> { }
                }
            } else if (top && event.getRawSlot() == COLLECT) {
                if (event.getClick() != ClickType.LEFT) return;
                collect(player);
            } else if (!order.paid()) {
                if (!top && event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
                    ItemStack item = event.getClickedInventory().getItem(event.getSlot());
                    if (!RepairSettings.accepts(item)) { message(player, "invalid-item"); return; }
                    var items = order.items();
                    for (int i = 0; i < items.size(); i++) if (items.get(i) == null) {
                        service.swap(menu.owner, i, item);
                        event.getClickedInventory().setItem(event.getSlot(), null); break;
                    }
                } else if (top && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                    int slot = index(event.getRawSlot());
                    if (slot >= 0) {
                        ItemStack cursor = event.getCursor();
                        if (cursor != null && !cursor.getType().isAir() && !RepairSettings.accepts(cursor)) { message(player, "invalid-item"); return; }
                        player.setItemOnCursor(service.swap(menu.owner, slot, cursor));
                    }
                } else if (top && event.isShiftClick() && index(event.getRawSlot()) >= 0) {
                    // Return the draft as a batch; no item is thrown on the ground if the inventory is full.
                    collect(player);
                }
            }
        } catch (IllegalArgumentException invalid) { message(player, "invalid-config"); }
        catch (Exception failure) { failure(player, failure); }
        refresh(player, menu);
    }
    private static int index(int rawSlot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == rawSlot) return i;
        return -1;
    }
    private void collect(Player player) throws Exception {
        RepairOrder order = service.order(player.getUniqueId());
        if (order.paid() && !service.ready(player.getUniqueId())) { message(player, "locked"); return; }
        service.collect(player);
        if (!service.order(player.getUniqueId()).emptyItems()) message(player, "inventory-full");
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu
                && event.getRawSlots().stream().anyMatch(s -> s < event.getView().getTopInventory().getSize())) event.setCancelled(true);
    }
    @EventHandler public void inventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu) || !(event.getPlayer() instanceof Player player)
                || !menu.owner.equals(player.getUniqueId())) return;
        // Paid orders remain with the blacksmith, even after the timer expires, until explicitly collected.
        if (!service.order(menu.owner).paid()) try { collect(player); } catch (Exception failure) { failure(player, failure); }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!service.order(id).emptyItems()) Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) message(event.getPlayer(), service.ready(id) ? "join-ready" : "join-pending");
        });
    }

    public void command(CommandSender sender, String[] args) {
        if (args.length < 2) { message(sender, "usage"); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("unbind")) {
            plugin.getConfig().set("blacksmith.npc-key", ""); plugin.saveConfig(); message(sender, "unbound"); return;
        }
        if (!(sender instanceof Player player)) { message(sender, "players-only"); return; }
        if (action.equals("open")) { open(player); return; }
        var provider = npcs.provider();
        if (provider.isEmpty()) { message(sender, "no-provider"); return; }
        try {
            NpcHandle npc;
            if (action.equals("create")) {
                String old = settings().npcKey();
                if (!old.isEmpty() && provider.get().byKey(old).isPresent()) { message(sender, "already-bound"); return; }
                NpcSpec spec = NpcSpec.at(GuiItemUtil.colorize(text("npc-name")), player.getLocation()).withLookClose(true)
                        .withTags(Map.of("lovetweaks.role", "blacksmith"));
                if (args.length >= 3) spec = spec.withSkinName(args[2]);
                npc = provider.get().create(spec);
            } else if (action.equals("bind")) {
                var target = npcs.targeted(player);
                if (target.isEmpty()) { message(sender, "no-target"); return; }
                npc = provider.get().byKey(target.get().key()).orElseThrow();
            } else { message(sender, "usage"); return; }
            if (npc.key().equals(heraldKey.get())) { message(sender, "role-conflict"); return; }
            plugin.getConfig().set("blacksmith.npc-key", npc.key());
            plugin.getConfig().set("blacksmith.npc-name", npc.name());
            plugin.saveConfig(); npc.spawn(); message(sender, "bound");
        } catch (RuntimeException failure) { failure(player, failure); }
    }
    @Override public void close() throws Exception {
        closed = true; task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        HandlerList.unregisterAll(this);
        store.close();
    }
}

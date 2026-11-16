package me.lovelace.loveTweaks.repair;

import dev.lovelace.lovecore.api.economy.LoveEconomy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.*;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Main thread only. Revalidate payment and item locks on every action, not only in the GUI. */
public final class RepairService {
    public enum Payment { STARTED, NO_MONEY, EMPTY, LOCKED, DISABLED, NO_ECONOMY }
    private final JavaPlugin plugin;
    private final RepairStore store;
    private final Map<UUID, RepairOrder> orders;
    private final Supplier<RepairSettings> settings;
    private final Supplier<Optional<LoveEconomy>> economy;
    private final LongSupplier clock;

    public RepairService(RepairStore store, Supplier<RepairSettings> settings,
                         Supplier<Optional<LoveEconomy>> economy, LongSupplier clock, JavaPlugin plugin) throws Exception {
        this.plugin = plugin;
        this.store = store; this.settings = settings; this.economy = economy; this.clock = clock;
        this.orders = store.load();
    }
    public RepairOrder order(UUID owner) { return orders.getOrDefault(owner, RepairOrder.empty()); }
    public boolean ready(UUID owner) { return order(owner).ready(clock.getAsLong()); }
    public long secondsLeft(UUID owner) { return Math.max(0, (order(owner).readyAt() - clock.getAsLong() + 999) / 1000); }
    public Optional<LoveEconomy> economy() { return economy.get(); }
    public RepairSettings.Quote quote(UUID owner) {
        return settings.get().quote(order(owner).items(), economy.get().orElseThrow());
    }
    private void commit(UUID owner, RepairOrder order) throws Exception {
        store.save(owner, order);
        if (order.emptyItems()) orders.remove(owner); else orders.put(owner, order);
    }
    /** Swap the cursor with a draft slot. Caller changes the cursor only after this commits. */
    public ItemStack swap(UUID owner, int slot, ItemStack incoming) throws Exception {
        RepairOrder order = order(owner);
        if (order.paid()) throw new IllegalStateException("Заказ уже оплачен");
        boolean empty = incoming == null || incoming.getType().isAir();
        if (!empty && (!settings.get().enabled() || !RepairSettings.accepts(incoming)))
            throw new IllegalArgumentException("Нужен повреждённый предмет в количестве 1");
        var items = order.items();
        ItemStack old = items.set(slot, empty ? null : incoming.clone());
        commit(owner, new RepairOrder(items, false, 0, 0));
        return old;
    }
    public Payment pay(Player player) throws Exception {
        UUID id = player.getUniqueId(); RepairOrder order = order(id);
        if (order.paid()) return Payment.LOCKED;
        if (order.emptyItems()) return Payment.EMPTY;
        if (!settings.get().enabled()) return Payment.DISABLED;
        var money = economy.get(); if (money.isEmpty()) return Payment.NO_ECONOMY;
        // Reject any invalid persisted item, including changes to its custom durability on upgrade.
        for (ItemStack item : order.items()) if (item != null && !RepairSettings.accepts(item))
            throw new IllegalArgumentException("В заказе есть предмет, который нельзя починить");
        var quote = settings.get().quote(order.items(), money.get());
        long finish = Math.addExact(clock.getAsLong(), Math.multiplyExact(quote.seconds(), 1000));
        ItemStack[] before = copy(player.getInventory().getStorageContents());
        // LoveEconomy may drop change when the wallet has no room. Track that change during
        // this synchronous charge so an I/O rollback cannot leave extra coins in the world.
        CoinDrops drops = new CoinDrops(player, money.get());
        plugin.getServer().getPluginManager().registerEvents(drops, plugin);
        try {
            if (!money.get().charge(player, quote.price())) return Payment.NO_MONEY;
            commit(id, new RepairOrder(order.items(), true, finish, quote.price()));
        } catch (Exception failure) {
            player.getInventory().setStorageContents(before);
            drops.items.forEach(Item::remove);
            throw failure;
        } finally { HandlerList.unregisterAll(drops); }
        return Payment.STARTED;
    }
    /** Collect ready items or return unsubmitted items. Full inventory leaves the remainder in escrow. */
    public int collect(Player player) throws Exception {
        UUID id = player.getUniqueId(); RepairOrder order = order(id);
        if (order.paid() && !order.ready(clock.getAsLong())) return 0;
        var remaining = order.items();
        ItemStack[] planned = copy(player.getInventory().getStorageContents());
        int count = 0;
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack item = remaining.get(i); if (item == null) continue;
            ItemStack give = order.paid() ? RepairSettings.repaired(item) : item.clone();
            if (fitOne(planned, give)) { remaining.set(i, null); count++; }
        }
        // Plan in storage slots only, commit escrow first, then apply the planned inventory.
        // Armor/offhand slots and world drops are never used as overflow storage.
        commit(id, new RepairOrder(remaining, order.paid(), order.readyAt(), order.price()));
        player.getInventory().setStorageContents(planned);
        return count;
    }
    private static final class CoinDrops implements Listener {
        private final Player player;
        private final LoveEconomy money;
        private final List<Item> items = new ArrayList<>();
        private CoinDrops(Player player, LoveEconomy money) { this.player = player; this.money = money; }
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void spawned(ItemSpawnEvent event) {
            Item item = event.getEntity();
            if (item.getWorld().equals(player.getWorld())
                    && item.getLocation().distanceSquared(player.getLocation()) <= 16
                    && money.isCoin(item.getItemStack())) items.add(item);
        }
    }
    private static boolean fitOne(ItemStack[] storage, ItemStack item) {
        for (ItemStack existing : storage) {
            if (existing != null && existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                existing.setAmount(existing.getAmount() + 1); return true;
            }
        }
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == null || storage[i].getType().isAir()) { storage[i] = item; return true; }
        }
        return false;
    }
    private static ItemStack[] copy(ItemStack[] contents) {
        return Arrays.stream(contents).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }
}

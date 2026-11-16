package me.lovelace.loveTweaks.repair;

import dev.lovelace.lovecore.api.economy.*;
import dev.lovelace.lovecore.api.npc.*;
import me.lovelace.loveTweaks.integration.NpcIntegration;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class BlacksmithTest {
    @TempDir Path folder;
    ServerMock server;
    org.bukkit.plugin.java.JavaPlugin plugin;
    PlayerMock player;
    TestMoney money;
    FailingStore store;
    RepairService service;
    Blacksmith blacksmith;
    AtomicLong now = new AtomicLong(1_000_000);
    boolean economyAvailable = true;

    static class FailingStore extends RepairStore {
        boolean fail;
        FailingStore(Path path) throws Exception { super(path); }
        @Override public void save(UUID owner, RepairOrder order) throws SQLException {
            if (fail) throw new SQLException("injected storage failure");
            super.save(owner, order);
        }
    }
    static class TestMoney implements LoveEconomy {
        int charges; double index = 1; boolean failCharge, dropChange;
        @Override public String currencyName() { return "монеты"; }
        @Override public List<Denomination> denominations() { return List.of(); }
        @Override public long balance(Player player) {
            return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull)
                    .filter(i -> i.getType() == Material.EMERALD).mapToLong(ItemStack::getAmount).sum();
        }
        @Override public boolean has(Player p, long amount) { return balance(p) >= amount; }
        @Override public boolean charge(Player p, long amount) {
            if (!has(p, amount)) return false;
            charges++;
            for (int i = 0; i < 36 && amount > 0; i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item == null || item.getType() != Material.EMERALD) continue;
                int take = (int) Math.min(amount, item.getAmount());
                int left = item.getAmount() - take; amount -= take;
                if (left == 0) p.getInventory().setItem(i, null); else { item.setAmount(left); p.getInventory().setItem(i, item); }
            }
            if (dropChange) p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.EMERALD, 5));
            if (failCharge) throw new IllegalStateException("injected economy failure after removal");
            return true;
        }
        @Override public void give(Player p, long amount) { throw new AssertionError("refund must restore exact coins, not issue new drops"); }
        @Override public boolean canFit(Player p, long amount) { return true; }
        @Override public long valueOf(ItemStack stack) { return stack != null && stack.getType() == Material.EMERALD ? 1 : 0; }
        @Override public double priceIndex() { return index; }
    }
    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("RepairFixture");
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            plugin.getConfig().loadFromString(YamlConfiguration.loadConfiguration(reader).saveToString());
        }
        player = server.addPlayer("SmithCustomer");
        money = new TestMoney();
        Bukkit.getServicesManager().register(LoveEconomy.class, money, plugin, ServicePriority.Normal);
        store = new FailingStore(folder.resolve("blacksmith.db"));
        service = new RepairService(store, () -> new RepairSettings(plugin.getConfig()),
                () -> economyAvailable ? Optional.of(money) : Optional.empty(), now::get, plugin);
    }
    @AfterEach void teardown() throws Exception {
        try { if (blacksmith != null) blacksmith.close(); else if (store != null) store.close(); }
        finally { MockBukkit.unmock(); }
    }
    ItemStack damaged(Material type, int damage) {
        ItemStack item = new ItemStack(type);
        Damageable meta = (Damageable) item.getItemMeta(); meta.setDamage(damage); item.setItemMeta(meta); return item;
    }
    ItemStack richSword() {
        ItemStack item = damaged(Material.DIAMOND_SWORD, 500);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Семейный клинок")); meta.lore(List.of(Component.text("История клинка")));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true); meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.setCustomModelData(4321);
        meta.getPersistentDataContainer().set(new NamespacedKey("itemsadder", "id"), PersistentDataType.STRING, "weapons:heirloom");
        meta.getPersistentDataContainer().set(new NamespacedKey("lovetweaks", "iznurenie"), PersistentDataType.INTEGER, 2);
        ((Repairable)meta).setRepairCost(31);
        item.setItemMeta(meta); return item;
    }
    void funds() { player.getInventory().addItem(new ItemStack(Material.EMERALD, 1024)); }
    void put(int slot, ItemStack item) throws Exception { service.swap(player.getUniqueId(), slot, item); }
    RepairOrder order() { return service.order(player.getUniqueId()); }
    void finish() { now.addAndGet(1_000_000); }
    long count(Material material) { return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i -> i.getType() == material).mapToLong(ItemStack::getAmount).sum(); }
    void gui() throws Exception {
        // Use the same persisted data, but the module owns the service once it starts.
        NpcIntegration npcs = new NpcIntegration(() -> "loveessential", id -> Optional.empty(), s -> {}, () -> false);
        blacksmith = new Blacksmith(plugin, npcs, () -> "", store, now::get);
        service = blacksmith.service();
        blacksmith.open(player);
    }
    InventoryClickEvent click(int rawSlot, ClickType click, InventoryAction action) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, rawSlot, click, action);
        Bukkit.getPluginManager().callEvent(event); return event;
    }

    @Test void priceTracksMaterialWearEnchantsAndGlobalIndex() {
        var settings = new RepairSettings(plugin.getConfig());
        long iron = settings.quote(List.of(damaged(Material.IRON_SWORD, 125)), money).price();
        long diamond = settings.quote(List.of(damaged(Material.DIAMOND_SWORD, 780)), money).price();
        assertEquals(50, iron); assertTrue(diamond > iron);
        assertTrue(settings.quote(List.of(damaged(Material.IRON_SWORD, 200)), money).price() > iron);
        var enchanted = damaged(Material.IRON_SWORD, 125); enchanted.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        assertTrue(settings.quote(List.of(enchanted), money).price() > iron);
        money.index = 2; assertEquals(100, settings.quote(List.of(damaged(Material.IRON_SWORD, 125)), money).price());
    }
    @Test void customEnchantmentsAffectPriceAndTime() {
        var settings = new RepairSettings(plugin.getConfig());
        ItemStack base = damaged(Material.IRON_SWORD, 125), custom = base.clone();
        var meta = custom.getItemMeta(); meta.getPersistentDataContainer().set(new NamespacedKey("lovetweaks", "enchant_iznurenie"), PersistentDataType.INTEGER, 2); custom.setItemMeta(meta);
        assertTrue(settings.quote(List.of(custom), money).price() > settings.quote(List.of(base), money).price());
        assertTrue(settings.quote(List.of(custom), money).seconds() > settings.quote(List.of(base), money).seconds());
    }
    @Test void exactMaterialOverridesTierAndMinimumPreventsFreeRepairs() {
        plugin.getConfig().set("blacksmith.prices.materials.IRON_SWORD", 10);
        assertEquals(5, new RepairSettings(plugin.getConfig()).quote(List.of(damaged(Material.IRON_SWORD, 1)), money).price());
        assertEquals(10, new RepairSettings(plugin.getConfig()).quote(List.of(damaged(Material.IRON_SWORD, 250)), money).price());
    }
    @Test void rejectsNonToolsUndamagedUnbreakableAndStacks() {
        for (ItemStack item : List.of(new ItemStack(Material.STONE), new ItemStack(Material.IRON_SWORD))) assertFalse(RepairSettings.accepts(item));
        ItemStack unbreakable = damaged(Material.IRON_SWORD, 1); var meta = unbreakable.getItemMeta(); meta.setUnbreakable(true); unbreakable.setItemMeta(meta);
        assertFalse(RepairSettings.accepts(unbreakable));
        ItemStack stacked = damaged(Material.IRON_SWORD, 1); stacked.setAmount(2); assertFalse(RepairSettings.accepts(stacked));
    }
    @Test void preservesEveryComponentThroughSqlitePaymentRestartAndCollection() throws Exception {
        ItemStack original = richSword(), expected = original.clone();
        Damageable fixedMeta = (Damageable) expected.getItemMeta(); fixedMeta.setDamage(0); expected.setItemMeta(fixedMeta);
        put(0, original); put(1, damaged(Material.IRON_HELMET, 100)); funds();
        assertEquals(RepairService.Payment.STARTED, service.pay(player));
        long paidPrice = order().price(), finishAt = order().readyAt();
        store.close(); store = new FailingStore(folder.resolve("blacksmith.db"));
        service = new RepairService(store, () -> new RepairSettings(plugin.getConfig()), () -> Optional.of(money), now::get, plugin);
        assertEquals(paidPrice, order().price()); assertEquals(finishAt, order().readyAt());
        assertEquals(0, service.collect(player));
        finish(); assertEquals(2, service.collect(player));
        ItemStack actual = Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i -> i.getType() == Material.DIAMOND_SWORD).findFirst().orElseThrow();
        assertEquals(expected, actual, "all metadata/components survive, including Mending and custom enchants");
        assertTrue(order().emptyItems()); assertTrue(store.load().isEmpty());
        assertEquals(0, service.collect(player)); assertEquals(1, count(Material.DIAMOND_SWORD));
    }
    @Test void locksPaidItemsAndChargesOnlyOnce() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds();
        assertEquals(RepairService.Payment.STARTED, service.pay(player));
        long balance = money.balance(player);
        assertEquals(RepairService.Payment.LOCKED, service.pay(player)); assertEquals(balance, money.balance(player));
        assertEquals(1, money.charges); assertEquals(0, service.collect(player));
        assertThrows(IllegalStateException.class, () -> service.swap(player.getUniqueId(), 0, null));
        assertEquals(100, ((Damageable)order().items().getFirst().getItemMeta()).getDamage());
    }
    @Test void insufficientCoinsAndUnavailableEconomyLeaveItemsUntouched() throws Exception {
        ItemStack item = damaged(Material.IRON_SWORD, 100); put(0, item);
        assertEquals(RepairService.Payment.NO_MONEY, service.pay(player)); assertFalse(order().paid()); assertEquals(item, order().items().getFirst());
        economyAvailable = false;
        assertEquals(RepairService.Payment.NO_ECONOMY, service.pay(player)); assertFalse(order().paid());
    }
    @Test void storageFailureRollsBackPhysicalCoinsAndKeepsDraft() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds();
        ItemStack[] before = Arrays.stream(player.getInventory().getStorageContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new); store.fail = true;
        assertThrows(SQLException.class, () -> service.pay(player));
        assertArrayEquals(before, player.getInventory().getStorageContents()); assertFalse(order().paid());
        store.fail = false; assertFalse(store.load().get(player.getUniqueId()).paid());
    }
    @Test void fullInventoryKeepsReadyItemsAndAllowsPartialCollection() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); put(1, damaged(Material.DIAMOND_PICKAXE, 500)); funds(); service.pay(player); finish();
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        assertEquals(0, service.collect(player)); assertFalse(order().emptyItems());
        player.getInventory().setItem(4, null); assertEquals(1, service.collect(player)); assertEquals(1, count(Material.IRON_SWORD));
        assertNull(order().items().getFirst()); assertNotNull(order().items().get(1));
        player.getInventory().setItem(5, null); assertEquals(1, service.collect(player)); assertTrue(order().emptyItems());
    }
    @Test void collectionStorageFailureRollsBackPlayerInventory() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds(); service.pay(player); finish();
        ItemStack[] before = Arrays.stream(player.getInventory().getStorageContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new); store.fail = true;
        assertThrows(SQLException.class, () -> service.collect(player));
        assertArrayEquals(before, player.getInventory().getStorageContents()); assertNotNull(order().items().getFirst());
    }
    @Test void disabledModuleStillReturnsDraftsAndReadyPaidOrders() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); plugin.getConfig().set("blacksmith.enabled", false);
        assertEquals(RepairService.Payment.DISABLED, service.pay(player));
        assertEquals(1, service.collect(player)); assertEquals(100, ((Damageable)player.getInventory().getItem(0).getItemMeta()).getDamage());
    }
    @Test void negativeAndNonFiniteConfigCannotProduceFreeOrders() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds();
        plugin.getConfig().set("blacksmith.prices.enchantment-level-multiplier", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.pay(player)); assertEquals(0, money.charges);
    }
    @Test void ownersCannotShareOrTakeEachOthersOrders() throws Exception {
        put(0, richSword()); PlayerMock other = server.addPlayer("Other");
        assertTrue(service.order(other.getUniqueId()).emptyItems()); assertEquals(0, service.collect(other)); assertNotNull(order().items().getFirst());
    }
    @Test void guiCursorDepositAndCloseReturnOriginalWithoutRepairOrPayment() throws Exception {
        gui(); ItemStack original = richSword(); player.setItemOnCursor(original);
        assertTrue(click(Blacksmith.SLOTS[0], ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
        assertTrue(player.getItemOnCursor().getType().isAir()); assertEquals(original, order().items().getFirst());
        player.closeInventory(); assertTrue(order().emptyItems()); assertEquals(0, money.charges);
        assertEquals(original, player.getInventory().getItem(0));
    }
    @Test void guiShiftDepositPayAndLockedExtractionBlocksNumberDoubleAndDrag() throws Exception {
        gui(); ItemStack original = richSword(); player.getInventory().setItem(0, original); funds();
        click(36 + 27, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertNull(player.getInventory().getItem(0)); assertEquals(original, order().items().getFirst());
        click(Blacksmith.PAY, ClickType.LEFT, InventoryAction.PICKUP_ALL); assertTrue(order().paid());
        for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DROP, ClickType.SWAP_OFFHAND))
            assertTrue(click(Blacksmith.SLOTS[0], type, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(click(36, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE), new ItemStack(Material.STONE, 2), false, Map.of(Blacksmith.SLOTS[0], new ItemStack(Material.STONE)));
        Bukkit.getPluginManager().callEvent(drag); assertTrue(drag.isCancelled());
        player.closeInventory(); assertEquals(0, count(Material.DIAMOND_SWORD)); assertNotNull(order().items().getFirst());
        finish(); blacksmith.open(player); click(Blacksmith.COLLECT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertEquals(1, count(Material.DIAMOND_SWORD)); assertTrue(order().emptyItems()); assertEquals(1, money.charges);
    }
    @Test void greyPayButtonAndRepeatedClicksWithoutMoneyCannotStartRepair() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); gui();
        assertEquals(Material.GRAY_DYE, player.getOpenInventory().getTopInventory().getItem(Blacksmith.PAY).getType());
        click(Blacksmith.PAY, ClickType.LEFT, InventoryAction.PICKUP_ALL); click(Blacksmith.PAY, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertFalse(order().paid()); assertEquals(0, money.charges);
    }
    @Test void fullInventoryOnClosingDraftKeepsItAcrossReopen() throws Exception {
        put(0, richSword()); gui();
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        player.closeInventory(); assertNotNull(order().items().getFirst());
        player.getInventory().setItem(0, null); blacksmith.open(player);
        click(Blacksmith.COLLECT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(order().emptyItems()); assertEquals(1, count(Material.DIAMOND_SWORD));
    }
    @Test void npcRoleMatchesBothProviderAndBoundKey() throws Exception {
        gui(); player.closeInventory(); plugin.getConfig().set("blacksmith.npc-key", "ext-smith");
        for (String provider : List.of("citizens", "loveessential")) {
            NpcHandle handle = (NpcHandle) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{NpcHandle.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "providerId" -> provider; case "key" -> "ext-smith"; default -> null;
                    });
            var event = new NpcClickEvent(player, handle, NpcClick.RIGHT);
            Bukkit.getPluginManager().callEvent(event);
            assertEquals(provider.equals("loveessential"), event.isCancelled());
            if (provider.equals("loveessential")) assertInstanceOf(Blacksmith.Menu.class, player.getOpenInventory().getTopInventory().getHolder());
        }
    }

    @Test void failedPaymentAlsoRemovesPhysicalChangeDroppedByEconomy() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds();
        long before = money.balance(player); store.fail = true; money.dropChange = true;
        assertThrows(SQLException.class, () -> service.pay(player));
        assertEquals(before, money.balance(player)); assertFalse(order().paid());
        assertTrue(player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class).isEmpty());
    }
    @Test void economyFailureAfterRemovingCoinsRestoresInventory() throws Exception {
        put(0, damaged(Material.IRON_SWORD, 100)); funds();
        long before = money.balance(player); money.failCharge = true;
        assertThrows(IllegalStateException.class, () -> service.pay(player));
        assertEquals(before, money.balance(player)); assertFalse(order().paid());
    }

}

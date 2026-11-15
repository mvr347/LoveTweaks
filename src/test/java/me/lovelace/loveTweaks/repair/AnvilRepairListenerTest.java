package me.lovelace.loveTweaks.repair;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.*;
import static org.junit.jupiter.api.Assertions.*;

class AnvilRepairListenerTest {
    ServerMock server; JavaPlugin plugin; PlayerMock player;
    AnvilRepairListener listener; TestAnvilView view; AnvilInventoryMock inventory;
    static class TestAnvilView extends InventoryViewMock implements AnvilView {
        int cost = 7, max = 40, materials = 1;
        TestAnvilView(PlayerMock player, AnvilInventoryMock inventory) { super(player, inventory, player.getInventory(), InventoryType.ANVIL); }
        @Override public AnvilInventory getTopInventory() { return (AnvilInventory) super.getTopInventory(); }
        @Override public String getRenameText() { return ""; }
        @Override public int getRepairItemCountCost() { return materials; }
        @Override public int getRepairCost() { return cost; }
        @Override public int getMaximumRepairCost() { return max; }
        @Override public void setRepairItemCountCost(int value) { materials = value; }
        @Override public void setRepairCost(int value) { cost = value; }
        @Override public void setMaximumRepairCost(int value) { max = value; }
        @Override public boolean bypassesEnchantmentLevelRestriction() { return false; }
        @Override public void bypassEnchantmentLevelRestriction(boolean value) { }
    }
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = MockBukkit.createMockPlugin(); player = server.addPlayer();
        listener = new AnvilRepairListener(plugin); inventory = new AnvilInventoryMock(player);
        view = new TestAnvilView(player, inventory); player.openInventory(view);
    }
    @AfterEach void stop() { MockBukkit.unmock(); }
    ItemStack priorWork(int penalty) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        var meta = item.getItemMeta(); ((Repairable)meta).setRepairCost(penalty);
        ((Damageable)meta).setDamage(100); meta.displayName(Component.text("Старый меч")); item.setItemMeta(meta); return item;
    }
    int penalty(ItemStack item) { return ((Repairable)item.getItemMeta()).getRepairCost(); }
    @Test void clearsResultPenaltyWithoutChangingExperienceOrMaterialCost() {
        ItemStack original = priorWork(1), expected = priorWork(0);
        var event = new PrepareAnvilEvent(view, original); listener.prepare(event);
        assertEquals(expected, event.getResult()); assertEquals(1, penalty(original), "do not mutate the source stack");
        assertEquals(7, view.getRepairCost()); assertEquals(1, view.getRepairItemCountCost()); assertEquals(40, view.getMaximumRepairCost());
    }
    @Test void oldExpensiveInputsAreNormalizedForVanillaRecalculationOutsidePrepareEvent() {
        inventory.setItem(0, priorWork(31)); inventory.setItem(1, priorWork(15));
        var event = new PrepareAnvilEvent(view, null); listener.prepare(event); listener.prepare(event);
        assertNull(event.getResult()); assertEquals(31, penalty(inventory.getItem(0)));
        server.getScheduler().performOneTick();
        assertEquals(0, penalty(inventory.getItem(0))); assertEquals(0, penalty(inventory.getItem(1)));
        assertEquals(Component.text("Старый меч"), inventory.getItem(0).getItemMeta().displayName());
        assertEquals(100, ((Damageable)inventory.getItem(0).getItemMeta()).getDamage());
    }
    @Test void scheduledNormalizationDoesNotTouchItemsAfterViewWasClosed() {
        inventory.setItem(0, priorWork(31)); listener.prepare(new PrepareAnvilEvent(view, null));
        player.closeInventory(); server.getScheduler().performOneTick(); assertEquals(31, penalty(inventory.getItem(0)));
    }
    @Test void configCanRestoreVanillaPenalty() {
        plugin.getConfig().set("anvil.remove-prior-work-penalty", false);
        ItemStack item = priorWork(31); inventory.setItem(0, item);
        var event = new PrepareAnvilEvent(view, item); listener.prepare(event); server.getScheduler().performOneTick();
        assertEquals(31, penalty(event.getResult())); assertEquals(31, penalty(inventory.getItem(0)));
    }
}

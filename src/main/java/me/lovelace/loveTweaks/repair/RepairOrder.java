package me.lovelace.loveTweaks.repair;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** One escrow per owner, never the visible GUI items. Readiness uses wall time, including downtime. */
public record RepairOrder(List<ItemStack> items, boolean paid, long readyAt, long price) {
    public static final int CAPACITY = 18;
    public RepairOrder {
        if (items.size() != CAPACITY) throw new IllegalArgumentException("Неверное число слотов заказа");
        items = copy(items);
    }
    @Override public List<ItemStack> items() { return copy(items); }
    public static RepairOrder empty() {
        return new RepairOrder(new ArrayList<>(java.util.Collections.nCopies(CAPACITY, null)), false, 0, 0);
    }
    public boolean emptyItems() { return items.stream().allMatch(i -> i == null || i.getType().isAir()); }
    public boolean ready(long now) { return paid && now >= readyAt; }
    public static List<ItemStack> copy(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : items) copy.add(item == null || item.getType().isAir() ? null : item.clone());
        return copy;
    }
}

package me.lovelace.loveTweaks.repair;

import org.bukkit.event.*;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Keep the vanilla operation cost, remove only the exponential prior-work penalty. */
public final class AnvilRepairListener implements Listener {
    private final JavaPlugin plugin;
    private final Set<AnvilInventory> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    public AnvilRepairListener(JavaPlugin plugin) { this.plugin = plugin; }
    public static ItemStack withoutPenalty(ItemStack original) {
        if (original == null || !(original.getItemMeta() instanceof Repairable meta) || meta.getRepairCost() == 0) return original;
        ItemStack copy = original.clone(); meta.setRepairCost(0); copy.setItemMeta(meta); return copy;
    }
    private static boolean hasPenalty(ItemStack item) {
        return item != null && item.getItemMeta() instanceof Repairable meta && meta.getRepairCost() > 0;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil.remove-prior-work-penalty", true)) return;
        AnvilInventory inv = event.getInventory();
        // Vanilla may already have hidden a >=40-level result. Recalculate from clean input
        // components next tick, outside this Prepare event (no recursive/overwritten result).
        boolean dirty = hasPenalty(inv.getItem(0)) || hasPenalty(inv.getItem(1));
        if (dirty) {
            event.setResult(null);
            if (pending.add(inv)) plugin.getServer().getScheduler().runTask(plugin, () -> {
                pending.remove(inv);
                if (!inv.equals(event.getView().getPlayer().getOpenInventory().getTopInventory())) return;
                for (int i = 0; i < 2; i++) {
                    ItemStack current = inv.getItem(i), clean = withoutPenalty(current);
                    if (clean != current) inv.setItem(i, clean);
                }
            });
            return;
        }
        event.setResult(withoutPenalty(event.getResult()));
    }
}

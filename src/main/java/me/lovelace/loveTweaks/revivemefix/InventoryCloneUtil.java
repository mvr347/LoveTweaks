package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.inventory.ItemStack;

/**
 * Deep-copies {@link ItemStack} arrays/items for a snapshot. Never store a raw reference to a
 * live inventory's contents array — the player (or another plugin) can mutate those slots after
 * the snapshot is taken, which would silently corrupt what gets dropped later.
 */
final class InventoryCloneUtil {

    private InventoryCloneUtil() {
    }

    static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }

        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = cloneItem(source[i]);
        }
        return result;
    }

    static ItemStack cloneItem(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}

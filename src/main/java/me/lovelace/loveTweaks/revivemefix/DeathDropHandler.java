package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Turns a captured {@link InventorySnapshot} into vanilla-style death drops on a
 * {@link PlayerDeathEvent}. Adds to {@link PlayerDeathEvent#getDrops()} rather than dropping
 * items into the world directly, so Paper's own death-drop pipeline (and any other plugin that
 * still wants to react to drops) sees them normally.
 */
final class DeathDropHandler {

    private DeathDropHandler() {
    }

    static void apply(PlayerDeathEvent event, InventorySnapshot snapshot) {
        for (ItemStack item : snapshot.contents()) {
            addIfValid(event, item);
        }
        for (ItemStack item : snapshot.armor()) {
            addIfValid(event, item);
        }
        addIfValid(event, snapshot.offHand());
    }

    private static void addIfValid(PlayerDeathEvent event, ItemStack item) {
        if (isValid(item)) {
            event.getDrops().add(item.clone());
        }
    }

    private static boolean isValid(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }
}

package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * An immutable, independent copy of a player's inventory taken the moment they go Downed.
 * Storage contents (main + hotbar), armor and offhand are kept as separate arrays rather than
 * flattened into one, so {@link DeathDropHandler} can reason about them without guessing slot
 * ranges back out of a single combined array.
 */
final class InventorySnapshot {

    private final UUID playerId;
    private final long createdAtMillis;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offHand;

    private InventorySnapshot(UUID playerId, long createdAtMillis, ItemStack[] contents, ItemStack[] armor, ItemStack offHand) {
        this.playerId = playerId;
        this.createdAtMillis = createdAtMillis;
        this.contents = contents;
        this.armor = armor;
        this.offHand = offHand;
    }

    static InventorySnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(
                player.getUniqueId(),
                System.currentTimeMillis(),
                InventoryCloneUtil.cloneArray(inventory.getStorageContents()),
                InventoryCloneUtil.cloneArray(inventory.getArmorContents()),
                InventoryCloneUtil.cloneItem(inventory.getItemInOffHand())
        );
    }

    UUID playerId() {
        return playerId;
    }

    ItemStack[] contents() {
        return contents;
    }

    ItemStack[] armor() {
        return armor;
    }

    ItemStack offHand() {
        return offHand;
    }

    boolean isExpired(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return false;
        }
        return System.currentTimeMillis() - createdAtMillis > timeoutSeconds * 1000L;
    }
}

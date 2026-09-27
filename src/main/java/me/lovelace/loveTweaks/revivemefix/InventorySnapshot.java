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
    private final int totalExperience;
    private final int level;

    private InventorySnapshot(UUID playerId, long createdAtMillis, ItemStack[] contents, ItemStack[] armor, ItemStack offHand, int totalExperience, int level) {
        this.playerId = playerId;
        this.createdAtMillis = createdAtMillis;
        this.contents = contents;
        this.armor = armor;
        this.offHand = offHand;
        this.totalExperience = totalExperience;
        this.level = level;
    }

    static InventorySnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(
                player.getUniqueId(),
                System.currentTimeMillis(),
                InventoryCloneUtil.cloneArray(inventory.getStorageContents()),
                InventoryCloneUtil.cloneArray(inventory.getArmorContents()),
                InventoryCloneUtil.cloneItem(inventory.getItemInOffHand()),
                player.getTotalExperience(),
                player.getLevel()
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

    int totalExperience() {
        return totalExperience;
    }

    int level() {
        return level;
    }

    java.util.List<ItemStack> allItems() {
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    list.add(item);
                }
            }
        }
        if (armor != null) {
            for (ItemStack item : armor) {
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    list.add(item);
                }
            }
        }
        if (offHand != null && !offHand.getType().isAir() && offHand.getAmount() > 0) {
            list.add(offHand);
        }
        return list;
    }

    boolean isExpired(long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return false;
        }
        return System.currentTimeMillis() - createdAtMillis > timeoutSeconds * 1000L;
    }
}

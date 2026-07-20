package me.lovelace.loveTweaks.economy;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Checks and withdraws ItemsAdder custom items used as an in-game currency (mirrors
 * LoveClans' ItemsAdderEconomyService — there is no Vault economy plugin on this server).
 * ItemsAdder has no built-in "remove N of item" API, so withdrawal manually scans and
 * decrements matching inventory slots.
 */
public final class ItemsAdderEconomyService {

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /** Returns how many of the given ItemsAdder item the player is carrying. */
    public int countOwned(Player player, String namespacedId) {
        if (!isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            CustomStack customStack = CustomStack.byItemStack(stack);
            if (customStack != null && namespacedId.equals(customStack.getNamespacedID())) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean hasItem(Player player, String namespacedId, long amount) {
        if (amount <= 0) {
            return true;
        }
        if (!isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return false;
        }
        return countOwned(player, namespacedId) >= amount;
    }

    /** Caller must have already verified {@link #hasItem} for this exact amount. */
    public void withdraw(Player player, String namespacedId, long amount) {
        if (amount <= 0 || !isAvailable() || namespacedId == null || namespacedId.isBlank()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        long remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null) {
                continue;
            }
            CustomStack customStack = CustomStack.byItemStack(stack);
            if (customStack == null || !namespacedId.equals(customStack.getNamespacedID())) {
                continue;
            }
            long take = Math.min(remaining, stack.getAmount());
            if (take >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount((int) (stack.getAmount() - take));
            }
            remaining -= take;
        }
        inventory.setContents(contents);
        player.updateInventory();
    }
}

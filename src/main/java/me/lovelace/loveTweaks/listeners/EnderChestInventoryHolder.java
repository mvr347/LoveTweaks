package me.lovelace.loveTweaks.listeners;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnderChestInventoryHolder implements InventoryHolder {

    private final Block enderChestBlock;
    private Inventory inventory;

    public EnderChestInventoryHolder(Block enderChestBlock) {
        this.enderChestBlock = enderChestBlock;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Block getEnderChestBlock() {
        return enderChestBlock;
    }
}

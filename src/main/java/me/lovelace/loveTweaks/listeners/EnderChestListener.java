package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.InventorySerializationUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState; // Import TileState
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.logging.Level;

public class EnderChestListener implements Listener {

    private final LoveTweaks plugin;

    public EnderChestListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.ENDER_CHEST) {
                if (plugin.getLoveTweaksConfig().isEnderChestsAsNormalChests()) {
                    event.setCancelled(true); // Cancel default Ender Chest behavior

                    BlockState blockState = clickedBlock.getState();
                    if (!(blockState instanceof TileState tileState)) { // Check and cast to TileState
                        plugin.getLogger().log(Level.WARNING, "Ender Chest at " + clickedBlock.getLocation() + " is not a TileState. Cannot store inventory.");
                        return;
                    }
                    PersistentDataContainer container = tileState.getPersistentDataContainer(); // Use tileState

                    Inventory chestInventory;
                    String serializedInventory = container.get(plugin.getEnderChestKey(), PersistentDataType.STRING);

                    if (serializedInventory != null && !serializedInventory.isEmpty()) {
                        try {
                            ItemStack[] contents = InventorySerializationUtil.inventoryFromBase64(serializedInventory);
                            chestInventory = plugin.getServer().createInventory(new EnderChestInventoryHolder(clickedBlock), 27, "Ender Chest (Normal)");
                            chestInventory.setContents(contents);
                        } catch (IOException e) {
                            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize Ender Chest inventory for block at " + clickedBlock.getLocation(), e);
                            chestInventory = plugin.getServer().createInventory(new EnderChestInventoryHolder(clickedBlock), 27, "Ender Chest (Normal)");
                        }
                    } else {
                        chestInventory = plugin.getServer().createInventory(new EnderChestInventoryHolder(clickedBlock), 27, "Ender Chest (Normal)");
                    }

                    // Set the inventory in the holder
                    ((EnderChestInventoryHolder) chestInventory.getHolder()).setInventory(chestInventory);
                    event.getPlayer().openInventory(chestInventory);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof EnderChestInventoryHolder holder) {
            Block enderChestBlock = holder.getEnderChestBlock();
            if (enderChestBlock != null && enderChestBlock.getType() == Material.ENDER_CHEST) {
                BlockState blockState = enderChestBlock.getState();
                if (!(blockState instanceof TileState tileState)) { // Check and cast to TileState
                    plugin.getLogger().log(Level.WARNING, "Ender Chest at " + enderChestBlock.getLocation() + " is not a TileState. Cannot save inventory.");
                    return;
                }
                PersistentDataContainer container = tileState.getPersistentDataContainer(); // Use tileState

                try {
                    String serializedInventory = InventorySerializationUtil.inventoryToBase64(event.getInventory());
                    container.set(plugin.getEnderChestKey(), PersistentDataType.STRING, serializedInventory);
                    tileState.update(); // Save the changes to the block state (use tileState.update())
                } catch (IllegalStateException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to serialize Ender Chest inventory for block at " + enderChestBlock.getLocation(), e);
                }
            }
        }
    }
}

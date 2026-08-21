package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.InventorySerializationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Обработчик режима эндер-сундуков как обычных сундуков ({@code ender-chests.as-normal-chests: true}).
 * Защищен от:
 * 1. Дюпа при одновременном открытии несколькими игроками (общий экземпляр Inventory).
 * 2. Потери/дюпа предметов при поломке сундука (дроп содержимого и закрытие GUI).
 * 3. Двойного открытия при взаимодействии двумя руками.
 */
public class EnderChestListener implements Listener {

    public record BlockKey(String worldName, int x, int y, int z) {
        public static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
        public static BlockKey from(Location loc) {
            return new BlockKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
    }

    private final LoveTweaks plugin;
    // Кэш открытых инвентарей по координатам сундука для предотвращения дюпа при мульти-доступе
    private final Map<BlockKey, Inventory> activeInventories = new ConcurrentHashMap<>();

    public EnderChestListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.ENDER_CHEST) {
            return;
        }
        if (!plugin.getLoveTweaksConfig().isEnderChestsAsNormalChests()) {
            return;
        }

        event.setCancelled(true);
        BlockKey blockKey = BlockKey.from(clickedBlock);
        Location blockLoc = clickedBlock.getLocation();

        // Если сундук уже кем-то открыт — открываем тот же самый инвентарь (защита от дюпа)
        Inventory chestInventory = activeInventories.get(blockKey);
        if (chestInventory == null) {
            BlockState blockState = clickedBlock.getState();
            if (!(blockState instanceof TileState tileState)) {
                plugin.getLogger().log(Level.WARNING, "Ender Chest at " + blockLoc + " is not a TileState. Cannot store inventory.");
                return;
            }

            PersistentDataContainer container = tileState.getPersistentDataContainer();
            String serializedInventory = container.get(plugin.getEnderChestKey(), PersistentDataType.STRING);

            chestInventory = plugin.getServer().createInventory(new EnderChestInventoryHolder(clickedBlock), 27, "Ender Chest");
            if (serializedInventory != null && !serializedInventory.isEmpty()) {
                try {
                    ItemStack[] contents = InventorySerializationUtil.inventoryFromBase64(serializedInventory);
                    chestInventory.setContents(contents);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to deserialize Ender Chest inventory at " + blockLoc, e);
                }
            }

            if (chestInventory.getHolder() instanceof EnderChestInventoryHolder holder) {
                holder.setInventory(chestInventory);
            }
            activeInventories.put(blockKey, chestInventory);
        }

        event.getPlayer().openInventory(chestInventory);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderChestInventoryHolder holder)) {
            return;
        }

        Block enderChestBlock = holder.getEnderChestBlock();
        if (enderChestBlock == null) {
            return;
        }

        Location loc = enderChestBlock.getLocation();
        BlockKey blockKey = BlockKey.from(enderChestBlock);
        // Сохраняем в PDC только когда ПОСЛЕДНИЙ игрок закрывает инвентарь
        if (event.getInventory().getViewers().size() <= 1) {
            activeInventories.remove(blockKey);

            if (enderChestBlock.getType() == Material.ENDER_CHEST) {
                BlockState blockState = enderChestBlock.getState();
                if (blockState instanceof TileState tileState) {
                    try {
                        String serialized = InventorySerializationUtil.inventoryToBase64(event.getInventory());
                        tileState.getPersistentDataContainer().set(plugin.getEnderChestKey(), PersistentDataType.STRING, serialized);
                        tileState.update();
                    } catch (IllegalStateException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to serialize Ender Chest inventory at " + loc, e);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleChestDestruction(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            handleChestDestruction(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            handleChestDestruction(b);
        }
    }

    private void handleChestDestruction(Block block) {
        if (block.getType() != Material.ENDER_CHEST || !plugin.getLoveTweaksConfig().isEnderChestsAsNormalChests()) {
            return;
        }

        Location loc = block.getLocation();
        BlockKey blockKey = BlockKey.from(block);
        Inventory active = activeInventories.remove(blockKey);
        ItemStack[] items = null;

        if (active != null) {
            items = active.getContents();
            for (HumanEntity viewer : new ArrayList<>(active.getViewers())) {
                viewer.closeInventory();
            }
        } else {
            BlockState state = block.getState();
            if (state instanceof TileState tileState) {
                String serialized = tileState.getPersistentDataContainer().get(plugin.getEnderChestKey(), PersistentDataType.STRING);
                if (serialized != null && !serialized.isEmpty()) {
                    try {
                        items = InventorySerializationUtil.inventoryFromBase64(serialized);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to deserialize destroyed Ender Chest inventory at " + loc + ": " + e.getMessage(), e);
                    }
                }
            }
        }

        if (items != null) {
            Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
            for (ItemStack stack : items) {
                if (stack != null && stack.getType() != Material.AIR) {
                    loc.getWorld().dropItemNaturally(dropLoc, stack);
                }
            }
        }
    }

    /**
     * Закрывает все открытые виртуальные эндер-сундуки при отключении или перезагрузке плагина.
     */
    public void closeAll() {
        for (Inventory inv : new ArrayList<>(activeInventories.values())) {
            for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) {
                viewer.closeInventory();
            }
        }
        activeInventories.clear();
    }
}

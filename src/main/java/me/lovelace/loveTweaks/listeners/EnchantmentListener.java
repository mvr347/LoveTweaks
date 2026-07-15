package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class EnchantmentListener implements Listener {

    private final LoveTweaks plugin;

    public EnchantmentListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getLoveTweaksConfig().isDisableMending()) {
            // Remove Mending from the enchantments offered
            event.getEnchantsToAdd().remove(Enchantment.MENDING);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
            event.setResult(null); // No result if all enchantments are disabled
            return;
        }
        if (plugin.getLoveTweaksConfig().isDisableMending()) {
            ItemStack result = event.getResult();
            if (result != null) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null) {
                    if (meta.hasEnchant(Enchantment.MENDING)) {
                        meta.removeEnchant(Enchantment.MENDING);
                        result.setItemMeta(meta);
                    }
                    // For enchanted books
                    if (meta instanceof EnchantmentStorageMeta esMeta) {
                        if (esMeta.hasStoredEnchant(Enchantment.MENDING)) {
                            esMeta.removeStoredEnchant(Enchantment.MENDING);
                            result.setItemMeta(esMeta);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
            // Clear all loot if all enchantments are disabled (might be too harsh, consider removing only enchanted items)
            // For now, let's just remove enchantments from items.
            for (ItemStack item : event.getLoot()) {
                if (item != null && item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasEnchants()) {
                        for (Enchantment enchantment : meta.getEnchants().keySet()) {
                            meta.removeEnchant(enchantment);
                        }
                        item.setItemMeta(meta);
                    }
                    if (meta instanceof EnchantmentStorageMeta esMeta) {
                        for (Enchantment enchantment : esMeta.getStoredEnchants().keySet()) {
                            esMeta.removeStoredEnchant(enchantment);
                        }
                        item.setItemMeta(esMeta);
                    }
                }
            }
            return;
        }
        if (plugin.getLoveTweaksConfig().isDisableMending()) {
            Iterator<ItemStack> iterator = event.getLoot().iterator();
            while (iterator.hasNext()) {
                ItemStack item = iterator.next();
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (meta.hasEnchant(Enchantment.MENDING)) {
                            meta.removeEnchant(Enchantment.MENDING);
                            item.setItemMeta(meta);
                        }
                        if (meta instanceof EnchantmentStorageMeta esMeta) {
                            if (esMeta.hasStoredEnchant(Enchantment.MENDING)) {
                                esMeta.removeStoredEnchant(Enchantment.MENDING);
                                item.setItemMeta(esMeta);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!plugin.getLoveTweaksConfig().isDisableMending() && !plugin.getLoveTweaksConfig().isDisableAllEnchantments()) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        ItemStack stack = caughtItem.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        boolean changed = false;
        if (meta instanceof EnchantmentStorageMeta esMeta) {
            if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
                for (Enchantment ench : new HashSet<>(esMeta.getStoredEnchants().keySet())) {
                    esMeta.removeStoredEnchant(ench);
                }
                changed = true;
            } else if (esMeta.hasStoredEnchant(Enchantment.MENDING)) {
                esMeta.removeStoredEnchant(Enchantment.MENDING);
                changed = true;
            }
            if (changed) stack.setItemMeta(esMeta);
        } else {
            if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
                for (Enchantment ench : new HashSet<>(meta.getEnchants().keySet())) {
                    meta.removeEnchant(ench);
                }
                changed = true;
            } else if (meta.hasEnchant(Enchantment.MENDING)) {
                meta.removeEnchant(Enchantment.MENDING);
                changed = true;
            }
            if (changed) stack.setItemMeta(meta);
        }
        if (changed) caughtItem.setItemStack(stack);
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        MerchantRecipe recipe = event.getRecipe();
        ItemStack result = recipe.getResult();

        if (plugin.getLoveTweaksConfig().isDisableAllEnchantments()) {
            if (result != null && result.hasItemMeta()) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null && meta.hasEnchants()) {
                    event.setCancelled(true); // Cancel the trade if it offers any enchanted item
                    return;
                }
                if (meta instanceof EnchantmentStorageMeta esMeta && !esMeta.getStoredEnchants().isEmpty()) {
                    event.setCancelled(true); // Cancel the trade if it offers an enchanted book
                    return;
                }
            }
        }

        if (plugin.getLoveTweaksConfig().isDisableMending()) {
            if (result != null && result.hasItemMeta()) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null) {
                    if (meta.hasEnchant(Enchantment.MENDING)) {
                        event.setCancelled(true); // Cancel the trade if it offers Mending
                        return;
                    }
                    if (meta instanceof EnchantmentStorageMeta esMeta) {
                        if (esMeta.hasStoredEnchant(Enchantment.MENDING)) {
                            event.setCancelled(true); // Cancel the trade if it offers a Mending book
                            return;
                        }
                    }
                }
            }
        }
    }
}

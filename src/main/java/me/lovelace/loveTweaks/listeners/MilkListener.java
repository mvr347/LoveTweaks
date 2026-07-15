package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class MilkListener implements Listener {

    private final LoveTweaks plugin;
    private final NamespacedKey purificationKey;

    public MilkListener(LoveTweaks plugin) {
        this.plugin = plugin;
        this.purificationKey = new NamespacedKey(plugin, "purification_potion");

        registerPurificationRecipe();
    }

    private void registerPurificationRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "purification_potion");
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, createPurificationPotion());
        recipe.addIngredient(Material.GHAST_TEAR);
        recipe.addIngredient(Material.FERMENTED_SPIDER_EYE);
        recipe.addIngredient(Material.NETHER_WART);

        plugin.getServer().addRecipe(recipe);
    }

    /**
     * Создаёт зелье очищения — кастомный предмет, помеченный через PersistentDataContainer,
     * который снимает все активные эффекты при употреблении.
     */
    public ItemStack createPurificationPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta meta = potion.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Зелье очищения", NamedTextColor.AQUA));
            meta.lore(List.of(
                    Component.text("Снимает все активные эффекты", NamedTextColor.GRAY),
                    Component.text("Сварено с кровью Нижнего мира", NamedTextColor.DARK_GRAY)
            ));

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(purificationKey, PersistentDataType.BYTE, (byte) 1);

            potion.setItemMeta(meta);
        }
        return potion;
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item.getType() == Material.MILK_BUCKET) {
            if (plugin.getLoveTweaksConfig().isDisableMilk()) {
                event.setCancelled(true);
            }
            return;
        }

        if (item.getType() == Material.POTION && isPurificationPotion(item)) {
            // Ванильное употребление зелья не должно происходить - убираем предмет вручную
            // и обрабатываем эффект очищения сами
            event.setCancelled(true);

            consumeFromHand(player, item);
            giveEmptyBottle(player);
            clearAllEffects(player);

            player.sendMessage(Component.text("Вы ощущаете очищение...", NamedTextColor.AQUA));
        }
    }

    /**
     * Убирает зелье очищения из руки игрока (основной или дополнительной),
     * уменьшая количество предметов в стаке или очищая слот полностью.
     */
    private void consumeFromHand(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();

        ItemStack mainHand = inventory.getItemInMainHand();
        if (isPurificationPotion(mainHand)) {
            decreaseOrClear(inventory, mainHand, EquipmentSlot.HAND);
            return;
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (isPurificationPotion(offHand)) {
            decreaseOrClear(inventory, offHand, EquipmentSlot.OFF_HAND);
        }
    }

    private void decreaseOrClear(PlayerInventory inventory, ItemStack stack, EquipmentSlot slot) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            if (slot == EquipmentSlot.HAND) {
                inventory.setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                inventory.setItemInOffHand(new ItemStack(Material.AIR));
            }
        }
    }

    private void giveEmptyBottle(Player player) {
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        PlayerInventory inventory = player.getInventory();

        if (inventory.firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), bottle);
        } else {
            inventory.addItem(bottle);
        }
    }

    private void clearAllEffects(Player player) {
        // Копируем в новый список, чтобы избежать ConcurrentModificationException
        // при удалении эффектов во время итерации
        List<PotionEffect> activeEffects = new ArrayList<>(player.getActivePotionEffects());
        for (PotionEffect effect : activeEffects) {
            player.removePotionEffect(effect.getType());
        }
    }

    public boolean isPurificationPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(purificationKey, PersistentDataType.BYTE);
    }
}

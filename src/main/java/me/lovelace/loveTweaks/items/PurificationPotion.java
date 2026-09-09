package me.lovelace.loveTweaks.items;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.managers.PurificationPotionConfig;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import me.lovelace.loveTweaks.utils.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания и идентификации предмета "Зелье очищения".
 * Поддерживает кастомные текстуры ItemsAdder, CustomModelData и маркировку через PDC.
 */
public class PurificationPotion {

    private static NamespacedKey purificationKey;
    private static NamespacedKey legacyKey;
    private static LoveTweaks plugin;

    public static void init(LoveTweaks pluginInstance) {
        plugin = pluginInstance;
        purificationKey = new NamespacedKey(pluginInstance, "purification_potion");
        legacyKey = NamespacedKey.fromString("lovetweaks:purification_potion");
    }

    public static NamespacedKey getPurificationKey() {
        return purificationKey;
    }

    /**
     * Создаёт ItemStack зелья очищения с метаданными из конфигурации.
     */
    public static ItemStack create() {
        if (plugin == null) {
            return new ItemStack(Material.POTION);
        }
        PurificationPotionConfig config = plugin.getLoveTweaksConfig().getPurificationPotionConfig();

        ItemStack potion = null;
        if (config.itemsadderItem() != null && !config.itemsadderItem().isBlank()) {
            potion = ItemsAdderHook.createCustomItem(config.itemsadderItem());
        }
        if (potion == null) {
            potion = new ItemStack(config.material() != null ? config.material() : Material.POTION);
        }

        ItemMeta meta = potion.getItemMeta();
        if (meta != null) {
            meta.displayName(GuiItemUtil.colorize(config.name())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : config.lore()) {
                lore.add(GuiItemUtil.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            if (config.customModelData() > 0) {
                meta.setCustomModelData(config.customModelData());
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (purificationKey != null) {
                pdc.set(purificationKey, PersistentDataType.BYTE, (byte) 1);
            }

            potion.setItemMeta(meta);
        }

        return potion;
    }

    /**
     * Проверяет, является ли предмет зельем очищения (по PDC, ItemsAdder ID или DisplayName).
     */
    public static boolean isPurificationPotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (purificationKey != null && pdc.has(purificationKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (legacyKey != null && pdc.has(legacyKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (plugin != null) {
            PurificationPotionConfig cfg = plugin.getLoveTweaksConfig().getPurificationPotionConfig();
            if (cfg != null) {
                if (cfg.itemsadderItem() != null && !cfg.itemsadderItem().isBlank()
                        && ItemsAdderHook.isCustomItem(item, cfg.itemsadderItem())) {
                    return true;
                }
                // Запасная проверка по названию
                if (meta.hasDisplayName()) {
                    String expectedName = GuiItemUtil.stripColor(cfg.name());
                    String actualName = GuiItemUtil.stripColor(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
                    if (!expectedName.isEmpty() && expectedName.equalsIgnoreCase(actualName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

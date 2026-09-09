package me.lovelace.loveTweaks.items;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания и идентификации предмета "Свиток телепортации".
 * Идентификация — через PersistentDataContainer, чтобы не завязываться на название предмета.
 */
public class TeleportScroll {

    // Ключ и плагин хранятся как static, создаются один раз при инициализации менеджера
    private static NamespacedKey scrollKey;
    private static LoveTweaks plugin;

    private static NamespacedKey legacyKey1;
    private static NamespacedKey legacyKey2;

    public static void init(LoveTweaks pluginInstance) {
        plugin = pluginInstance;
        scrollKey = new NamespacedKey(pluginInstance, "teleport_scroll");
        legacyKey1 = NamespacedKey.fromString("loveteleportscroll:teleport_scroll");
        legacyKey2 = NamespacedKey.fromString("lovetweaks:teleport_scroll");
    }

    public static NamespacedKey getScrollKey() {
        return scrollKey;
    }

    /**
     * Создаёт ItemStack свитка телепортации с кастомными метаданными и поддержкой ItemsAdder.
     * maxStackSize = 1 выставляется через ItemMeta (Paper 1.20.5+ API).
     */
    public static ItemStack create() {
        var config = plugin.getLoveTweaksConfig().getTeleportScrollConfig();

        ItemStack scroll = null;
        if (config.itemsadderItem() != null && !config.itemsadderItem().isBlank()) {
            scroll = me.lovelace.loveTweaks.utils.ItemsAdderHook.createCustomItem(config.itemsadderItem());
        }
        if (scroll == null) {
            scroll = new ItemStack(config.material());
        }

        ItemMeta meta = scroll.getItemMeta();
        if (meta != null) {
            // Без курсива — Adventure по умолчанию добавляет курсив к custom именам
            meta.displayName(GuiItemUtil.colorize(config.itemName())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : config.itemLore()) {
                lore.add(GuiItemUtil.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // CustomModelData если задан
            if (config.customModelData() > 0) {
                meta.setCustomModelData(config.customModelData());
            }

            // Запрещаем стакаться — максимальный размер стака = 1
            meta.setMaxStackSize(1);

            // Помечаем предмет через PDC для надёжной идентификации
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(scrollKey, PersistentDataType.BYTE, (byte) 1);

            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /**
     * Проверяет, является ли предмет свитком телепортации по метке в PDC, ItemsAdder или имени.
     */
    public static boolean isScroll(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (scrollKey != null && pdc.has(scrollKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (legacyKey1 != null && pdc.has(legacyKey1, PersistentDataType.BYTE)) {
            return true;
        }
        if (legacyKey2 != null && pdc.has(legacyKey2, PersistentDataType.BYTE)) {
            return true;
        }
        if (plugin != null) {
            var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            if (cfg != null) {
                if (cfg.itemsadderItem() != null && !cfg.itemsadderItem().isBlank()
                        && me.lovelace.loveTweaks.utils.ItemsAdderHook.isCustomItem(item, cfg.itemsadderItem())) {
                    return true;
                }
                // Запасная проверка по названию предмета из конфигурации
                if (meta.hasDisplayName()) {
                    String expectedName = GuiItemUtil.stripColor(cfg.itemName());
                    String actualName = GuiItemUtil.stripColor(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
                    if (!expectedName.isEmpty() && expectedName.equalsIgnoreCase(actualName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

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
 * Фабрика для создания и идентификации предмета "Свиток телепортации к координатам".
 * ПКМ открывает ввод координат в чат, после чего запускается отсчёт и телепортация в мир world.
 */
public final class CoordinateTeleportScroll {

    private static NamespacedKey markerKey;
    private static NamespacedKey legacyKey1;
    private static NamespacedKey legacyKey2;
    private static LoveTweaks plugin;

    private CoordinateTeleportScroll() {}

    public static void init(LoveTweaks pluginInstance) {
        plugin = pluginInstance;
        markerKey = new NamespacedKey(pluginInstance, "coord_teleport_scroll");
        legacyKey1 = NamespacedKey.fromString("lovecoordinateteleportscroll:coord_teleport_scroll");
        legacyKey2 = NamespacedKey.fromString("lovetweaks:coord_teleport_scroll");
    }

    public static NamespacedKey getMarkerKey() { return markerKey; }

    /**
     * Создаёт ItemStack свитка телепортации по координатам с поддержкой ItemsAdder.
     */
    public static ItemStack create() {
        var config = plugin.getLoveTweaksConfig().getCoordTeleportScrollConfig();

        ItemStack scroll = null;
        if (config.getItemsadderItem() != null && !config.getItemsadderItem().isBlank()) {
            scroll = me.lovelace.loveTweaks.utils.ItemsAdderHook.createCustomItem(config.getItemsadderItem());
        }
        if (scroll == null) {
            scroll = new ItemStack(config.getItemMaterial());
        }

        ItemMeta meta = scroll.getItemMeta();
        if (meta != null) {
            // Без курсива — Adventure по умолчанию добавляет курсив к custom именам
            meta.displayName(GuiItemUtil.colorize(config.getItemName())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : config.getItemLore()) {
                lore.add(GuiItemUtil.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // CustomModelData если задан
            if (config.getCustomModelData() > 0) {
                meta.setCustomModelData(config.getCustomModelData());
            }

            // Запрещаем стакаться — максимальный размер стака = 1
            meta.setMaxStackSize(1);

            // Помечаем предмет через PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);

            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /** Проверяет, является ли предмет координатным свитком телепортации по метке в PDC, ItemsAdder или имени. */
    public static boolean isScroll(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (markerKey != null && pdc.has(markerKey, PersistentDataType.BYTE)) {
            return true;
        }
        if (legacyKey1 != null && pdc.has(legacyKey1, PersistentDataType.BYTE)) {
            return true;
        }
        if (legacyKey2 != null && pdc.has(legacyKey2, PersistentDataType.BYTE)) {
            return true;
        }
        if (plugin != null) {
            var cfg = plugin.getLoveTweaksConfig().getCoordTeleportScrollConfig();
            if (cfg != null) {
                if (cfg.getItemsadderItem() != null && !cfg.getItemsadderItem().isBlank()
                        && me.lovelace.loveTweaks.utils.ItemsAdderHook.isCustomItem(item, cfg.getItemsadderItem())) {
                    return true;
                }
                // Запасная проверка по названию предмета из конфигурации
                if (meta.hasDisplayName()) {
                    String expectedName = GuiItemUtil.stripColor(cfg.getItemName());
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

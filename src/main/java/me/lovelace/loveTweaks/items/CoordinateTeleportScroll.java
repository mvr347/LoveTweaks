package me.lovelace.loveTweaks.items;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания и идентификации предмета "Свиток телепортации к координатам".
 * В отличие от {@link TeleportScroll} (телепорт к другому игроку), этот свиток ведёт к
 * заранее заданной в конфиге точке — какой именно, хранится прямо в предмете через PDC,
 * так что один и тот же класс обслуживает произвольное число точек ({@code coord-teleport-scroll.scrolls}).
 */
public final class CoordinateTeleportScroll {

    private static NamespacedKey markerKey;
    private static NamespacedKey idKey;
    private static LoveTweaks plugin;

    private CoordinateTeleportScroll() {}

    public static void init(LoveTweaks pluginInstance) {
        plugin = pluginInstance;
        markerKey = new NamespacedKey(pluginInstance, "coord_teleport_scroll");
        idKey = new NamespacedKey(pluginInstance, "coord_teleport_scroll_id");
    }

    public static NamespacedKey getMarkerKey() { return markerKey; }
    public static NamespacedKey getIdKey() { return idKey; }

    /**
     * Создаёт ItemStack свитка для конкретной точки из {@code coord-teleport-scroll.scrolls}.
     * @return готовый предмет, или {@code null} если такого id нет в конфиге.
     */
    public static ItemStack create(String scrollId) {
        CoordinateScrollDefinition def = plugin.getLoveTweaksConfig().getCoordTeleportScrollConfig().getScroll(scrollId);
        if (def == null) {
            return null;
        }

        ItemStack scroll = new ItemStack(def.material());
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            // Без курсива — Adventure по умолчанию добавляет курсив к custom именам
            meta.displayName(GuiItemUtil.colorize(def.itemName())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : def.itemLore()) {
                lore.add(GuiItemUtil.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // Запрещаем стакаться — максимальный размер стака = 1
            meta.setMaxStackSize(1);

            // Помечаем предмет через PDC: маркер типа + id конкретной точки назначения
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(idKey, PersistentDataType.STRING, scrollId);

            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /** Проверяет, является ли предмет координатным свитком телепортации по метке в PDC. */
    public static boolean isScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    /** Id точки назначения ({@code coord-teleport-scroll.scrolls.<id>}), или {@code null} если это не такой свиток. */
    public static String getScrollId(ItemStack item) {
        if (!isScroll(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
}

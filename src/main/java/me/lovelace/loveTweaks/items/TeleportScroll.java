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

    public static void init(LoveTweaks pluginInstance) {
        plugin = pluginInstance;
        scrollKey = new NamespacedKey(pluginInstance, "teleport_scroll");
    }

    public static NamespacedKey getScrollKey() {
        return scrollKey;
    }

    /**
     * Создаёт ItemStack свитка телепортации с кастомными метаданными.
     * maxStackSize = 1 выставляется через ItemMeta (Paper 1.20.5+ API).
     */
    public static ItemStack create() {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            var config = plugin.getLoveTweaksConfig().getTeleportScrollConfig();

            // Без курсива — Adventure по умолчанию добавляет курсив к custom именам
            meta.displayName(GuiItemUtil.colorize(config.itemName())
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            for (String line : config.itemLore()) {
                lore.add(GuiItemUtil.colorize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

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
     * Проверяет, является ли предмет свитком телепортации по метке в PDC.
     */
    public static boolean isScroll(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(scrollKey, PersistentDataType.BYTE);
    }
}

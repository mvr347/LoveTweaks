package me.lovelace.loveTweaks.items;

import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Фабрика для создания и идентификации предмета "Свиток телепортации".
 * Идентификация — через PersistentDataContainer, чтобы не завязываться на название предмета.
 */
public class TeleportScroll {

    // Ключ хранится как static final, создаётся один раз при инициализации менеджера
    private static NamespacedKey scrollKey;

    public static void init(LoveTweaks plugin) {
        scrollKey = new NamespacedKey(plugin, "teleport_scroll");
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
            // Название с золотым цветом, без курсива (Adventure по умолчанию добавляет курсив к custom именам)
            meta.displayName(
                    Component.text("✦ Свиток телепортации ✦", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, false)
            );

            meta.lore(List.of(
                    Component.text("Позволяет телепортироваться к игроку", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("ПКМ", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(" — использовать свиток", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false))
            ));

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

package me.lovelace.loveTweaks.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Опциональная интеграция с ItemsAdder через reflection (без жёсткой зависимости при компиляции/запуске).
 * Позволяет использовать кастомные предметы, текстуры и модели ItemsAdder для свитков и других предметов.
 */
public final class ItemsAdderHook {

    private static Boolean available = null;
    private static Method getInstanceMethod;
    private static Method getItemStackMethod;
    private static Method byItemStackMethod;
    private static Method getNamespacedIDMethod;

    private ItemsAdderHook() {}

    public static boolean isAvailable() {
        if (available == null) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                    Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
                    getItemStackMethod = customStackClass.getMethod("getItemStack");
                    byItemStackMethod = customStackClass.getMethod("byItemStack", ItemStack.class);
                    getNamespacedIDMethod = customStackClass.getMethod("getNamespacedID");
                    available = true;
                } else {
                    available = false;
                }
            } catch (Throwable t) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Создаёт копию ItemStack из ItemsAdder по ID (например: "lovetweaks:teleport_scroll" или "custom_scroll").
     * Возвращает null, если ItemsAdder не установлен или предмет не найден в реестре ItemsAdder.
     */
    public static ItemStack createCustomItem(String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank()) return null;
        if (!isAvailable()) return null;
        try {
            Object customStack = getInstanceMethod.invoke(null, namespacedId);
            if (customStack != null) {
                Object item = getItemStackMethod.invoke(customStack);
                if (item instanceof ItemStack is) {
                    return is.clone();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Возвращает NamespacedID предмета ItemsAdder (например: "lovetweaks:teleport_scroll"), если предмет создан ItemsAdder.
     */
    public static String getNamespacedId(ItemStack item) {
        if (item == null || !isAvailable()) return null;
        try {
            Object customStack = byItemStackMethod.invoke(null, item);
            if (customStack != null) {
                Object id = getNamespacedIDMethod.invoke(customStack);
                return id != null ? id.toString() : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Проверяет, является ли ItemStack предметом ItemsAdder с указанным ID.
     */
    public static boolean isCustomItem(ItemStack item, String namespacedId) {
        if (item == null || namespacedId == null || namespacedId.isBlank()) return false;
        String id = getNamespacedId(item);
        if (id != null) {
            return id.equalsIgnoreCase(namespacedId) || id.endsWith(":" + namespacedId);
        }
        return false;
    }

    /**
     * Сбрасывает кэш доступности ItemsAdder (например, при перезагрузке плагинов).
     */
    public static void resetCache() {
        available = null;
    }
}

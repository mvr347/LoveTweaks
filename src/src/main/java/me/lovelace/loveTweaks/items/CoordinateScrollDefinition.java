package me.lovelace.loveTweaks.items;

import org.bukkit.Material;

import java.util.List;

/**
 * Одна точка назначения координатного свитка телепортации, настраивается в
 * {@code coord-teleport-scroll.scrolls.<id>}. Сами лимиты безопасности (мин/макс Y,
 * граница мира) хранятся отдельно в {@code CoordinateTeleportScrollConfig} — они общие
 * для всех точек, а не часть конкретного определения.
 */
public record CoordinateScrollDefinition(
        String id,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String permission,
        Material material,
        String itemName,
        List<String> itemLore
) {
    /** Нужно ли проверять право доступа перед использованием (пустой узел = доступно всем). */
    public boolean hasPermissionNode() {
        return permission != null && !permission.isBlank();
    }
}

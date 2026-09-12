package me.lovelace.loveTweaks.items;

import org.bukkit.Material;

import java.util.List;

/**
 * Один предмет из стартового набора, выдаваемого при первом заходе на сервер.
 * Настраивается в config.yml (секция {@code first-join.items}).
 */
public record FirstJoinItem(
        Material material,
        int amount,
        boolean teleportScroll,
        String displayName,
        List<String> lore
) {
}

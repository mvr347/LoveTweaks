package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.entity.Player;

/**
 * Answers whether ReviveMe is installed and, best-effort, whether a given player is currently
 * Downed. The actual Downed/Revived hooking (which drives {@link DownedInventoryManager}) is a
 * side effect of construction in {@link ReflectiveReviveMeDetector} — this interface only exists
 * so the rest of the module never references that reflective implementation directly.
 */
interface ReviveMeDetector {

    boolean isAvailable();

    boolean isDowned(Player player);
}

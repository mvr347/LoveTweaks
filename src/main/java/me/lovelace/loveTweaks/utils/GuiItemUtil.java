package me.lovelace.loveTweaks.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared base64-skull/legacy-color item builder for the project's Java-rendered GUIs
 * (mirrors the private helpers in {@code ScoreboardGUI} so every menu — scoreboard, Herald —
 * builds buttons the same way instead of each duplicating its own copy).
 */
public final class GuiItemUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private GuiItemUtil() {}

    public static ItemStack buildItem(String materialStr, String displayName, List<String> lore) {
        ItemStack item = resolveItem(materialStr);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(colorize(displayName));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(colorize(line));
        }
        meta.lore(loreComponents);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static Component colorize(String text) {
        return LEGACY.deserialize(text.replace('&', '§'));
    }

    private static ItemStack resolveItem(String materialStr) {
        if (materialStr != null && materialStr.startsWith("basehead-")) {
            ItemStack skull = createBase64Skull(materialStr.substring(9));
            if (skull != null) return skull;
        }
        Material mat = materialStr != null ? Material.matchMaterial(materialStr.toUpperCase()) : null;
        return new ItemStack(mat != null ? mat : Material.STONE);
    }

    private static ItemStack createBase64Skull(String base64) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) return skull;

            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes()));
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            return null;
        }
    }
}

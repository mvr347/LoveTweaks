package me.lovelace.loveTweaks.enchantments;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CustomEnchantManager {

    private final LoveTweaks plugin;

    public CustomEnchantManager(@NotNull LoveTweaks plugin) {
        this.plugin = plugin;
    }

    public int getLevel(@Nullable ItemStack item, @NotNull CustomEnchantType type) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = type.getKey(plugin);
        Integer level = pdc.get(key, PersistentDataType.INTEGER);
        if (level == null) {
            NamespacedKey altKey = new NamespacedKey(plugin, "enchant_" + type.getId());
            level = pdc.get(altKey, PersistentDataType.INTEGER);
        }
        return level != null ? level : 0;
    }

    @NotNull
    public Map<CustomEnchantType, Integer> getEnchantments(@Nullable ItemStack item) {
        Map<CustomEnchantType, Integer> map = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) {
            return map;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (CustomEnchantType type : CustomEnchantType.values()) {
            NamespacedKey key = type.getKey(plugin);
            Integer level = pdc.get(key, PersistentDataType.INTEGER);
            if (level == null) {
                NamespacedKey altKey = new NamespacedKey(plugin, "enchant_" + type.getId());
                level = pdc.get(altKey, PersistentDataType.INTEGER);
            }
            if (level != null && level > 0) {
                map.put(type, level);
            }
        }
        return map;
    }

    public boolean hasAnyCustomEnchant(@Nullable ItemStack item) {
        return !getEnchantments(item).isEmpty();
    }

    public void applyEnchantment(@NotNull ItemStack item, @NotNull CustomEnchantType type, int level) {
        // hasItemMeta() is false for pristine items on modern Paper, so it must not gate writing
        if (item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int clampedLevel = Math.max(1, Math.min(level, type.getMaxLevel()));
        pdc.set(type.getKey(plugin), PersistentDataType.INTEGER, clampedLevel);

        // Handle mutual exclusions
        if (type.getMutuallyExclusiveWith() != null) {
            CustomEnchantType mutual = CustomEnchantType.fromId(type.getMutuallyExclusiveWith());
            if (mutual != null) {
                pdc.remove(mutual.getKey(plugin));
                pdc.remove(new NamespacedKey(plugin, "enchant_" + mutual.getId()));
            }
        }

        updateItemLore(meta, isBook(item));
        item.setItemMeta(meta);
    }

    public void removeEnchantment(@NotNull ItemStack item, @NotNull CustomEnchantType type) {
        if (item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(type.getKey(plugin));
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "enchant_" + type.getId()));
        updateItemLore(meta, isBook(item));
        item.setItemMeta(meta);
    }

    public void removeAllCustomEnchantments(@NotNull ItemStack item) {
        if (item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (CustomEnchantType type : CustomEnchantType.values()) {
            pdc.remove(type.getKey(plugin));
            pdc.remove(new NamespacedKey(plugin, "enchant_" + type.getId()));
        }
        updateItemLore(meta, isBook(item));
        item.setItemMeta(meta);
    }

    public void updateItemLore(@NotNull ItemMeta meta) {
        updateItemLore(meta, false);
    }

    public void updateItemLore(@NotNull ItemMeta meta, boolean isBook) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> currentLore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();

        // Remove all old custom enchant lore lines
        currentLore.removeIf(line -> {
            String stripped = ChatColor.stripColor(line).trim();
            if (stripped.startsWith("Применяется на:") || (isBook && stripped.isEmpty())) {
                return true;
            }
            if (stripped.startsWith("▪")) {
                return true;
            }
            for (CustomEnchantType type : CustomEnchantType.values()) {
                if (stripped.startsWith(type.getDisplayName())) {
                    return true;
                }
            }
            return false;
        });

        if (isBook) {
            // Match real vanilla enchanted books: just colored name + roman-numeral level
            // per enchant, nothing else. No description bullet, no "Применяется на:" footer -
            // vanilla never shows either on a book, and since these are fake PDC-based
            // enchants the client can't tell the difference, so the fake has to look identical.
            List<String> newLore = new ArrayList<>();
            for (CustomEnchantType type : CustomEnchantType.values()) {
                Integer level = pdc.get(type.getKey(plugin), PersistentDataType.INTEGER);
                if (level == null) {
                    level = pdc.get(new NamespacedKey(plugin, "enchant_" + type.getId()), PersistentDataType.INTEGER);
                }
                if (level != null && level > 0) {
                    newLore.add(type.getFormattedName(level));
                }
            }
            if (!newLore.isEmpty()) {
                newLore.addAll(currentLore);
                meta.setLore(newLore);
            } else {
                meta.setLore(currentLore.isEmpty() ? null : currentLore);
            }
            return;
        }

        // Collect current active custom enchantments for equipment
        List<String> enchantLines = new ArrayList<>();
        for (CustomEnchantType type : CustomEnchantType.values()) {
            Integer level = pdc.get(type.getKey(plugin), PersistentDataType.INTEGER);
            if (level == null) {
                level = pdc.get(new NamespacedKey(plugin, "enchant_" + type.getId()), PersistentDataType.INTEGER);
            }
            if (level != null && level > 0) {
                enchantLines.add(type.getFormattedName(level));
            }
        }

        if (!enchantLines.isEmpty()) {
            enchantLines.addAll(currentLore);
            meta.setLore(enchantLines);
        } else {
            meta.setLore(currentLore.isEmpty() ? null : currentLore);
        }
    }

    @NotNull
    public ItemStack createEnchantedBook(@NotNull CustomEnchantType type, int level) {
        int clampedLevel = Math.max(1, Math.min(level, type.getMaxLevel()));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        meta.getPersistentDataContainer().set(type.getKey(plugin), PersistentDataType.INTEGER, clampedLevel);
        // Раньше здесь был meta.setDisplayName("§eЗачарованная книга") - жёстко заданное имя
        // ПОДМЕНЯЛО настоящее ванильное "Зачарованная книга" (переводится под клиент игрока и
        // авто-красится по редкости предмета) на нашу английскую по коду, но всегда русскую и
        // всегда жёлтую строку. Не трогаем displayName вовсе - ваниль сама выберет и текст, и
        // цвет ровно как у настоящей зачарованной книги; свою метку оставляем только в лоре ниже.
        meta.setEnchantmentGlintOverride(true);
        updateItemLore(meta, true);
        book.setItemMeta(meta);
        return book;
    }

    public boolean isApplicable(@NotNull CustomEnchantType type, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (isBook(item)) {
            return true;
        }
        if (type.getTarget() == CustomEnchantType.Target.DAGGER) {
            return isDagger(item);
        } else if (type.getTarget() == CustomEnchantType.Target.LEGGINGS) {
            return isLeggings(item);
        }
        return false;
    }

    public boolean isBook(@Nullable ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return mat == Material.ENCHANTED_BOOK || mat == Material.BOOK;
    }

    public boolean isLeggings(@Nullable ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_LEGGINGS");
    }

    public boolean isDagger(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.hasItemMeta()) {
            // Check if config allows on all swords
            return plugin.getLoveTweaksConfig().isCustomEnchantsAllowOnAllSwords() && isSword(item);
        }
        ItemMeta meta = item.getItemMeta();

        // 1. Check display name
        if (meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT);
            if (stripped.contains("кинжал") || stripped.contains("dagger")) {
                return true;
            }
        }

        // 2. Check PDC keys
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            String fullKey = key.toString().toLowerCase(Locale.ROOT);
            if (fullKey.contains("dagger")) {
                return true;
            }
        }

        // 3. Check lore
        if (meta.hasLore()) {
            for (String line : Objects.requireNonNull(meta.getLore())) {
                String stripped = ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
                if (stripped.contains("кинжал") || stripped.contains("dagger")) {
                    return true;
                }
            }
        }

        // 4. Fallback if config allows on all swords
        return plugin.getLoveTweaksConfig().isCustomEnchantsAllowOnAllSwords() && isSword(item);
    }

    public boolean isSword(@Nullable ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD");
    }

    public boolean areMutuallyExclusive(@NotNull CustomEnchantType a, @NotNull CustomEnchantType b) {
        if (a.getMutuallyExclusiveWith() != null && a.getMutuallyExclusiveWith().equalsIgnoreCase(b.getId())) {
            return true;
        }
        if (b.getMutuallyExclusiveWith() != null && b.getMutuallyExclusiveWith().equalsIgnoreCase(a.getId())) {
            return true;
        }
        return false;
    }
}

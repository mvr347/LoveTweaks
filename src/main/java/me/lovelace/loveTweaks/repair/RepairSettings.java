package me.lovelace.loveTweaks.repair;

import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveTweaks.enchantments.CustomEnchantType;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

/** Prices are in LoveEconomy's smallest unit, before its global price index. */
public record RepairSettings(ConfigurationSection config) {
    public record Quote(long price, long seconds) {}

    public boolean enabled() { return config.getBoolean("blacksmith.enabled", true); }
    public String npcKey() { return config.getString("blacksmith.npc-key", ""); }
    public String text(String key) { return config.getString("blacksmith.text." + key, key); }

    public static boolean accepts(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1) return false;
        return item.getItemMeta() instanceof Damageable meta && !meta.isUnbreakable()
                && meta.getDamage() > 0 && maximumDamage(item, meta) > 0;
    }

    private static int maximumDamage(ItemStack item, Damageable meta) {
        return meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
    }

    public Quote quote(Iterable<ItemStack> items, LoveEconomy economy) {
        long total = 0, seconds = 0;
        for (ItemStack item : items) {
            if (!accepts(item)) continue;
            Damageable meta = (Damageable) item.getItemMeta();
            double wear = Math.min(1.0, (double) meta.getDamage() / maximumDamage(item, meta));
            long levels = meta.getEnchants().values().stream().mapToLong(Integer::longValue).sum();
            for (CustomEnchantType type : CustomEnchantType.values()) {
                Integer level = meta.getPersistentDataContainer().get(new NamespacedKey("lovetweaks", type.getId()), PersistentDataType.INTEGER);
                if (level == null) level = meta.getPersistentDataContainer().get(new NamespacedKey("lovetweaks", "enchant_" + type.getId()), PersistentDataType.INTEGER);
                if (level != null && level > 0) levels += level;
            }
            String material = item.getType().name();
            long base = config.getLong("blacksmith.prices.materials." + material, -1);
            if (base < 0) {
                base = config.getLong("blacksmith.prices.default", 100);
                var tiers = config.getConfigurationSection("blacksmith.prices.tiers");
                if (tiers != null) for (String prefix : tiers.getKeys(false)) {
                    if (material.startsWith(prefix + "_")) { base = tiers.getLong(prefix); break; }
                }
            }
            double factor = 1.0 + levels * nonnegative("blacksmith.prices.enchantment-level-multiplier", 0.15);
            double raw = Math.max(nonnegative("blacksmith.prices.minimum", 5), Math.max(0, base) * wear * factor);
            if (!Double.isFinite(raw) || raw > 1_000_000_000_000L) throw new IllegalArgumentException("Цена ремонта слишком велика");
            long scaled = economy.scaled(Math.max(1, (long) Math.ceil(raw)));
            if (scaled < 1 || scaled > 1_000_000_000_000L) throw new IllegalArgumentException("Неверный индекс цен LoveEconomy");
            total = Math.addExact(total, scaled);
            double time = Math.max(1, nonnegative("blacksmith.time.minimum-seconds", 5));
            time = Math.max(time, nonnegative("blacksmith.time.seconds-per-item", 30) * wear
                    + levels * nonnegative("blacksmith.time.seconds-per-enchantment-level", 2));
            if (!Double.isFinite(time) || time > 604_800) throw new IllegalArgumentException("Время ремонта слишком велико");
            seconds = Math.addExact(seconds, (long) Math.ceil(time));
        }
        return new Quote(total, seconds);
    }

    private double nonnegative(String key, double fallback) {
        double value = config.getDouble(key, fallback);
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Неверная настройка " + key);
        return value;
    }

    /** Touch only durability: names, lore, vanilla/custom enchants and item components survive. */
    public static ItemStack repaired(ItemStack original) {
        ItemStack result = original.clone();
        Damageable meta = (Damageable) result.getItemMeta();
        meta.setDamage(0);
        result.setItemMeta(meta);
        return result;
    }
}

package me.lovelace.loveTweaks.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Конфигурация предмета "Зелье очищения".
 * Поддерживает кастомную текстуру ItemsAdder, кулдаун, кастомный рецепт,
 * список не сбрасываемых эффектов (например: BAD_OMEN) и настраиваемые сообщения.
 */
public class PurificationPotionConfig {

    private Material material = Material.POTION;
    private String itemsadderItem = "";
    private int customModelData = 0;
    private String name = "&bЗелье очищения";
    private List<String> lore = List.of(
            "&7Снимает все активные эффекты",
            "&8Кулдаун: &f30 сек."
    );
    private int cooldownSeconds = 30;
    private String cooldownMessage = "&cВы сможете использовать зелье очищения через <seconds> сек.";
    private String purificationMessage = "&bВы ощущаете очищение...";
    private List<String> ignoredEffects = new ArrayList<>(List.of(
            "BAD_OMEN",
            "RAID_OMEN",
            "TRIAL_OMEN"
    ));
    private final Set<String> normalizedIgnoredEffects = new HashSet<>();
    private final Set<PotionEffectType> ignoredEffectTypes = new HashSet<>();
    private boolean recipeEnabled = true;
    private List<String> recipeIngredients = List.of(
            "GHAST_TEAR",
            "FERMENTED_SPIDER_EYE",
            "NETHER_WART"
    );

    public PurificationPotionConfig() {
        rebuildIgnoredCache();
    }

    public void load(ConfigurationSection section) {
        if (section == null) {
            rebuildIgnoredCache();
            return;
        }

        String matStr = section.getString("material", "POTION");
        Material mat = Material.matchMaterial(matStr.toUpperCase(Locale.ROOT));
        this.material = mat != null ? mat : Material.POTION;

        this.itemsadderItem = section.getString("itemsadder-item", "");
        this.customModelData = section.getInt("custom-model-data", 0);
        this.name = section.getString("name", "&bЗелье очищения");

        if (section.isList("lore")) {
            this.lore = section.getStringList("lore");
        }

        this.cooldownSeconds = Math.max(0, section.getInt("cooldown-seconds", 30));
        this.cooldownMessage = section.getString("cooldown-message", "&cВы сможете использовать зелье очищения через <seconds> сек.");
        this.purificationMessage = section.getString("purification-message", "&bВы ощущаете очищение...");

        if (section.isList("ignored-effects")) {
            this.ignoredEffects = new ArrayList<>(section.getStringList("ignored-effects"));
        } else if (section.isList("unremovable-effects")) {
            this.ignoredEffects = new ArrayList<>(section.getStringList("unremovable-effects"));
        } else if (section.isList("protected-effects")) {
            this.ignoredEffects = new ArrayList<>(section.getStringList("protected-effects"));
        }

        rebuildIgnoredCache();

        ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
        if (recipeSection != null) {
            this.recipeEnabled = recipeSection.getBoolean("enabled", true);
            if (recipeSection.isList("ingredients")) {
                this.recipeIngredients = recipeSection.getStringList("ingredients");
            }
        } else {
            this.recipeEnabled = section.getBoolean("recipe-enabled", true);
        }
    }

    private void rebuildIgnoredCache() {
        normalizedIgnoredEffects.clear();
        ignoredEffectTypes.clear();

        for (String raw : ignoredEffects) {
            if (raw == null || raw.isBlank()) continue;
            String trimmed = raw.trim();
            String clean = trimmed.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("-", "_");
            normalizedIgnoredEffects.add(clean);
            normalizedIgnoredEffects.add(trimmed.toUpperCase(Locale.ROOT));

            try {
                PotionEffectType byName = PotionEffectType.getByName(trimmed.toUpperCase(Locale.ROOT));
                if (byName != null) {
                    ignoredEffectTypes.add(byName);
                }
            } catch (Throwable ignored) {}

            try {
                NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
                if (key != null) {
                    PotionEffectType byKey = PotionEffectType.getByKey(key);
                    if (byKey != null) {
                        ignoredEffectTypes.add(byKey);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public boolean isEffectIgnored(PotionEffect effect) {
        return effect != null && isEffectIgnored(effect.getType());
    }

    public boolean isEffectIgnored(String effectName) {
        if (effectName == null || effectName.isBlank()) return false;
        String clean = effectName.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("-", "_");
        return normalizedIgnoredEffects.contains(clean)
                || normalizedIgnoredEffects.contains(effectName.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isEffectIgnored(PotionEffectType type) {
        if (type == null) return false;
        if (ignoredEffectTypes.contains(type)) return true;

        String name = type.getName();
        if (name != null && isEffectIgnored(name)) {
            return true;
        }

        try {
            NamespacedKey key = type.getKey();
            if (key != null && (isEffectIgnored(key.getKey()) || isEffectIgnored(key.toString()))) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public Material material() { return material; }
    public String itemsadderItem() { return itemsadderItem; }
    public int customModelData() { return customModelData; }
    public String name() { return name; }
    public List<String> lore() { return lore; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public String cooldownMessage() { return cooldownMessage; }
    public String purificationMessage() { return purificationMessage; }
    public List<String> ignoredEffects() { return ignoredEffects; }
    public boolean isRecipeEnabled() { return recipeEnabled; }
    public List<String> recipeIngredients() { return recipeIngredients; }
}

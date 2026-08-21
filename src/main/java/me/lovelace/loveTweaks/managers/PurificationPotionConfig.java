package me.lovelace.loveTweaks.managers;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация предмета "Зелье очищения".
 * Поддерживает кастомную текстуру ItemsAdder, кулдаун, кастомный рецепт и настраиваемые сообщения.
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
    private boolean recipeEnabled = true;
    private List<String> recipeIngredients = List.of(
            "GHAST_TEAR",
            "FERMENTED_SPIDER_EYE",
            "NETHER_WART"
    );

    public void load(ConfigurationSection section) {
        if (section == null) return;

        String matStr = section.getString("material", "POTION");
        Material mat = Material.matchMaterial(matStr.toUpperCase());
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

    public Material material() { return material; }
    public String itemsadderItem() { return itemsadderItem; }
    public int customModelData() { return customModelData; }
    public String name() { return name; }
    public List<String> lore() { return lore; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public String cooldownMessage() { return cooldownMessage; }
    public String purificationMessage() { return purificationMessage; }
    public boolean isRecipeEnabled() { return recipeEnabled; }
    public List<String> recipeIngredients() { return recipeIngredients; }
}

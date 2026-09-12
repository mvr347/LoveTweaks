package me.lovelace.loveTweaks.managers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Item name/lore and all action-bar text for the teleport scroll, loaded from {@code teleport-scroll.*}. */
public final class TeleportScrollConfig {

    private String itemsadderItem = "";
    private org.bukkit.Material material = org.bukkit.Material.PAPER;
    private int customModelData = 0;
    private String itemName = "&6✦ Свиток телепортации ✦";
    private List<String> itemLore = List.of();
    private final Map<String, String> messages = new HashMap<>();

    public void load(ConfigurationSection section) {
        if (section == null) return;

        itemsadderItem = section.getString("item.itemsadder-item", section.getString("item.itemsadder", ""));
        String matStr = section.getString("item.material");
        if (matStr != null) {
            org.bukkit.Material matched = org.bukkit.Material.matchMaterial(matStr.toUpperCase());
            if (matched != null) material = matched;
        }
        customModelData = section.getInt("item.custom-model-data", section.getInt("item.cmd", 0));

        itemName = section.getString("item.name", itemName);
        List<String> lore = section.getStringList("item.lore");
        itemLore = lore.isEmpty() ? new ArrayList<>() : lore;

        messages.clear();
        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }
    }

    public String itemsadderItem() { return itemsadderItem; }
    public org.bukkit.Material material() { return material; }
    public int customModelData() { return customModelData; }
    public String itemName() { return itemName; }
    public List<String> itemLore() { return itemLore; }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the key itself if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}

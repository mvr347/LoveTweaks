package me.lovelace.loveTweaks.managers;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Item name/lore and all action-bar text for the teleport scroll, loaded from {@code teleport-scroll.*}. */
public final class TeleportScrollConfig {

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("prompt", "&6✦ Введите ник игрока в чат &e(10 секунд)"),
            Map.entry("timeout", "&c✗ Время вышло!"),
            Map.entry("player-not-found", "&c✗ Игрок &e<player>&c не найден или не в сети!"),
            Map.entry("cannot-target-self", "&c✗ Нельзя телепортироваться к себе!"),
            Map.entry("target-not-in-world", "&c✗ Игрок &e<player>&c находится в другом измерении! Телепортация возможна только в мир world."),
            Map.entry("target-afk", "&c✗ Игрок &e<player>&c АФК!"),
            Map.entry("you-afk", "&c✗ &e<player>&c не смог телепортироваться к вам: вы слишком долго стояли без движения."),
            Map.entry("countdown-initiator", "&6✦ Телепортация через &e<seconds> сек&6... Не двигайтесь!"),
            Map.entry("countdown-target", "&6✦ К вам телепортируется &e<player>&6... Не двигайтесь!"),
            Map.entry("cancelled-initiator", "&c✗ Телепортация отменена! <reason>"),
            Map.entry("cancelled-target", "&c✗ Телепортация отменена!"),
            Map.entry("success-initiator", "&a✦ Телепортация выполнена!"),
            Map.entry("success-target", "&a✦ К вам телепортировался &e<player>&a!"),
            Map.entry("reason-pvp-self", "(вы в PvP)"),
            Map.entry("reason-pvp-target", "(игрок <player> в PvP)"),
            Map.entry("reason-invisible-self", "(вы в невидимости)"),
            Map.entry("reason-invisible-target", "(игрок <player> в невидимости)"),
            Map.entry("reason-moved-self", "(вы двигались)"),
            Map.entry("reason-moved-target", "(игрок <player> двигался)"),
            Map.entry("reason-dropped", "(свиток убран из руки)"),
            Map.entry("reason-wrong-world", "(игрок <player> в другом мире)")
    );

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

    /**
     * Raw message template for {@code key} (with its own {@code <placeholder>} tags).
     * Falls back to built-in Russian defaults so missing keys never show as raw "you-afk" etc.
     */
    public String message(String key) {
        String fromConfig = messages.get(key);
        if (fromConfig != null && !fromConfig.isEmpty()) {
            return fromConfig;
        }
        return DEFAULT_MESSAGES.getOrDefault(key, key);
    }
}

package me.lovelace.loveTweaks.managers;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Конфигурация свитка телепортации по координатам ({@code coord-teleport-scroll}).
 */
public class CoordinateTeleportScrollConfig {

    private boolean enabled = true;
    private String targetWorld = "world";
    private int castTimeSeconds = 5;
    private int cooldownSeconds = 30;

    private String itemsadderItem = "";
    private Material itemMaterial = Material.PAPER;
    private int customModelData = 0;
    private String itemName = "&b✦ Свиток телепортации: Координаты ✦";
    private List<String> itemLore = List.of(
            "&7Позволяет телепортироваться по координатам",
            "&7в мире &fworld",
            "",
            "&eПКМ&7 — использовать свиток"
    );

    private double minY = -64;
    private double maxY = 320;
    private boolean respectWorldBorder = true;
    private double maxDistanceFromSpawn = 0;
    private List<String> allowedWorlds = List.of("world");

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("prompt", "&b✦ Введите координаты в чат &e(X Z или X Y Z)&b, или \"отмена\" (10 секунд)"),
            Map.entry("timeout", "&c✗ Время ожидания ввода координат истекло!"),
            Map.entry("chat-cancelled", "&7Ввод координат отменён."),
            Map.entry("invalid-format", "&c✗ Неверный формат! Введите: <X> <Z> (например: 100 200) или <X> <Y> <Z>"),
            Map.entry("world-not-allowed", "&c✗ Телепортация по координатам разрешена только в мире world!"),
            Map.entry("destination-blocked", "&c✗ Точка назначения недоступна (вне границы мира или недопустимая высота)!"),
            Map.entry("destination-water", "&c✗ Нельзя телепортироваться на воду! Выберите координаты на суше."),
            Map.entry("destination-underground", "&c✗ Нельзя телепортироваться под землю или в шахту! Телепортация разрешена только на поверхность суши."),
            Map.entry("destination-unsafe", "&c✗ Точка назначения опасна (лава, огонь или препятствия)!"),
            Map.entry("no-permission", "&cУ вас нет прав на этот свиток!"),
            Map.entry("on-cooldown", "&cЭтот свиток будет готов через &e<seconds> сек&c!"),
            Map.entry("countdown", "&6✦ Телепортация через &e<seconds> сек&6... Не двигайтесь!"),
            Map.entry("cancelled", "&c✗ Телепортация отменена! <reason>"),
            Map.entry("success", "&a✦ Телепортация по координатам выполнена!"),
            Map.entry("reason-moved", "(вы двигались)"),
            Map.entry("reason-invisible", "(вы в невидимости)"),
            Map.entry("reason-pvp", "(вы в PvP)"),
            Map.entry("reason-dropped", "(свиток убран из руки)")
    );

    private final Map<String, String> messages = new HashMap<>();

    public void load(ConfigurationSection section, Logger logger) {
        messages.clear();
        messages.putAll(DEFAULT_MESSAGES);

        if (section == null) {
            return;
        }

        enabled = section.getBoolean("enabled", true);
        targetWorld = section.getString("target-world", "world");
        castTimeSeconds = Math.max(0, section.getInt("cast-time-seconds", 5));
        cooldownSeconds = Math.max(0, section.getInt("cooldown-seconds", 30));

        itemsadderItem = section.getString("item.itemsadder-item", section.getString("item.itemsadder", ""));
        Object matObj = section.get("item.material");
        if (matObj != null) {
            Material matched = Material.matchMaterial(String.valueOf(matObj).toUpperCase());
            if (matched != null) itemMaterial = matched;
        }
        customModelData = section.getInt("item.custom-model-data", section.getInt("item.cmd", 0));
        itemName = section.getString("item.name", itemName);
        List<String> lore = section.getStringList("item.lore");
        if (!lore.isEmpty()) {
            itemLore = new ArrayList<>(lore);
        }

        ConfigurationSection limits = section.getConfigurationSection("limits");
        if (limits != null) {
            minY = limits.getDouble("min-y", -64);
            maxY = limits.getDouble("max-y", 320);
            respectWorldBorder = limits.getBoolean("respect-world-border", true);
            maxDistanceFromSpawn = Math.max(0, limits.getDouble("max-distance-from-spawn", 0));
            allowedWorlds = limits.getStringList("allowed-worlds");
            if (allowedWorlds.isEmpty()) {
                allowedWorlds = List.of(targetWorld);
            }
        } else {
            minY = -64;
            maxY = 320;
            respectWorldBorder = true;
            maxDistanceFromSpawn = 0;
            allowedWorlds = List.of(targetWorld);
        }
        if (minY > maxY) {
            double tmp = minY;
            minY = maxY;
            maxY = tmp;
            if (logger != null) {
                logger.warning("[coord-teleport-scroll] limits.min-y больше limits.max-y — значения переставлены местами.");
            }
        }

        ConfigurationSection messagesSection = section.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, DEFAULT_MESSAGES.getOrDefault(key, "")));
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getTargetWorld() { return targetWorld; }
    public int getCastTimeSeconds() { return castTimeSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public String getItemsadderItem() { return itemsadderItem; }
    public Material getItemMaterial() { return itemMaterial; }
    public int getCustomModelData() { return customModelData; }
    public String getItemName() { return itemName; }
    public List<String> getItemLore() { return itemLore; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }
    public boolean isRespectWorldBorder() { return respectWorldBorder; }
    public double getMaxDistanceFromSpawn() { return maxDistanceFromSpawn; }
    public List<String> getAllowedWorlds() { return allowedWorlds != null ? allowedWorlds : List.of(targetWorld); }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the default if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
    }
}

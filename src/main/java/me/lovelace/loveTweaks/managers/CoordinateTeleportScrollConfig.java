package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.items.CoordinateScrollDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Глобальные лимиты безопасности, сообщения и список точек назначения координатного свитка
 * телепортации, загружается из {@code coord-teleport-scroll.*}. Сами точки — {@link CoordinateScrollDefinition}
 * — лежат в {@code coord-teleport-scroll.scrolls.<id>} и хранят только координаты/предмет, лимиты
 * общие для всех точек и живут здесь же.
 */
public final class CoordinateTeleportScrollConfig {

    private boolean enabled = true;
    private int castTimeSeconds = 5;
    private int cooldownSeconds = 30;

    // ─── Лимиты безопасности (применяются к точке НАЗНАЧЕНИЯ каждого свитка) ──────────────
    private double minY = -64;
    private double maxY = 320;
    private boolean respectWorldBorder = true;
    private double maxDistanceFromSpawn = 0;
    private List<String> allowedWorlds = List.of();

    private final Map<String, String> messages = new HashMap<>();
    // LinkedHashMap — сохраняем порядок объявления из config.yml (пригодится для /lovetweaksadmin givecoordscroll без аргумента и т.п.)
    private final Map<String, CoordinateScrollDefinition> scrolls = new LinkedHashMap<>();

    public void load(ConfigurationSection section, Logger logger) {
        messages.clear();
        scrolls.clear();

        if (section == null) {
            return;
        }

        enabled = section.getBoolean("enabled", true);
        castTimeSeconds = Math.max(0, section.getInt("cast-time-seconds", 5));
        cooldownSeconds = Math.max(0, section.getInt("cooldown-seconds", 30));

        ConfigurationSection limits = section.getConfigurationSection("limits");
        if (limits != null) {
            minY = limits.getDouble("min-y", -64);
            maxY = limits.getDouble("max-y", 320);
            respectWorldBorder = limits.getBoolean("respect-world-border", true);
            maxDistanceFromSpawn = Math.max(0, limits.getDouble("max-distance-from-spawn", 0));
            allowedWorlds = limits.getStringList("allowed-worlds");
        } else {
            minY = -64;
            maxY = 320;
            respectWorldBorder = true;
            maxDistanceFromSpawn = 0;
            allowedWorlds = List.of();
        }
        if (minY > maxY) {
            // Защита от опечатки в конфиге: иначе диапазон высоты пуст и все свитки перестанут работать молча.
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
                messages.put(key, messagesSection.getString(key, ""));
            }
        }

        ConfigurationSection scrollsSection = section.getConfigurationSection("scrolls");
        if (scrollsSection != null) {
            for (String id : scrollsSection.getKeys(false)) {
                ConfigurationSection s = scrollsSection.getConfigurationSection(id);
                if (s == null) continue;

                String world = s.getString("world", "world");
                double x = s.getDouble("x", 0);
                double y = s.getDouble("y", 100);
                double z = s.getDouble("z", 0);
                float yaw = (float) s.getDouble("yaw", 0);
                float pitch = (float) s.getDouble("pitch", 0);
                String permission = s.getString("permission", "");

                Material material = Material.PAPER;
                Object materialObj = s.get("item.material");
                if (materialObj != null) {
                    Material matched = Material.matchMaterial(String.valueOf(materialObj).toUpperCase());
                    if (matched != null) material = matched;
                }
                String itemName = s.getString("item.name", "&6✦ Свиток телепортации ✦");
                List<String> itemLore = s.getStringList("item.lore");

                CoordinateScrollDefinition def = new CoordinateScrollDefinition(
                        id, world, x, y, z, yaw, pitch, permission,
                        material, itemName, itemLore.isEmpty() ? List.of() : itemLore
                );

                // Предупреждаем в консоли сразу при загрузке, если координаты уже сейчас нарушают
                // лимиты — не блокируем загрузку конфига, но админ узнаёт об опечатке из лога,
                // а не когда игрок в игре молча получит "телепортация недоступна".
                if (logger != null && (y < minY || y > maxY)) {
                    logger.warning("[coord-teleport-scroll] Свиток '" + id + "': y=" + y
                            + " вне допустимого диапазона [" + minY + ", " + maxY + "] — телепортация будет отклоняться в игре.");
                }

                scrolls.put(id, def);
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getCastTimeSeconds() { return castTimeSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }
    public boolean isRespectWorldBorder() { return respectWorldBorder; }
    public double getMaxDistanceFromSpawn() { return maxDistanceFromSpawn; }
    public List<String> getAllowedWorlds() { return allowedWorlds != null ? allowedWorlds : List.of(); }
    public Map<String, CoordinateScrollDefinition> getScrolls() { return Collections.unmodifiableMap(scrolls); }
    public CoordinateScrollDefinition getScroll(String id) { return scrolls.get(id); }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the key itself if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, key);
    }
}

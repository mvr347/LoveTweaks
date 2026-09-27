package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.herald.HeraldGuiConfig;
import me.lovelace.loveTweaks.items.FirstJoinItem;
import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollConfig;
import me.lovelace.loveTweaks.managers.TeleportScrollConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoveTweaksConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    // Общие тексты команд, не привязанные к конкретной функции
    private final Map<String, String> messages = new HashMap<>();

    // Ender Chests
    private boolean enderChestsAsNormalChests;

    // Enchantments
    private boolean disableMending;
    private boolean disableAllEnchantments;

    // Custom Enchantments
    private boolean customEnchantmentsEnabled;
    private boolean customEnchantsAllowOnAllSwords;

    // Hunger
    private float extraExhaustionPerSecond;
    private float saturationMultiplier;

    // Milk & Purification Potion
    private boolean disableMilk;
    private final me.lovelace.loveTweaks.managers.PurificationPotionConfig purificationPotionConfig = new me.lovelace.loveTweaks.managers.PurificationPotionConfig();
    private String purificationPotionName;
    private List<String> purificationPotionLore = List.of();
    private String purificationMessage;

    // Зельеварение и варочные стойки
    private boolean disableBrewing;
    private boolean disableBrewingStandCraft;
    private boolean disableBrewingStandWorld;
    private boolean disablePotionNaturalDrops;
    private String disabledBrewingMessage;

    // Потеря/поломка предметов при выбрасывании
    private boolean itemDropLossEnabled;
    private double itemDropLossChance;
    private double itemDropCompleteStackLossChance;
    private double itemDropBreakChance;
    private double itemDropTerriblePolitenessMultiplier;
    private double itemDropGoodStandingMultiplier;
    private String itemDropBreakMessage;
    private String itemDropDamageMessage;
    private String itemDropLoseMessage;
    private String itemDropStackPartialLoseMessage;

    // Автовозрождение
    private boolean autoRespawnEnabled;
    private int autoRespawnDelayTicks;

    // Свиток телепортации (к игроку)
    private final TeleportScrollConfig teleportScrollConfig = new TeleportScrollConfig();

    // Свиток телепортации к координатам
    private final CoordinateTeleportScrollConfig coordTeleportScrollConfig = new CoordinateTeleportScrollConfig();

    // Королевский Глашатай
    private boolean heraldEnabled;
    private int heraldNpcId;
    private String heraldNpcName;
    private int heraldSlots;
    private int heraldMinDurationMinutes;
    private int heraldMaxDurationMinutes;
    private int heraldDurationStepMinutes;
    private int heraldMaxMessageLength;
    private long heraldMinCost;
    private long heraldMaxCost;
    private final HeraldGuiConfig heraldGuiConfig = new HeraldGuiConfig();

    // Стартовый набор при первом заходе на сервер
    private boolean firstJoinEnabled;
    private final List<FirstJoinItem> firstJoinItems = new ArrayList<>();

    // Ванильные ограничения (Жалобы, Достижения, Друзья)
    private boolean disableAdvancements;
    private boolean disableChatReports;
    private boolean disableFriendsAndReports;
    private String disabledVanillaCommandMessage;
    private List<String> blockedVanillaCommands = new ArrayList<>();

    // Scoreboard (delegated to ScoreboardConfig)
    private final ScoreboardConfig scoreboardConfig = new ScoreboardConfig();

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("no-permission", "&cНедостаточно прав."),
            Map.entry("players-only", "&cЭта команда доступна только игрокам."),
            Map.entry("help-header", "&8========== &bLoveTweaks Admin &8=========="),
            Map.entry("help-reload", "&b/lovetweaksadmin reload &7— Перезагрузить конфигурацию"),
            Map.entry("help-givescroll", "&b/lovetweaksadmin givescroll <игрок> &7— Выдать свиток телепортации к игроку"),
            Map.entry("help-givecoordscroll", "&b/lovetweaksadmin givecoordscroll <игрок> &7— Выдать свиток телепортации по координатам"),
            Map.entry("help-givepurification", "&b/lovetweaksadmin givepurification <игрок> [кол-во] &7— Выдать зелье очищения"),
            Map.entry("help-herald", "&b/lovetweaksadmin herald <bind|unbind|clear|open> &7— Управление Королевским Глашатаем"),
            Map.entry("help-footer", "&8========================================="),
            Map.entry("usage-givescroll", "&eИспользование: &f/lovetweaksadmin givescroll <игрок>"),
            Map.entry("usage-givecoordscroll", "&eИспользование: &f/lovetweaksadmin givecoordscroll <игрок>"),
            Map.entry("usage-givepurification", "&eИспользование: &f/lovetweaksadmin givepurification <игрок> [количество]"),
            Map.entry("reload-done", "&aLoveTweaks конфиг перезагружен!"),
            Map.entry("player-not-found", "&cИгрок &e<player>&c не найден или не в сети."),
            Map.entry("scroll-given-sender", "&aСвиток телепортации выдан игроку &e<player>&a."),
            Map.entry("scroll-given-target", "&6Вы получили &eСвиток телепортации&6!"),
            Map.entry("purification-given-sender", "&aЗелье очищения (<amount> шт.) выдано игроку &e<player>&a."),
            Map.entry("purification-given-target", "&bВы получили &eЗелье очищения&b!"),
            Map.entry("coord-scroll-not-found", "&cСвиток с id &e<id>&c не найден в coord-teleport-scroll.scrolls."),
            Map.entry("citizens-missing", "&cCitizens не установлен или не включён."),
            Map.entry("npc-not-selected", "&cВыберите NPC (&e/npc select&c) или посмотрите на него и повторите команду."),
            Map.entry("herald-npc-bound", "&aNPC Глашатая привязан: &e<npc>"),
            Map.entry("herald-npc-unbound", "&aNPC Глашатая отвязан."),
            Map.entry("herald-cleared", "&aГолос Королевства сброшен."),
            Map.entry("herald-opened", "&aМеню Глашатая открыто для &e<player>&a.")
    );

    public LoveTweaksConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        migrateDefaults();

        loadMessages(config);

        enderChestsAsNormalChests = config.getBoolean("ender-chests.as-normal-chests", false);
        disableMending = config.getBoolean("enchantments.disable-mending", false);
        disableAllEnchantments = config.getBoolean("enchantments.disable-all-enchantments", false);
        customEnchantmentsEnabled = config.getBoolean("custom-enchantments.enabled", true);
        customEnchantsAllowOnAllSwords = config.getBoolean("custom-enchantments.allow-on-all-swords", false);
        extraExhaustionPerSecond = (float) config.getDouble("hunger.extra-exhaustion-per-second", 0.25);
        saturationMultiplier = (float) config.getDouble("hunger.saturation-multiplier", 0.5);
        disableMilk = config.getBoolean("milk.disable-milk", true);

        // Загрузка настроек зелья очищения
        purificationPotionConfig.load(config.getConfigurationSection("milk.purification-potion"));
        purificationPotionName = purificationPotionConfig.name();
        purificationPotionLore = purificationPotionConfig.lore();
        purificationMessage = purificationPotionConfig.purificationMessage();

        // Зельеварение и варочные стойки
        disableBrewing = config.getBoolean("brewing.disable-brewing", true);
        disableBrewingStandCraft = config.getBoolean("brewing.disable-brewing-stand-craft", true);
        disableBrewingStandWorld = config.getBoolean("brewing.disable-brewing-stand-world", true);
        disablePotionNaturalDrops = config.getBoolean("brewing.disable-potion-natural-drops", true);
        disabledBrewingMessage = config.getString("brewing.disabled-message", "&cЗельеварение и варочные стойки отключены на этом сервере.");

        itemDropLossEnabled = config.getBoolean("item-drop-loss.enabled", false);
        itemDropLossChance = config.getDouble("item-drop-loss.lose-chance", 0.22);
        itemDropCompleteStackLossChance = config.getDouble("item-drop-loss.complete-stack-loss-chance", 0.01);
        itemDropBreakChance = config.getDouble("item-drop-loss.break-chance", 0.33);
        itemDropBreakMessage = config.getString("item-drop-loss.break-message", "&cВаш предмет сломался при падении!");
        itemDropDamageMessage = config.getString("item-drop-loss.damage-message", "&eВаш предмет повредился при падении!");
        itemDropLoseMessage = config.getString("item-drop-loss.lose-message", "&7Ваш предмет потерялся при падении!");
        itemDropStackPartialLoseMessage = config.getString("item-drop-loss.stack-partial-lose-message", "&7Часть предметов (<count> шт.) потерялась при падении!");
        itemDropTerriblePolitenessMultiplier = config.getDouble("item-drop-loss.terrible-politeness-multiplier", 1.6);
        itemDropGoodStandingMultiplier = config.getDouble("item-drop-loss.good-standing-multiplier", 0.5);

        autoRespawnEnabled = config.getBoolean("auto-respawn.enabled", true);
        autoRespawnDelayTicks = Math.max(1, config.getInt("auto-respawn.delay-ticks", 5));

        teleportScrollConfig.load(config.getConfigurationSection("teleport-scroll"));
        coordTeleportScrollConfig.load(config.getConfigurationSection("coord-teleport-scroll"), plugin.getLogger());

        heraldEnabled = config.getBoolean("herald.enabled", true);
        heraldNpcId = config.getInt("herald.npc-id", -1);
        heraldNpcName = config.getString("herald.npc-name", "");
        heraldSlots = Math.max(1, config.getInt("herald.slots", 3));
        heraldMinDurationMinutes = Math.max(1, config.getInt("herald.min-duration-minutes", 10));
        heraldMaxDurationMinutes = Math.max(heraldMinDurationMinutes, config.getInt("herald.max-duration-minutes", 60));
        heraldDurationStepMinutes = Math.max(1, config.getInt("herald.duration-step-minutes", 10));
        heraldMaxMessageLength = Math.max(1, config.getInt("herald.max-message-length", 50));
        heraldMinCost = config.getLong("herald.min-cost", 50);
        heraldMaxCost = Math.max(heraldMinCost, config.getLong("herald.max-cost", 300));
        heraldGuiConfig.load(config.getConfigurationSection("herald.gui"));

        loadFirstJoinItems(config);

        disableAdvancements = config.getBoolean("vanilla.disable-advancements", true);
        disableChatReports = config.getBoolean("vanilla.disable-chat-reports", true);
        disableFriendsAndReports = config.getBoolean("vanilla.disable-friends-and-reports", true);
        disabledVanillaCommandMessage = config.getString("vanilla.disabled-command-message", "&cВанильные жалобы, достижения и система друзей отключены на этом сервере.");
        blockedVanillaCommands = config.getStringList("vanilla.blocked-commands");
        if (blockedVanillaCommands.isEmpty()) {
            blockedVanillaCommands = List.of("report", "chatreport", "wreport", "friend", "friends", "f", "advancement", "advancements");
        }

        scoreboardConfig.load(config);
    }

    /**
     * Выполняет глубокую миграцию конфига: подгружает дефолты из jar и при необходимости
     * дополняет отсутствующие секции (messages, coord-teleport-scroll, herald) в live-конфиге.
     */
    private void migrateDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            config.setDefaults(defaults);

            boolean changed = false;

            // Миграция секции messages
            ConfigurationSection defMessages = defaults.getConfigurationSection("messages");
            if (defMessages != null) {
                if (!config.isConfigurationSection("messages")) {
                    config.createSection("messages");
                    changed = true;
                }
                ConfigurationSection liveMessages = config.getConfigurationSection("messages");
                if (liveMessages != null) {
                    for (String key : defMessages.getKeys(false)) {
                        if (!liveMessages.contains(key)) {
                            liveMessages.set(key, defMessages.get(key));
                            changed = true;
                        }
                    }
                }
            }

            // Миграция секции coord-teleport-scroll
            if (!config.isConfigurationSection("coord-teleport-scroll")) {
                config.set("coord-teleport-scroll", defaults.get("coord-teleport-scroll"));
                changed = true;
            } else {
                ConfigurationSection defCoord = defaults.getConfigurationSection("coord-teleport-scroll");
                ConfigurationSection liveCoord = config.getConfigurationSection("coord-teleport-scroll");
                if (defCoord != null && liveCoord != null) {
                    for (String key : defCoord.getKeys(true)) {
                        if (!liveCoord.contains(key)) {
                            liveCoord.set(key, defCoord.get(key));
                            changed = true;
                        }
                    }
                }
            }

            // Миграция секции herald
            if (!config.isConfigurationSection("herald")) {
                config.set("herald", defaults.get("herald"));
                changed = true;
            } else {
                ConfigurationSection defHerald = defaults.getConfigurationSection("herald");
                ConfigurationSection liveHerald = config.getConfigurationSection("herald");
                if (defHerald != null && liveHerald != null) {
                    for (String key : defHerald.getKeys(true)) {
                        if (!liveHerald.contains(key)) {
                            liveHerald.set(key, defHerald.get(key));
                            changed = true;
                        }
                    }
                }
            }

            // Миграция секции brewing
            if (!config.isConfigurationSection("brewing")) {
                config.set("brewing", defaults.get("brewing"));
                changed = true;
            } else {
                ConfigurationSection defBrewing = defaults.getConfigurationSection("brewing");
                ConfigurationSection liveBrewing = config.getConfigurationSection("brewing");
                if (defBrewing != null && liveBrewing != null) {
                    for (String key : defBrewing.getKeys(true)) {
                        if (!liveBrewing.contains(key)) {
                            liveBrewing.set(key, defBrewing.get(key));
                            changed = true;
                        }
                    }
                }
            }

            // Миграция секции revive-inventory-fix
            if (!config.isConfigurationSection("revive-inventory-fix")) {
                config.set("revive-inventory-fix", defaults.get("revive-inventory-fix"));
                changed = true;
            } else {
                ConfigurationSection defRevive = defaults.getConfigurationSection("revive-inventory-fix");
                ConfigurationSection liveRevive = config.getConfigurationSection("revive-inventory-fix");
                if (defRevive != null && liveRevive != null) {
                    for (String key : defRevive.getKeys(true)) {
                        if (!liveRevive.contains(key)) {
                            liveRevive.set(key, defRevive.get(key));
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                plugin.saveConfig();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось выполнить миграцию config.yml: " + e.getMessage());
        }
    }

    private void loadMessages(FileConfiguration config) {
        messages.clear();
        messages.putAll(DEFAULT_MESSAGES);
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            messages.put(key, section.getString(key, DEFAULT_MESSAGES.getOrDefault(key, "")));
        }
    }

    private void loadFirstJoinItems(FileConfiguration config) {
        firstJoinEnabled = config.getBoolean("first-join.enabled", false);

        firstJoinItems.clear();
        for (Map<?, ?> raw : config.getMapList("first-join.items")) {
            Object materialObj = raw.get("material");
            Material material = materialObj != null
                    ? Material.matchMaterial(String.valueOf(materialObj).toUpperCase())
                    : null;
            if (material == null) material = Material.PAPER;

            int amount = 1;
            if (raw.get("amount") instanceof Number n) amount = Math.max(1, n.intValue());

            boolean teleportScroll = Boolean.TRUE.equals(raw.get("teleport-scroll"));

            String displayName = raw.get("display-name") != null ? String.valueOf(raw.get("display-name")) : null;

            List<String> lore = new ArrayList<>();
            if (raw.get("lore") instanceof List<?> loreList) {
                for (Object line : loreList) lore.add(String.valueOf(line));
            }

            firstJoinItems.add(new FirstJoinItem(material, amount, teleportScroll, displayName, lore));
        }
    }

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the default if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, key));
    }

    public boolean isEnderChestsAsNormalChests() { return enderChestsAsNormalChests; }
    public boolean isDisableMending() { return disableMending; }
    public boolean isDisableAllEnchantments() { return disableAllEnchantments; }
    public float getExtraExhaustionPerSecond() { return extraExhaustionPerSecond; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isDisableMilk() { return disableMilk; }
    public me.lovelace.loveTweaks.managers.PurificationPotionConfig getPurificationPotionConfig() { return purificationPotionConfig; }
    public String getPurificationPotionName() { return purificationPotionName; }
    public List<String> getPurificationPotionLore() { return purificationPotionLore; }
    public String getPurificationMessage() { return purificationMessage; }
    public boolean isItemDropLossEnabled() { return itemDropLossEnabled; }
    public double getItemDropLossChance() { return itemDropLossChance; }
    public double getItemDropCompleteStackLossChance() { return itemDropCompleteStackLossChance; }
    public double getItemDropBreakChance() { return itemDropBreakChance; }
    public double getItemDropTerriblePolitenessMultiplier() { return itemDropTerriblePolitenessMultiplier; }
    public double getItemDropGoodStandingMultiplier() { return itemDropGoodStandingMultiplier; }
    public String getItemDropBreakMessage() { return itemDropBreakMessage; }
    public String getItemDropDamageMessage() { return itemDropDamageMessage; }
    public String getItemDropLoseMessage() { return itemDropLoseMessage; }
    public String getItemDropStackPartialLoseMessage() { return itemDropStackPartialLoseMessage; }
    public boolean isAutoRespawnEnabled() { return autoRespawnEnabled; }
    public int getAutoRespawnDelayTicks() { return autoRespawnDelayTicks; }
    public TeleportScrollConfig getTeleportScrollConfig() { return teleportScrollConfig; }
    public CoordinateTeleportScrollConfig getCoordTeleportScrollConfig() { return coordTeleportScrollConfig; }

    public boolean isHeraldEnabled() { return heraldEnabled; }
    public int getHeraldNpcId() { return heraldNpcId; }
    public String getHeraldNpcName() { return heraldNpcName; }
    public int getHeraldSlots() { return heraldSlots; }
    public int getHeraldMinDurationMinutes() { return heraldMinDurationMinutes; }
    public int getHeraldMaxDurationMinutes() { return heraldMaxDurationMinutes; }
    public int getHeraldDurationStepMinutes() { return heraldDurationStepMinutes; }
    public int getHeraldMaxMessageLength() { return heraldMaxMessageLength; }
    public long getHeraldMinCost() { return heraldMinCost; }
    public long getHeraldMaxCost() { return heraldMaxCost; }
    public HeraldGuiConfig getHeraldGuiConfig() { return heraldGuiConfig; }

    public void setHeraldNpc(int npcId, String npcName) {
        this.heraldNpcId = npcId;
        this.heraldNpcName = npcName == null ? "" : npcName;
        config.set("herald.npc-id", this.heraldNpcId);
        config.set("herald.npc-name", this.heraldNpcName);
        plugin.saveConfig();
    }

    public boolean isFirstJoinEnabled() { return firstJoinEnabled; }
    public List<FirstJoinItem> getFirstJoinItems() { return firstJoinItems; }
    public boolean isDisableAdvancements() { return disableAdvancements; }
    public boolean isDisableChatReports() { return disableChatReports; }
    public boolean isDisableFriendsAndReports() { return disableFriendsAndReports; }
    public String getDisabledVanillaCommandMessage() { return disabledVanillaCommandMessage; }
    public List<String> getBlockedVanillaCommands() { return blockedVanillaCommands; }
    public boolean isDisableBrewing() { return disableBrewing; }
    public boolean isDisableBrewingStandCraft() { return disableBrewingStandCraft; }
    public boolean isDisableBrewingStandWorld() { return disableBrewingStandWorld; }
    public boolean isDisablePotionNaturalDrops() { return disablePotionNaturalDrops; }
    public String getDisabledBrewingMessage() { return disabledBrewingMessage; }
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
    public boolean isCustomEnchantmentsEnabled() { return customEnchantmentsEnabled; }
    public boolean isCustomEnchantsAllowOnAllSwords() { return customEnchantsAllowOnAllSwords; }
}

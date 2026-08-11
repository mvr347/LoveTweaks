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

    // Hunger
    private float extraExhaustionPerSecond;
    private float saturationMultiplier;

    // Milk
    private boolean disableMilk;
    private String purificationPotionName;
    private List<String> purificationPotionLore = List.of();
    private String purificationMessage;

    // Потеря/поломка предметов при выбрасывании
    private boolean itemDropLossEnabled;
    private double itemDropLossChance;
    private double itemDropBreakChance;
    private String itemDropBreakMessage;
    private String itemDropLoseMessage;

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

    // Scoreboard (delegated to ScoreboardConfig)
    private final ScoreboardConfig scoreboardConfig = new ScoreboardConfig();

    public LoveTweaksConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        migrateCoordTeleportScrollSection();

        loadMessages(config);

        enderChestsAsNormalChests = config.getBoolean("ender-chests.as-normal-chests", false);
        disableMending = config.getBoolean("enchantments.disable-mending", false);
        disableAllEnchantments = config.getBoolean("enchantments.disable-all-enchantments", false);
        extraExhaustionPerSecond = (float) config.getDouble("hunger.extra-exhaustion-per-second", 0.25);
        saturationMultiplier = (float) config.getDouble("hunger.saturation-multiplier", 0.5);
        disableMilk = config.getBoolean("milk.disable-milk", true);
        purificationPotionName = config.getString("milk.purification-potion.name", "&bЗелье очищения");
        purificationPotionLore = config.getStringList("milk.purification-potion.lore");
        purificationMessage = config.getString("milk.purification-message", "&bВы ощущаете очищение...");

        itemDropLossEnabled = config.getBoolean("item-drop-loss.enabled", false);
        itemDropLossChance = config.getDouble("item-drop-loss.lose-chance", 0.22);
        itemDropBreakChance = config.getDouble("item-drop-loss.break-chance", 0.33);
        itemDropBreakMessage = config.getString("item-drop-loss.break-message", "&cВаш предмет сломался при падении!");
        itemDropLoseMessage = config.getString("item-drop-loss.lose-message", "&7Ваш предмет потерялся при падении!");

        teleportScrollConfig.load(config.getConfigurationSection("teleport-scroll"));
        coordTeleportScrollConfig.load(config.getConfigurationSection("coord-teleport-scroll"), plugin.getLogger());

        heraldEnabled = config.getBoolean("herald.enabled", false);
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

        scoreboardConfig.load(config);
    }

    /**
     * {@code saveDefaultConfig()} only writes {@code config.yml} when the file doesn't exist yet.
     * On a server where LoveTweaks was already installed before the coordinate-teleport-scroll
     * feature was added, the live config.yml on disk simply has no {@code coord-teleport-scroll}
     * section (or no {@code .scrolls} sub-key) — so {@link CoordinateTeleportScrollConfig#getScroll}
     * always returns {@code null} and {@code /lovetweaksadmin givecoordscroll} fails as
     * "not found" for every id, even valid ones. Merge just this section's bundled defaults
     * into the live config once, without touching anything else the admin has customized.
     */
    private void migrateCoordTeleportScrollSection() {
        boolean hasSection = config.isConfigurationSection("coord-teleport-scroll");
        boolean hasScrolls = hasSection && config.isConfigurationSection("coord-teleport-scroll.scrolls");
        if (hasScrolls) return;

        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection defaultSection = defaults.getConfigurationSection("coord-teleport-scroll");
            if (defaultSection == null) return;

            if (!hasSection) {
                config.set("coord-teleport-scroll", defaultSection);
            } else {
                config.set("coord-teleport-scroll.scrolls", defaultSection.getConfigurationSection("scrolls"));
            }
            plugin.saveConfig();
            plugin.getLogger().warning("[coord-teleport-scroll] Секция отсутствовала в config.yml (плагин обновлён со старой версии) "
                    + "— добавлены значения по умолчанию, включая точку 'spawn'. Проверьте/настройте её под свой сервер.");
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось смигрировать секцию coord-teleport-scroll: " + e.getMessage());
        }
    }

    private void loadMessages(FileConfiguration config) {
        messages.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            messages.put(key, section.getString(key, ""));
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

    /** Raw message template for {@code key} (with its own {@code <placeholder>} tags), or the key itself if unset. */
    public String message(String key) {
        return messages.getOrDefault(key, key);
    }

    public boolean isEnderChestsAsNormalChests() { return enderChestsAsNormalChests; }
    public boolean isDisableMending() { return disableMending; }
    public boolean isDisableAllEnchantments() { return disableAllEnchantments; }
    public float getExtraExhaustionPerSecond() { return extraExhaustionPerSecond; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isDisableMilk() { return disableMilk; }
    public String getPurificationPotionName() { return purificationPotionName; }
    public List<String> getPurificationPotionLore() { return purificationPotionLore; }
    public String getPurificationMessage() { return purificationMessage; }
    public boolean isItemDropLossEnabled() { return itemDropLossEnabled; }
    public double getItemDropLossChance() { return itemDropLossChance; }
    public double getItemDropBreakChance() { return itemDropBreakChance; }
    public String getItemDropBreakMessage() { return itemDropBreakMessage; }
    public String getItemDropLoseMessage() { return itemDropLoseMessage; }
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
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
}

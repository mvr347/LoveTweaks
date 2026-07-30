package me.lovelace.loveTweaks;

import me.lovelace.loveTweaks.items.FirstJoinItem;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LoveTweaksConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

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

    // Потеря/поломка предметов при выбрасывании
    private boolean itemDropLossEnabled;
    private double itemDropLossChance;
    private double itemDropBreakChance;

    // Королевский Глашатай
    private boolean heraldEnabled;
    private int heraldNpcId;
    private String heraldNpcName;
    private long heraldCost;
    private int heraldBroadcastIntervalHours;
    private String heraldGuiTitle;

    // Королевская Почта
    private boolean postEnabled;
    private int postNpcId;
    private String postNpcName;
    private long postCost;
    private int postFlightSeconds;
    private String postGuiTitle;

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

        enderChestsAsNormalChests = config.getBoolean("ender-chests.as-normal-chests", false);
        disableMending = config.getBoolean("enchantments.disable-mending", false);
        disableAllEnchantments = config.getBoolean("enchantments.disable-all-enchantments", false);
        extraExhaustionPerSecond = (float) config.getDouble("hunger.extra-exhaustion-per-second", 0.25);
        saturationMultiplier = (float) config.getDouble("hunger.saturation-multiplier", 0.5);
        disableMilk = config.getBoolean("milk.disable-milk", true);

        itemDropLossEnabled = config.getBoolean("item-drop-loss.enabled", false);
        itemDropLossChance = config.getDouble("item-drop-loss.lose-chance", 0.22);
        itemDropBreakChance = config.getDouble("item-drop-loss.break-chance", 0.33);

        heraldEnabled = config.getBoolean("herald.enabled", false);
        heraldNpcId = config.getInt("herald.npc-id", -1);
        heraldNpcName = config.getString("herald.npc-name", "");
        heraldCost = config.getLong("herald.cost", 500);
        heraldBroadcastIntervalHours = Math.max(1, config.getInt("herald.broadcast-interval-hours", 2));
        heraldGuiTitle = config.getString("herald.gui-title", "&6Королевский Глашатай");

        postEnabled = config.getBoolean("post.enabled", false);
        postNpcId = config.getInt("post.npc-id", -1);
        postNpcName = config.getString("post.npc-name", "");
        postCost = config.getLong("post.cost", 200);
        postFlightSeconds = Math.max(1, config.getInt("post.flight-seconds", 20));
        postGuiTitle = config.getString("post.gui-title", "&6Королевская Почта");

        loadFirstJoinItems(config);

        scoreboardConfig.load(config);
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

    public boolean isEnderChestsAsNormalChests() { return enderChestsAsNormalChests; }
    public boolean isDisableMending() { return disableMending; }
    public boolean isDisableAllEnchantments() { return disableAllEnchantments; }
    public float getExtraExhaustionPerSecond() { return extraExhaustionPerSecond; }
    public float getSaturationMultiplier() { return saturationMultiplier; }
    public boolean isDisableMilk() { return disableMilk; }
    public boolean isItemDropLossEnabled() { return itemDropLossEnabled; }
    public double getItemDropLossChance() { return itemDropLossChance; }
    public double getItemDropBreakChance() { return itemDropBreakChance; }

    public boolean isHeraldEnabled() { return heraldEnabled; }
    public int getHeraldNpcId() { return heraldNpcId; }
    public String getHeraldNpcName() { return heraldNpcName; }
    public long getHeraldCost() { return heraldCost; }
    public int getHeraldBroadcastIntervalHours() { return heraldBroadcastIntervalHours; }
    public String getHeraldGuiTitle() { return heraldGuiTitle; }

    public void setHeraldNpc(int npcId, String npcName) {
        this.heraldNpcId = npcId;
        this.heraldNpcName = npcName == null ? "" : npcName;
        config.set("herald.npc-id", this.heraldNpcId);
        config.set("herald.npc-name", this.heraldNpcName);
        plugin.saveConfig();
    }

    public boolean isPostEnabled() { return postEnabled; }
    public int getPostNpcId() { return postNpcId; }
    public String getPostNpcName() { return postNpcName; }
    public long getPostCost() { return postCost; }
    public int getPostFlightSeconds() { return postFlightSeconds; }
    public String getPostGuiTitle() { return postGuiTitle; }

    public void setPostNpc(int npcId, String npcName) {
        this.postNpcId = npcId;
        this.postNpcName = npcName == null ? "" : npcName;
        config.set("post.npc-id", this.postNpcId);
        config.set("post.npc-name", this.postNpcName);
        plugin.saveConfig();
    }

    public boolean isFirstJoinEnabled() { return firstJoinEnabled; }
    public List<FirstJoinItem> getFirstJoinItems() { return firstJoinItems; }
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
}

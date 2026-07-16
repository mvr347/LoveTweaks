package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ScoreboardConfig {

    private String title;
    private String fixedBottom;
    private int updateInterval;
    private int maxPlaceholders;
    private List<String> disabledWorlds;

    private final Map<String, ScoreboardPlaceholder> placeholders = new LinkedHashMap<>();
    private final List<String> placeholderOrder = new ArrayList<>();

    // GUI
    private String guiTitle;
    private String fillerMaterial;
    private String fillerName;
    private String profileMaterial;
    private String profileName;
    private List<String> profileLore;
    private String profileCommand;
    private String backMaterial;
    private String backName;
    private List<String> backLore;
    private String backCommand;
    private String closeMaterial;
    private String closeName;
    private List<String> closeLore;
    private String toggleOnMaterial;
    private String toggleOnName;
    private List<String> toggleOnLore;
    private String toggleOffMaterial;
    private String toggleOffName;
    private List<String> toggleOffLore;
    private String lockedMaterial;
    private String lockedName;

    public void load(FileConfiguration cfg) {
        ConfigurationSection sb = cfg.getConfigurationSection("scoreboard");
        if (sb == null) return;

        title = sb.getString("title", "&FVOIDCORE");
        fixedBottom = sb.getString("fixed-bottom", "  &c✌ &7V O I D C O R E &c✌   ");
        updateInterval = sb.getInt("update-interval", 20);
        maxPlaceholders = sb.getInt("max-placeholders", 8);
        disabledWorlds = sb.getStringList("disabled-worlds");

        placeholders.clear();
        placeholderOrder.clear();

        ConfigurationSection phSection = sb.getConfigurationSection("placeholders");
        if (phSection != null) {
            for (String id : phSection.getKeys(false)) {
                ConfigurationSection p = phSection.getConfigurationSection(id);
                if (p == null) continue;

                String conditionStr = p.getString("condition", "ALWAYS");
                ScoreboardPlaceholderCondition condition = ScoreboardPlaceholderCondition.valueOf(conditionStr);

                ScoreboardPlaceholder placeholder = new ScoreboardPlaceholder(
                        id,
                        p.getString("display-name", id),
                        p.getString("template", ""),
                        p.getString("icon", "PAPER"),
                        p.getStringList("lore"),
                        condition,
                        p.getInt("sort-group", 0)
                );
                placeholders.put(id, placeholder);
                placeholderOrder.add(id);
            }
        }

        ConfigurationSection gui = sb.getConfigurationSection("gui");
        if (gui == null) {
            setGuiDefaults();
            return;
        }

        guiTitle = gui.getString("title", "&8⚙ &lСкорборд");

        ConfigurationSection filler = gui.getConfigurationSection("filler");
        fillerMaterial = get(filler, "material", "GRAY_STAINED_GLASS_PANE");
        fillerName = get(filler, "display-name", " ");

        ConfigurationSection profile = gui.getConfigurationSection("profile-button");
        profileMaterial = get(profile, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MyNDJiZTA5MjI2YTNhZjlkYTM0YzZkMzA1YTEyMzA2M2FhZjQyMWFlZTAzZDk4M2MxYmY1MjE1YzQyMWU4In19fQ==");
        profileName = get(profile, "display-name", "&bПрофиль");
        profileLore = getLore(profile, List.of("", "&aЛКМ &7— открыть профиль"));
        profileCommand = profile != null ? profile.getString("command", "") : "";

        ConfigurationSection back = gui.getConfigurationSection("back-button");
        backMaterial = get(back, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==");
        backName = get(back, "display-name", "&7« Назад");
        backLore = getLore(back, List.of("", "&aЛКМ &7— вернуться в настройки"));
        backCommand = back != null ? back.getString("command", "") : "";

        ConfigurationSection close = gui.getConfigurationSection("close-button");
        closeMaterial = get(close, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==");
        closeName = get(close, "display-name", "&cЗакрыть");
        closeLore = getLore(close, List.of("", "&aЛКМ &7— закрыть меню"));

        ConfigurationSection togOn = gui.getConfigurationSection("toggle-on");
        toggleOnMaterial = get(togOn, "material", "LIME_DYE");
        toggleOnName = get(togOn, "display-name", "&aСкорборд");
        toggleOnLore = getLore(togOn, List.of("", "&7Скорборд включён.", "&cЛКМ &7— выключить"));

        ConfigurationSection togOff = gui.getConfigurationSection("toggle-off");
        toggleOffMaterial = get(togOff, "material", "GRAY_DYE");
        toggleOffName = get(togOff, "display-name", "&7Скорборд");
        toggleOffLore = getLore(togOff, List.of("", "&7Скорборд выключен.", "&aЛКМ &7— включить"));

        ConfigurationSection locked = gui.getConfigurationSection("locked-placeholder");
        lockedMaterial = get(locked, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTU4YjAyN2U5NTQ4MWQ3Y2NlMDY5YWY0ZTdlNjY4YzY3MjhiNzc2NDc4YzMwYmY1YjE2YzQ1OTNhYzI3YzZmMyJ9fX0=");
        lockedName = get(locked, "display-name", "&8🔒");
    }

    private void setGuiDefaults() {
        guiTitle = "&8⚙ &lСкорборд";
        fillerMaterial = "GRAY_STAINED_GLASS_PANE";
        fillerName = " ";
        profileMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MyNDJiZTA5MjI2YTNhZjlkYTM0YzZkMzA1YTEyMzA2M2FhZjQyMWFlZTAzZDk4M2MxYmY1MjE1YzQyMWU4In19fQ==";
        profileName = "&bПрофиль";
        profileLore = List.of("", "&aЛКМ &7— открыть профиль");
        profileCommand = "";
        backMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==";
        backName = "&7« Назад";
        backLore = List.of("", "&aЛКМ &7— вернуться в настройки");
        backCommand = "";
        closeMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==";
        closeName = "&cЗакрыть";
        closeLore = List.of("", "&aЛКМ &7— закрыть меню");
        toggleOnMaterial = "LIME_DYE";
        toggleOnName = "&aСкорборд";
        toggleOnLore = List.of("", "&7Скорборд включён.", "&cЛКМ &7— выключить");
        toggleOffMaterial = "GRAY_DYE";
        toggleOffName = "&7Скорборд";
        toggleOffLore = List.of("", "&7Скорборд выключен.", "&aЛКМ &7— включить");
        lockedMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTU4YjAyN2U5NTQ4MWQ3Y2NlMDY5YWY0ZTdlNjY4YzY3MjhiNzc2NDc4YzMwYmY1YjE2YzQ1OTNhYzI3YzZmMyJ9fX0=";
        lockedName = "&8🔒";
    }

    private static String get(ConfigurationSection s, String key, String def) {
        return s != null ? s.getString(key, def) : def;
    }

    private static List<String> getLore(ConfigurationSection s, List<String> def) {
        if (s == null || !s.contains("lore")) return def;
        List<String> l = s.getStringList("lore");
        return l.isEmpty() ? def : l;
    }

    public String getTitle() { return title; }
    public String getFixedBottom() { return fixedBottom; }
    public int getUpdateInterval() { return updateInterval; }
    public int getMaxPlaceholders() { return maxPlaceholders; }
    public List<String> getDisabledWorlds() { return disabledWorlds != null ? disabledWorlds : List.of(); }
    public Map<String, ScoreboardPlaceholder> getPlaceholders() { return placeholders; }
    public ScoreboardPlaceholder getPlaceholder(String id) { return placeholders.get(id); }
    public List<String> getPlaceholderOrder() { return placeholderOrder; }

    public String getGuiTitle() { return guiTitle; }
    public String getFillerMaterial() { return fillerMaterial; }
    public String getFillerName() { return fillerName; }
    public String getProfileMaterial() { return profileMaterial; }
    public String getProfileName() { return profileName; }
    public List<String> getProfileLore() { return profileLore; }
    public String getProfileCommand() { return profileCommand; }
    public String getBackMaterial() { return backMaterial; }
    public String getBackName() { return backName; }
    public List<String> getBackLore() { return backLore; }
    public String getBackCommand() { return backCommand; }
    public String getCloseMaterial() { return closeMaterial; }
    public String getCloseName() { return closeName; }
    public List<String> getCloseLore() { return closeLore; }
    public String getToggleOnMaterial() { return toggleOnMaterial; }
    public String getToggleOnName() { return toggleOnName; }
    public List<String> getToggleOnLore() { return toggleOnLore; }
    public String getToggleOffMaterial() { return toggleOffMaterial; }
    public String getToggleOffName() { return toggleOffName; }
    public List<String> getToggleOffLore() { return toggleOffLore; }
    public String getLockedMaterial() { return lockedMaterial; }
    public String getLockedName() { return lockedName; }
}

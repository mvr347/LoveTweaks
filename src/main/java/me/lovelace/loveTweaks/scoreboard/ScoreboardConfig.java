package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ScoreboardConfig {

    private String title;
    private String fixedBottom;
    private int updateInterval;
    private int maxSections;
    private List<String> disabledWorlds;

    private final List<ScoreboardSection> sections = new ArrayList<>();
    private final Map<String, ScoreboardSection> sectionMap = new LinkedHashMap<>();
    private final List<String> sectionOrder = new ArrayList<>();

    // GUI
    private String guiTitle;
    private String fillerMaterial;
    private String fillerName;
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

    public void load(FileConfiguration cfg) {
        ConfigurationSection sb = cfg.getConfigurationSection("scoreboard");
        if (sb == null) return;

        title = sb.getString("title", "&FVOIDCORE");
        fixedBottom = sb.getString("fixed-bottom", "  &c✌ &7V O I D C O R E &c✌   ");
        updateInterval = sb.getInt("update-interval", 20);
        maxSections = sb.getInt("max-sections", 8);
        disabledWorlds = sb.getStringList("disabled-worlds");

        sections.clear();
        sectionMap.clear();
        sectionOrder.clear();

        ConfigurationSection sects = sb.getConfigurationSection("sections");
        if (sects != null) {
            for (String id : sects.getKeys(false)) {
                ConfigurationSection s = sects.getConfigurationSection(id);
                if (s == null) continue;
                ScoreboardSection section = new ScoreboardSection(
                        id,
                        s.getString("display-name", id),
                        s.getString("icon", "PAPER"),
                        s.getStringList("lines"),
                        s.getStringList("lore")
                );
                sections.add(section);
                sectionMap.put(id, section);
                sectionOrder.add(id);
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

        ConfigurationSection back = gui.getConfigurationSection("back-button");
        backMaterial = get(back, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==");
        backName = get(back, "display-name", "&7« Назад");
        backLore = getLore(back, List.of("", "&aЛКМ &7— вернуться назад"));
        backCommand = back != null ? back.getString("command", "") : "";

        ConfigurationSection close = gui.getConfigurationSection("close-button");
        closeMaterial = get(close, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==");
        closeName = get(close, "display-name", "&cЗакрыть");
        closeLore = getLore(close, List.of("", "&aЛКМ &7— закрыть меню"));

        ConfigurationSection tonOn = gui.getConfigurationSection("toggle-on");
        toggleOnMaterial = get(tonOn, "material", "LIME_DYE");
        toggleOnName = get(tonOn, "display-name", "&aСкорборд");
        toggleOnLore = getLore(tonOn, List.of("", "&7Скорборд включён.", "&cЛКМ &7— выключить"));

        ConfigurationSection togOff = gui.getConfigurationSection("toggle-off");
        toggleOffMaterial = get(togOff, "material", "GRAY_DYE");
        toggleOffName = get(togOff, "display-name", "&7Скорборд");
        toggleOffLore = getLore(togOff, List.of("", "&7Скорборд выключен.", "&aЛКМ &7— включить"));
    }

    private void setGuiDefaults() {
        guiTitle = "&8⚙ &lСкорборд";
        fillerMaterial = "GRAY_STAINED_GLASS_PANE";
        fillerName = " ";
        backMaterial = "BARRIER";
        backName = "&7« Назад";
        backLore = List.of("", "&aЛКМ &7— вернуться назад");
        backCommand = "";
        closeMaterial = "BARRIER";
        closeName = "&cЗакрыть";
        closeLore = List.of("", "&aЛКМ &7— закрыть меню");
        toggleOnMaterial = "LIME_DYE";
        toggleOnName = "&aСкорборд";
        toggleOnLore = List.of("", "&7Скорборд включён.", "&cЛКМ &7— выключить");
        toggleOffMaterial = "GRAY_DYE";
        toggleOffName = "&7Скорборд";
        toggleOffLore = List.of("", "&7Скорборд выключен.", "&aЛКМ &7— включить");
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
    public int getMaxSections() { return maxSections; }
    public List<String> getDisabledWorlds() { return disabledWorlds != null ? disabledWorlds : List.of(); }
    public List<ScoreboardSection> getSections() { return sections; }
    public ScoreboardSection getSection(String id) { return sectionMap.get(id); }
    public List<String> getSectionOrder() { return sectionOrder; }

    public String getGuiTitle() { return guiTitle; }
    public String getFillerMaterial() { return fillerMaterial; }
    public String getFillerName() { return fillerName; }
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
}

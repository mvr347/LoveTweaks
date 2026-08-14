package me.lovelace.loveTweaks.scoreboard;

import me.lovelace.loveTweaks.textures.HeadTextures;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ScoreboardConfig {

    /**
     * First usable (non-border) column slot of each placeholder row in the /scoreboard GUI.
     * Slots 9-17 are the gui-gen-5 Row1 separator (always glass, part of the Header) and are
     * never used for content — the work zone starts at row 18-26.
     */
    private static final int[] PLACEHOLDER_ROW_START = {19, 28, 37};
    private static final int PLACEHOLDER_ROW_WIDTH = 7;
    public static final int MAX_PLACEHOLDER_SLOTS = PLACEHOLDER_ROW_START.length * PLACEHOLDER_ROW_WIDTH;

    private String title;
    private String top;
    private String bottom;
    private String separator;
    private int updateInterval;
    private int maxPlaceholders;
    private List<String> disabledWorlds;

    private final Map<String, ScoreboardPlaceholder> placeholders = new LinkedHashMap<>();
    private final List<String> placeholderOrder = new ArrayList<>();
    private final Map<String, Integer> placeholderSlots = new LinkedHashMap<>();

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
    private String placeholderOnMaterial;
    private String placeholderOffMaterial;
    private String placeholderBlockedMaterial;
    private String toggleOnMaterial;
    private String toggleOnName;
    private List<String> toggleOnLore;
    private String toggleOffMaterial;
    private String toggleOffName;
    private List<String> toggleOffLore;
    private String toggleEmptyMaterial;
    private String toggleEmptyName;
    private List<String> toggleEmptyLore;

    public void load(FileConfiguration cfg) {
        ConfigurationSection sb = cfg.getConfigurationSection("scoreboard");
        if (sb == null) return;

        title = sb.getString("title", "&FVOIDCORE");
        top = sb.getString("top", "&c✌ &7V O I D C O R E &c✌");
        bottom = sb.getString("bottom", "&c✌ &7V O I D C O R E &c✌");
        separator = sb.getString("separator", "");
        // Bukkit's runTaskTimer() throws IllegalArgumentException for period < 1, which would
        // otherwise crash the whole plugin's onEnable() (and reloadAll()) on a 0/negative typo.
        updateInterval = Math.max(1, sb.getInt("update-interval", 20));
        maxPlaceholders = sb.getInt("max-placeholders", 8);
        disabledWorlds = sb.getStringList("disabled-worlds");

        placeholders.clear();
        placeholderOrder.clear();

        ConfigurationSection phSection = sb.getConfigurationSection("placeholders");
        if (phSection != null) {
            for (String id : phSection.getKeys(false)) {
                ConfigurationSection p = phSection.getConfigurationSection(id);
                if (p == null) continue;

                ScoreboardRequirement requirement = null;
                ConfigurationSection req = p.getConfigurationSection("requirement");
                if (req != null) {
                    String phExpr = req.getString("placeholder");
                    if (phExpr != null && !phExpr.isBlank()) {
                        requirement = new ScoreboardRequirement(
                                phExpr,
                                req.getString("equals", null),
                                req.getString("reason", "")
                        );
                    }
                }

                ScoreboardPlaceholder placeholder = new ScoreboardPlaceholder(
                        id,
                        p.getString("display-name", id),
                        p.getString("template", ""),
                        p.getStringList("lore"),
                        requirement,
                        p.getInt("sort-group", 0)
                );
                placeholders.put(id, placeholder);
                placeholderOrder.add(id);
            }
        }
        assignPlaceholderSlots();

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
        profileMaterial = get(profile, "material", basehead(HeadTextures.SCOREBOARD_PROFILE));
        profileName = get(profile, "display-name", "&bПрофиль");
        profileLore = getLore(profile, List.of("", "&aЛКМ &7— открыть профиль"));
        profileCommand = profile != null ? profile.getString("command", "") : "";

        ConfigurationSection back = gui.getConfigurationSection("back-button");
        backMaterial = get(back, "material", basehead(HeadTextures.SCOREBOARD_BACK));
        backName = get(back, "display-name", "&7« Назад");
        backLore = getLore(back, List.of("", "&aЛКМ &7— вернуться в настройки"));
        backCommand = back != null ? back.getString("command", "") : "";

        ConfigurationSection close = gui.getConfigurationSection("close-button");
        closeMaterial = get(close, "material", basehead(HeadTextures.SCOREBOARD_CLOSE));
        closeName = get(close, "display-name", "&cЗакрыть");
        closeLore = getLore(close, List.of("", "&aЛКМ &7— закрыть меню"));

        ConfigurationSection phOn = gui.getConfigurationSection("placeholder-on");
        placeholderOnMaterial = get(phOn, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_ON));

        ConfigurationSection phOff = gui.getConfigurationSection("placeholder-off");
        placeholderOffMaterial = get(phOff, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF));

        ConfigurationSection phBlocked = gui.getConfigurationSection("placeholder-blocked");
        placeholderBlockedMaterial = get(phBlocked, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_BLOCKED));

        ConfigurationSection togOn = gui.getConfigurationSection("toggle-on");
        toggleOnMaterial = get(togOn, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_ON));
        toggleOnName = get(togOn, "display-name", "&aСкорборд включён");
        toggleOnLore = getLore(togOn, List.of("", "&7Скорборд отображается на экране.", "&cЛКМ &7— выключить"));

        ConfigurationSection togOff = gui.getConfigurationSection("toggle-off");
        toggleOffMaterial = get(togOff, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_OFF));
        toggleOffName = get(togOff, "display-name", "&7Скорборд выключен");
        toggleOffLore = getLore(togOff, List.of("", "&7Скорборд скрыт с экрана.", "&aЛКМ &7— включить"));

        ConfigurationSection togEmpty = gui.getConfigurationSection("toggle-empty");
        toggleEmptyMaterial = get(togEmpty, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_EMPTY));
        toggleEmptyName = get(togEmpty, "display-name", "&7Скорборд");
        toggleEmptyLore = getLore(togEmpty, List.of("", "&7Нет ни одного включённого плейсхолдера.", "&7Выберите плейсхолдеры ниже &8(СКМ)"));
    }

    private void assignPlaceholderSlots() {
        placeholderSlots.clear();
        List<ScoreboardPlaceholder> ordered = new ArrayList<>(placeholders.values());
        ordered.sort(Comparator.comparingInt(ScoreboardPlaceholder::sortGroup));

        int i = 0;
        for (ScoreboardPlaceholder ph : ordered) {
            if (i >= MAX_PLACEHOLDER_SLOTS) break;
            int row = i / PLACEHOLDER_ROW_WIDTH;
            int col = i % PLACEHOLDER_ROW_WIDTH;
            placeholderSlots.put(ph.id(), PLACEHOLDER_ROW_START[row] + col);
            i++;
        }
    }

    private void setGuiDefaults() {
        guiTitle = "&8⚙ &lСкорборд";
        fillerMaterial = "GRAY_STAINED_GLASS_PANE";
        fillerName = " ";
        profileMaterial = basehead(HeadTextures.SCOREBOARD_PROFILE);
        profileName = "&bПрофиль";
        profileLore = List.of("", "&aЛКМ &7— открыть профиль");
        profileCommand = "";
        backMaterial = basehead(HeadTextures.SCOREBOARD_BACK);
        backName = "&7« Назад";
        backLore = List.of("", "&aЛКМ &7— вернуться в настройки");
        backCommand = "";
        closeMaterial = basehead(HeadTextures.SCOREBOARD_CLOSE);
        closeName = "&cЗакрыть";
        closeLore = List.of("", "&aЛКМ &7— закрыть меню");
        placeholderOnMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_ON);
        placeholderOffMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF);
        placeholderBlockedMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_BLOCKED);
        toggleOnMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_ON);
        toggleOnName = "&aСкорборд включён";
        toggleOnLore = List.of("", "&7Скорборд отображается на экране.", "&cЛКМ &7— выключить");
        toggleOffMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_OFF);
        toggleOffName = "&7Скорборд выключен";
        toggleOffLore = List.of("", "&7Скорборд скрыт с экрана.", "&aЛКМ &7— включить");
        toggleEmptyMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_EMPTY);
        toggleEmptyName = "&7Скорборд";
        toggleEmptyLore = List.of("", "&7Нет ни одного включённого плейсхолдера.", "&7Выберите плейсхолдеры ниже &8(СКМ)");
    }

    /** {@code basehead-<base64>} — формат, который {@code GuiItemUtil}/{@code ScoreboardGUI} распознают как голову с кастомной текстурой. */
    private static String basehead(String base64Texture) {
        return "basehead-" + base64Texture;
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
    public String getTop() { return top; }
    public String getBottom() { return bottom; }
    public String getSeparator() { return separator; }
    public int getUpdateInterval() { return updateInterval; }
    public int getMaxPlaceholders() { return maxPlaceholders; }
    public List<String> getDisabledWorlds() { return disabledWorlds != null ? disabledWorlds : List.of(); }
    public Map<String, ScoreboardPlaceholder> getPlaceholders() { return placeholders; }
    public ScoreboardPlaceholder getPlaceholder(String id) { return placeholders.get(id); }
    public List<String> getPlaceholderOrder() { return placeholderOrder; }
    public Map<String, Integer> getPlaceholderSlots() { return placeholderSlots; }

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
    public String getPlaceholderOnMaterial() { return placeholderOnMaterial; }
    public String getPlaceholderOffMaterial() { return placeholderOffMaterial; }
    public String getPlaceholderBlockedMaterial() { return placeholderBlockedMaterial; }
    public String getToggleOnMaterial() { return toggleOnMaterial; }
    public String getToggleOnName() { return toggleOnName; }
    public List<String> getToggleOnLore() { return toggleOnLore; }
    public String getToggleOffMaterial() { return toggleOffMaterial; }
    public String getToggleOffName() { return toggleOffName; }
    public List<String> getToggleOffLore() { return toggleOffLore; }
    public String getToggleEmptyMaterial() { return toggleEmptyMaterial; }
    public String getToggleEmptyName() { return toggleEmptyName; }
    public List<String> getToggleEmptyLore() { return toggleEmptyLore; }
}

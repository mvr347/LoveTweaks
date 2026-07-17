package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ScoreboardConfig {

    /** First usable (non-border) column slot of each placeholder row in the /scoreboard GUI. */
    private static final int[] PLACEHOLDER_ROW_START = {10, 19, 28, 37};
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

        ConfigurationSection phOn = gui.getConfigurationSection("placeholder-on");
        placeholderOnMaterial = get(phOn, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODg5MDgyNTQ1MWMwMWJkMDNiMDMwNzkwNjIxYWI3NTM0NDgzMTlmODQ3NDliYjAyYzkwZjNhMjg0ODliZDcyIn19fQ==");

        ConfigurationSection phOff = gui.getConfigurationSection("placeholder-off");
        placeholderOffMaterial = get(phOff, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmZWY3NTNkNWI0ZmYyYTljYWU3NWJjMmVkZWIzMTUzMDI1YWJjNWNjMjc0NDI4NWYzMTk2NGY5NDA4YTFmIn19fQ==");

        ConfigurationSection phBlocked = gui.getConfigurationSection("placeholder-blocked");
        placeholderBlockedMaterial = get(phBlocked, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==");

        ConfigurationSection togOn = gui.getConfigurationSection("toggle-on");
        toggleOnMaterial = get(togOn, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2FmNmMzY2FjOTRjODk0NGQwNDQxNTBjMWRkNWU0ZTZhYjUzYTAxMzgyZGFlNDYzOTE0ZmIzYmU1YTI3MzE5ZCJ9fX0=");
        toggleOnName = get(togOn, "display-name", "&aСкорборд включён");
        toggleOnLore = getLore(togOn, List.of("", "&7Скорборд отображается на экране.", "&cЛКМ &7— выключить"));

        ConfigurationSection togOff = gui.getConfigurationSection("toggle-off");
        toggleOffMaterial = get(togOff, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM1MzQ1MjkwZDdlNDRkZWEzYTg5MGQ0ZDAxNDBmYzEwYTUyYTkwOTc2NzQzOGYwZjViNWQyODc3Y2JhNDg0YyJ9fX0=");
        toggleOffName = get(togOff, "display-name", "&7Скорборд выключен");
        toggleOffLore = getLore(togOff, List.of("", "&7Скорборд скрыт с экрана.", "&aЛКМ &7— включить"));

        ConfigurationSection togEmpty = gui.getConfigurationSection("toggle-empty");
        toggleEmptyMaterial = get(togEmpty, "material", "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjNjMDA1NmI3YjI4MWZlMmQ0ZmRkNjc1NzdiMDI2ZWE3NDIyNmYzNjQ5YTFkNTBkYjI3NDI1YmNmYjRiMGE5YyJ9fX0=");
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
        placeholderOnMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODg5MDgyNTQ1MWMwMWJkMDNiMDMwNzkwNjIxYWI3NTM0NDgzMTlmODQ3NDliYjAyYzkwZjNhMjg0ODliZDcyIn19fQ==";
        placeholderOffMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmZWY3NTNkNWI0ZmYyYTljYWU3NWJjMmVkZWIzMTUzMDI1YWJjNWNjMjc0NDI4NWYzMTk2NGY5NDA4YTFmIn19fQ==";
        placeholderBlockedMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==";
        toggleOnMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2FmNmMzY2FjOTRjODk0NGQwNDQxNTBjMWRkNWU0ZTZhYjUzYTAxMzgyZGFlNDYzOTE0ZmIzYmU1YTI3MzE5ZCJ9fX0=";
        toggleOnName = "&aСкорборд включён";
        toggleOnLore = List.of("", "&7Скорборд отображается на экране.", "&cЛКМ &7— выключить");
        toggleOffMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM1MzQ1MjkwZDdlNDRkZWEzYTg5MGQ0ZDAxNDBmYzEwYTUyYTkwOTc2NzQzOGYwZjViNWQyODc3Y2JhNDg0YyJ9fX0=";
        toggleOffName = "&7Скорборд выключен";
        toggleOffLore = List.of("", "&7Скорборд скрыт с экрана.", "&aЛКМ &7— включить");
        toggleEmptyMaterial = "basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjNjMDA1NmI3YjI4MWZlMmQ0ZmRkNjc1NzdiMDI2ZWE3NDIyNmYzNjQ5YTFkNTBkYjI3NDI1YmNmYjRiMGE5YyJ9fX0=";
        toggleEmptyName = "&7Скорборд";
        toggleEmptyLore = List.of("", "&7Нет ни одного включённого плейсхолдера.", "&7Выберите плейсхолдеры ниже &8(СКМ)");
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

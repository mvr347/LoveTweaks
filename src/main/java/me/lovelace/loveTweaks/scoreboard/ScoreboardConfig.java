package me.lovelace.loveTweaks.scoreboard;

import me.lovelace.loveTweaks.textures.HeadTextures;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

public class ScoreboardConfig {

    private String title;
    private String top;
    private String bottom;
    private String separator;
    private int updateInterval;
    private int maxPlaceholders;
    private List<String> disabledWorlds;

    private final Map<String, ScoreboardPlaceholder> placeholders = new LinkedHashMap<>();
    private final List<String> placeholderOrder = new ArrayList<>();
    private final Map<String, ScoreboardCategory> categories = new LinkedHashMap<>();
    private final Map<String, ScoreboardPreset> presets = new LinkedHashMap<>();
    private String defaultPreset;
    private final ScoreboardMessages messages = new ScoreboardMessages();

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
    private String categoryButtonMaterial;
    private String presetButtonMaterial;
    private String viewButtonMaterial;
    private String pagePrevMaterial;
    private String pageNextMaterial;
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
        // Hard cap 8: more does not fit the 2x4 slot cards nor the 15-line scoreboard body.
        maxPlaceholders = ScoreboardLayout.clampMaxLines(sb.getInt("max-placeholders", ScoreboardLayout.MAX_LINES));
        disabledWorlds = sb.getStringList("disabled-worlds");

        placeholders.clear();
        placeholderOrder.clear();
        categories.clear();
        presets.clear();

        loadCategories(sb.getConfigurationSection("categories"));
        loadPlaceholders(sb.getConfigurationSection("placeholders"));
        loadPresets(sb.getConfigurationSection("presets"));
        defaultPreset = sb.getString("default-preset", "");

        ConfigurationSection gui = sb.getConfigurationSection("gui");
        if (gui == null) {
            setGuiDefaults();
            return;
        }

        guiTitle = gui.getString("title", "&6Скорборд");

        ConfigurationSection filler = gui.getConfigurationSection("filler");
        fillerMaterial = get(filler, "material", "GRAY_STAINED_GLASS_PANE");
        fillerName = get(filler, "display-name", " ");

        ConfigurationSection profile = gui.getConfigurationSection("profile-button");
        profileMaterial = get(profile, "material", basehead(HeadTextures.SCOREBOARD_PROFILE));
        profileName = get(profile, "display-name", "&bПрофиль");
        profileLore = getLore(profile, List.of("&8&m─────────────────────", "&aЛКМ &8— &7открыть профиль"));
        profileCommand = profile != null ? profile.getString("command", "") : "";

        ConfigurationSection back = gui.getConfigurationSection("back-button");
        backMaterial = get(back, "material", basehead(HeadTextures.SCOREBOARD_BACK));
        backName = get(back, "display-name", "&cНазад");
        backLore = getLore(back, List.of("&8&m─────────────────────", "&7Вернуться в настройки"));
        backCommand = back != null ? back.getString("command", "") : "";

        ConfigurationSection close = gui.getConfigurationSection("close-button");
        closeMaterial = get(close, "material", basehead(HeadTextures.SCOREBOARD_CLOSE));
        closeName = get(close, "display-name", "&cЗакрыть");
        closeLore = getLore(close, List.of("&8&m─────────────────────", "&7Закрыть текущее окно"));

        ConfigurationSection phOn = gui.getConfigurationSection("placeholder-on");
        placeholderOnMaterial = get(phOn, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_ON));

        ConfigurationSection phOff = gui.getConfigurationSection("placeholder-off");
        placeholderOffMaterial = get(phOff, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF));

        ConfigurationSection phBlocked = gui.getConfigurationSection("placeholder-blocked");
        placeholderBlockedMaterial = get(phBlocked, "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_BLOCKED));

        ConfigurationSection togOn = gui.getConfigurationSection("toggle-on");
        toggleOnMaterial = get(togOn, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_ON));
        toggleOnName = get(togOn, "display-name", "&aСкорборд включён");
        toggleOnLore = getLore(togOn, List.of("&7Скорборд отображается на экране.", "&8&m─────────────────────", "&cЛКМ &8— &7выключить"));

        ConfigurationSection togOff = gui.getConfigurationSection("toggle-off");
        toggleOffMaterial = get(togOff, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_OFF));
        toggleOffName = get(togOff, "display-name", "&7Скорборд выключен");
        toggleOffLore = getLore(togOff, List.of("&7Скорборд скрыт с экрана.", "&8&m─────────────────────", "&aЛКМ &8— &7включить"));

        categoryButtonMaterial = get(gui.getConfigurationSection("category-button"), "material", basehead(HeadTextures.SCOREBOARD_PROFILE));
        presetButtonMaterial = get(gui.getConfigurationSection("preset-button"), "material", basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF));
        viewButtonMaterial = get(gui.getConfigurationSection("view-button"), "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_ON));
        pagePrevMaterial = get(gui.getConfigurationSection("page-prev"), "material", basehead(HeadTextures.SCOREBOARD_PAGE_PREV));
        pageNextMaterial = get(gui.getConfigurationSection("page-next"), "material", basehead(HeadTextures.SCOREBOARD_PAGE_NEXT));
        messages.load(gui.getConfigurationSection("messages"));

        ConfigurationSection togEmpty = gui.getConfigurationSection("toggle-empty");
        toggleEmptyMaterial = get(togEmpty, "material", basehead(HeadTextures.SCOREBOARD_TOGGLE_EMPTY));
        toggleEmptyName = get(togEmpty, "display-name", "&7Скорборд");
        toggleEmptyLore = getLore(togEmpty, List.of("", "&7Нет ни одного включённого плейсхолдера.", "&7Выберите плейсхолдеры ниже &8(СКМ)"));
    }

    private void loadCategories(ConfigurationSection section) {
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                categories.put(id, new ScoreboardCategory(id, c.getString("display-name", id),
                        c.getString("icon", null), c.getStringList("lore")));
            }
        }
        if (categories.isEmpty()) {
            categories.put("general", new ScoreboardCategory("general", "&bОбщее", null, List.of()));
        }
    }

    private void loadPlaceholders(ConfigurationSection section) {
        if (section == null) return;
        String firstCategory = categories.keySet().iterator().next();
        for (String id : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(id);
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

            List<ScoreboardPlaceholder.Variant> variants = new ArrayList<>();
            for (Map<?, ?> raw : p.getMapList("variants")) {
                Object tpl = raw.get("template");
                if (tpl == null) continue;
                Object name = raw.get("name");
                variants.add(new ScoreboardPlaceholder.Variant(name != null ? name.toString() : "", tpl.toString()));
            }
            if (variants.isEmpty()) {
                variants.add(new ScoreboardPlaceholder.Variant("", p.getString("template", "")));
            }

            // `category` replaced the numeric `sort-group`; an old config still maps through the table.
            String category = p.getString("category");
            if (category == null) category = LegacyMigration.categoryForSortGroup(p.getInt("sort-group", 0));
            if (!categories.containsKey(category)) category = firstCategory;

            placeholders.put(id, ScoreboardPlaceholder.create(id, p.getString("display-name", id), variants,
                    p.getStringList("lore"), requirement, category));
            placeholderOrder.add(id);
        }
    }

    private void loadPresets(ConfigurationSection section) {
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(id);
            if (p == null) continue;
            // The header holds 6 control buttons; one of them is the view toggle.
            if (presets.size() >= MAX_PRESETS) break;
            presets.put(id, new ScoreboardPreset(id, p.getString("name", id), p.getString("icon", null),
                    p.getStringList("lore"), PresetApplier.parse(p.getStringList("placeholders"))));
        }
    }

    /** Header slots 2-7 minus the view toggle. */
    public static final int MAX_PRESETS = 5;

    private void setGuiDefaults() {
        guiTitle = "&6Скорборд";
        fillerMaterial = "GRAY_STAINED_GLASS_PANE";
        fillerName = " ";
        profileMaterial = basehead(HeadTextures.SCOREBOARD_PROFILE);
        profileName = "&bПрофиль";
        profileLore = List.of("&8&m─────────────────────", "&aЛКМ &8— &7открыть профиль");
        profileCommand = "";
        backMaterial = basehead(HeadTextures.SCOREBOARD_BACK);
        backName = "&cНазад";
        backLore = List.of("&8&m─────────────────────", "&7Вернуться в настройки");
        backCommand = "";
        closeMaterial = basehead(HeadTextures.SCOREBOARD_CLOSE);
        closeName = "&cЗакрыть";
        closeLore = List.of("&8&m─────────────────────", "&7Закрыть текущее окно");
        placeholderOnMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_ON);
        placeholderOffMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF);
        placeholderBlockedMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_BLOCKED);
        toggleOnMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_ON);
        toggleOnName = "&aСкорборд включён";
        toggleOnLore = List.of("&7Скорборд отображается на экране.", "&8&m─────────────────────", "&cЛКМ &8— &7выключить");
        toggleOffMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_OFF);
        toggleOffName = "&7Скорборд выключен";
        toggleOffLore = List.of("&7Скорборд скрыт с экрана.", "&8&m─────────────────────", "&aЛКМ &8— &7включить");
        categoryButtonMaterial = basehead(HeadTextures.SCOREBOARD_PROFILE);
        presetButtonMaterial = basehead(HeadTextures.SCOREBOARD_PLACEHOLDER_OFF);
        viewButtonMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_ON);
        pagePrevMaterial = basehead(HeadTextures.SCOREBOARD_PAGE_PREV);
        pageNextMaterial = basehead(HeadTextures.SCOREBOARD_PAGE_NEXT);
        messages.load(null);
        toggleEmptyMaterial = basehead(HeadTextures.SCOREBOARD_TOGGLE_EMPTY);
        toggleEmptyName = "&7Скорборд";
        toggleEmptyLore = List.of("&7Нет ни одного включённого плейсхолдера.", "&8&m─────────────────────", "&7Выберите плейсхолдеры ниже");
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
    public Map<String, ScoreboardCategory> getCategories() { return categories; }
    public Map<String, ScoreboardPreset> getPresets() { return presets; }
    public String getDefaultPreset() { return defaultPreset; }
    public ScoreboardMessages messages() { return messages; }

    /** Ids of the placeholders of one category, in config order. */
    public List<ScoreboardPlaceholder> placeholdersOf(String categoryId) {
        List<ScoreboardPlaceholder> out = new ArrayList<>();
        for (ScoreboardPlaceholder ph : placeholders.values()) {
            if (ph.category().equals(categoryId)) out.add(ph);
        }
        return out;
    }

    public String getCategoryButtonMaterial() { return categoryButtonMaterial; }
    public String getPresetButtonMaterial() { return presetButtonMaterial; }
    public String getViewButtonMaterial() { return viewButtonMaterial; }
    public String getPagePrevMaterial() { return pagePrevMaterial; }
    public String getPageNextMaterial() { return pageNextMaterial; }

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

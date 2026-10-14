package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User-facing texts of the /scoreboard menus. Real values live in {@code scoreboard.gui.messages}
 * in config.yml; these defaults only keep the menu readable when a key is missing.
 */
public final class ScoreboardMessages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        d("view-name-categories", "По категориям");
        d("view-name-all", "Все");
        d("view-title", "&6Вид меню");
        d("view-current", "&7Сейчас: &f<current>");
        d("view-other", "&7Другой вид: &f<other>");
        d("view-click", "&aЛКМ &7— переключить");

        d("preset-header", "&7Заменяет текущий набор строками:");
        d("preset-line", "&8▸ &f<name>");
        d("preset-line-skipped", "&8▸ &8<name> &c✘");
        d("preset-active", "&aАктивна");
        d("preset-click", "&aЛКМ &7— применить");
        d("preset-unavailable", "&cНедоступно сейчас");
        d("preset-unavailable-reason", "&7Ни одна строка раскладки сейчас недоступна");

        d("slot-empty-name", "&7Слот <n> &8· &7пусто");
        d("slot-empty-lore", "&7Выберите строку в категории ниже");
        d("slot-label", "&7Слот <n>");
        d("slot-up", "&aЛКМ &7— выше");
        d("slot-down", "&aПКМ &7— ниже");
        d("slot-remove", "&cСКМ &7или &cShift+ПКМ &7— убрать");
        d("slot-variant", "&eShift+ЛКМ &7— формат: &f<variant>");

        d("category-enabled", "&7Включено: &f<on> &7из &f<total>");
        d("category-open", "&aЛКМ &7— открыть");

        d("flat-position", "&7Позиция в скорборде: &f#<pos> &7из &f<total>");
        d("flat-enable", "&aСКМ &7или &aShift+ПКМ &7— включить");
        d("flat-disable", "&cСКМ &7или &cShift+ПКМ &7— выключить");
        d("flat-up", "&aЛКМ &7— выше");
        d("flat-down", "&aПКМ &7— ниже");

        d("sub-enable", "&aЛКМ &7— включить");
        d("sub-disable", "&cЛКМ &7— выключить");
        d("sub-variant", "&eShift+ЛКМ &7— формат: &f<variant>");
        d("sub-slot", "&7Слот скорборда: &f#<n>");

        d("unavailable", "&cНедоступно");
        d("reason-plugin", "&7Плагин не установлен");
        d("reason-full", "&7Все <max> слотов заняты");

        d("back-to-main", "&aЛКМ &7— в главное меню");

        d("page-prev", "&e« Предыдущая страница");
        d("page-next", "&eСледующая страница »");
        d("page-lore", "&7Страница &f<page>&7/&f<pages>");
    }

    private static void d(String key, String value) {
        DEFAULTS.put(key, value);
    }

    private final Map<String, String> values = new HashMap<>(DEFAULTS);

    public void load(ConfigurationSection section) {
        values.clear();
        values.putAll(DEFAULTS);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String v = section.getString(key);
            if (v != null) values.put(key, v);
        }
    }

    public String get(String key) {
        return values.getOrDefault(key, key);
    }

    /** Replaces {@code <name>} tokens; args are alternating name, value. */
    public String get(String key, Object... args) {
        return fill(get(key), args);
    }

    public static String fill(String text, Object... args) {
        String out = text;
        for (int i = 0; i + 1 < args.length; i += 2) {
            out = out.replace("<" + args[i] + ">", String.valueOf(args[i + 1]));
        }
        return out;
    }

    public static List<String> defaultKeys() {
        return List.copyOf(DEFAULTS.keySet());
    }
}

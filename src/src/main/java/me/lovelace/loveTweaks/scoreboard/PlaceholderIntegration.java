package me.lovelace.loveTweaks.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderIntegration {

    // Некоторые внешние плагины (например, кланы) отдают цвет своих значений в PAPI
    // MiniMessage-тегами вида "<gold>TAG" вместо legacy '&'-кодов. Скорборд везде работает
    // с legacy-цветом, поэтому такой тег иначе попадает на экран буквально как "<gold>".
    private static final Pattern MINIMESSAGE_TAG = Pattern.compile(
            "<(/?)(#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|"
                    + "gray|grey|dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white|"
                    + "bold|italic|underlined|strikethrough|obfuscated|reset)>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, Character> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", '0'), Map.entry("dark_blue", '1'), Map.entry("dark_green", '2'),
            Map.entry("dark_aqua", '3'), Map.entry("dark_red", '4'), Map.entry("dark_purple", '5'),
            Map.entry("gold", '6'), Map.entry("gray", '7'), Map.entry("grey", '7'),
            Map.entry("dark_gray", '8'), Map.entry("dark_grey", '8'), Map.entry("blue", '9'),
            Map.entry("green", 'a'), Map.entry("aqua", 'b'), Map.entry("red", 'c'),
            Map.entry("light_purple", 'd'), Map.entry("yellow", 'e'), Map.entry("white", 'f'),
            Map.entry("bold", 'l'), Map.entry("italic", 'o'), Map.entry("underlined", 'n'),
            Map.entry("strikethrough", 'm'), Map.entry("obfuscated", 'k'), Map.entry("reset", 'r')
    );

    public String apply(Player player, String text) {
        return resolve(player, text);
    }

    /**
     * Expands a raw PAPI expression (e.g. "%clans_in_clan%"). Returns the input unchanged
     * if PlaceholderAPI is not present or the expansion fails, so callers can detect a
     * no-op expansion by comparing the result to the input.
     */
    public static String resolve(Player player, String text) {
        if (text == null || text.isBlank()) return text;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return text;
        try {
            return convertMiniMessageColors(PlaceholderAPI.setPlaceholders(player, text));
        } catch (Exception e) {
            return text;
        }
    }

    public static boolean has(Player player, String placeholder) {
        String test = "%" + placeholder + "%";
        String expanded = resolve(player, test);
        return !expanded.equals(test) && !expanded.isBlank();
    }

    /**
     * Rewrites recognized MiniMessage color/decoration tags (opening and closing) into legacy
     * '&'-codes so they render correctly through the scoreboard's legacy-color pipeline instead
     * of showing up as literal text like "&lt;gold&gt;". Unknown/unrelated "&lt;...&gt;" text
     * (e.g. part of a player-chosen name) is left untouched since only the whitelisted tag names
     * above are matched.
     */
    static String convertMiniMessageColors(String text) {
        if (text == null || text.indexOf('<') < 0) return text;

        Matcher matcher = MINIMESSAGE_TAG.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            boolean closingTag = !matcher.group(1).isEmpty();
            String tag = matcher.group(2).toLowerCase();

            String replacement;
            if (closingTag) {
                // Legacy-коды не имеют области действия, закрывающий тег просто убираем
                replacement = "";
            } else if (tag.startsWith("#")) {
                replacement = hexToLegacy(tag);
            } else {
                Character code = NAMED_COLORS.get(tag);
                replacement = code != null ? "&" + code : matcher.group();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String hexToLegacy(String hex) {
        StringBuilder legacy = new StringBuilder("&x");
        for (int i = 1; i < hex.length(); i++) {
            legacy.append('&').append(Character.toLowerCase(hex.charAt(i)));
        }
        return legacy.toString();
    }
}

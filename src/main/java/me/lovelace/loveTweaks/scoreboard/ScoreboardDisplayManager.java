package me.lovelace.loveTweaks.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardDisplayManager {

    // Unique invisible entries for scoreboard lines (section-sign + color code)
    private static final String[] INVISIBLE = {
        "§0","§1","§2","§3","§4","§5","§6","§7",
        "§8","§9","§a","§b","§c","§d","§e","§f",
        "§0§r","§1§r","§2§r","§3§r","§4§r"
    };

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LoveTweaks plugin;
    private final ScoreboardDataManager dataManager;
    private final ScoreboardConfig config;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Integer> lineCountCache = new HashMap<>();
    private final Map<UUID, LinePrefixCache> prefixCaches = new HashMap<>();
    private final Map<UUID, String> lastTitles = new HashMap<>();
    private PlaceholderIntegration papiIntegration;

    public ScoreboardDisplayManager(LoveTweaks plugin, ScoreboardDataManager dataManager, ScoreboardConfig config) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.config = config;
        initPAPI();
    }

    private void initPAPI() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            papiIntegration = new PlaceholderIntegration();
            plugin.getLogger().info("PlaceholderAPI hooked for scoreboard.");
        }
    }

    public void refreshPAPI() { initPAPI(); }

    /**
     * Players still in LoveAuth's limbo must not get a board. Not cached: a neighbour may register
     * the oracle later. Without LoveAuth the board shows immediately.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
    }

    public void updateScoreboard(Player player) {
        if (!isAuthenticated(player)) return;
        PlayerScoreboardState state = dataManager.getState(player.getUniqueId());

        if (!state.isScoreboardEnabled() || config.getDisabledWorlds().contains(player.getWorld().getName())) {
            removeScoreboard(player);
            return;
        }

        Rendered rendered = render(player, state);
        if (rendered == null) {
            removeScoreboard(player);
            return;
        }
        List<String> lines = rendered.lines();

        UUID uuid = player.getUniqueId();
        Scoreboard board = boards.get(uuid);
        int prevCount = lineCountCache.getOrDefault(uuid, -1);

        if (board == null) {
            board = createBoard(uuid, rendered);
            boards.put(uuid, board);
            lineCountCache.put(uuid, lines.size());
            player.setScoreboard(board);
            return;
        }

        if (prevCount != lines.size()) {
            // Resize in place instead of rebuilding the whole board (which resends everything).
            resizeBoard(board, uuid, prevCount, lines.size());
            lineCountCache.put(uuid, lines.size());
        }
        refreshLines(board, uuid, rendered);
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        boards.remove(uuid);
        lineCountCache.remove(uuid);
        prefixCaches.remove(uuid);
        lastTitles.remove(uuid);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        boards.clear();
        lineCountCache.clear();
        prefixCaches.clear();
        lastTitles.clear();
    }

    /**
     * Renders the active placeholders (in the player's chosen scoreboard order) two per row,
     * with a separator line after every pair — matching the reference layout: a lone trailing
     * placeholder still gets its own separator rather than being paired up. The top/bottom
     * branding lines are padded with leading spaces so they sit centered relative to the widest
     * content line. Returns null when there's nothing valid to show, so the caller hides the
     * scoreboard entirely.
     */
    List<String> buildLines(Player player, PlayerScoreboardState state) {
        Rendered rendered = render(player, state);
        return rendered == null ? null : rendered.lines();
    }

    /** Fully expanded (PAPI applied exactly once) title and lines. */
    record Rendered(String title, List<String> lines) {}

    Rendered render(Player player, PlayerScoreboardState state) {
        List<String> valid = new ArrayList<>();
        for (String id : state.getActivePlaceholders()) {
            if (valid.size() >= config.getMaxPlaceholders()) break;
            ScoreboardPlaceholder ph = config.getPlaceholder(id);
            // A line whose PAPI expansion is not installed (or whose requirement is unmet) is skipped
            // instead of printing a raw "%id_param%" on the board.
            if (ph == null || !ScoreboardAvailability.isAvailable(player, ph)) continue;

            String expanded = applyPAPI(player, ph.template(state.getVariant(id)));
            if (expanded.isBlank()) continue;
            valid.add(expanded);
        }

        if (valid.isEmpty()) {
            state.ensureAutoDisable();
            return null;
        }

        String rawTitle = applyPAPI(player, config.getTitle());
        String rawBottom = applyPAPI(player, config.getBottom());

        // Максимальная пиксельная ширина по видимым строкам плейсхолдеров и заголовку
        int contentWidth = getPixelWidth(rawTitle);
        for (String line : valid) {
            contentWidth = Math.max(contentWidth, getPixelWidth(line));
        }

        // Динамическое центрирование нижней строки по ширине контента
        String bottom = center(rawBottom, contentWidth);

        List<String> lines = new ArrayList<>();
        // Строка-разделитель между названием и плейсхолдерами (вместо top)
        String separator = applyPAPI(player, config.getSeparator());
        lines.add(separator);

        for (int i = 0; i < valid.size(); i += 2) {
            lines.add(valid.get(i));
            if (i + 1 < valid.size()) {
                lines.add(valid.get(i + 1));
            }
            lines.add(separator);
        }
        lines.add(bottom);
        return new Rendered(rawTitle, lines);
    }

    public static final int DEFAULT_MIN_PADDING_SPACES = 3;

    private static final java.util.regex.Pattern COLOR_CODES = java.util.regex.Pattern.compile(
            "[&§]x([&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]"
    );

    /**
     * Вычисляет пиксельную ширину строки в шрифте Minecraft с учётом
     * форматирования, значков, ItemsAdder font_images (:copper_coin:, etc.) и Unicode PUA.
     */
    public static int getPixelWidth(String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) return 0;
        int width = 0;
        boolean isBold = false;
        char[] chars = legacyText.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            // Обработка цветовых кодов
            if ((c == '§' || c == '&') && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                if (code == 'x' && i + 13 < chars.length) {
                    // Формат &x&r&r&g&g&b&b или §x§r§r§g§g§b§b (14 символов)
                    i += 13;
                    isBold = false;
                    continue;
                } else if (code == '#' && i + 7 < chars.length) {
                    // Формат &#rrggbb или §#rrggbb (8 символов)
                    i += 7;
                    isBold = false;
                    continue;
                } else if (code == 'l') {
                    isBold = true;
                } else if (code == 'r' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    isBold = false;
                }
                i++; // пропускаем код цвета
                continue;
            }

            // Обработка ItemsAdder font_images тегов, например :copper_coin:, :iron_coin: и т.д.
            if (c == ':') {
                int nextColon = legacyText.indexOf(':', i + 1);
                if (nextColon != -1 && nextColon - i <= 32) {
                    String tag = legacyText.substring(i + 1, nextColon).toLowerCase();
                    if (isFontImageTag(tag)) {
                        width += 9 + 1; // scale_ratio 9 + 1px spacing
                        i = nextColon;
                        continue;
                    }
                }
            }

            // Обработка ItemsAdder плейсхолдеров формата %img_copper_coin% и т.д.
            if (c == '%') {
                int nextPercent = legacyText.indexOf('%', i + 1);
                if (nextPercent != -1 && nextPercent - i <= 40) {
                    String placeholder = legacyText.substring(i + 1, nextPercent).toLowerCase();
                    if (placeholder.startsWith("img_")) {
                        width += 9 + 1; // scale_ratio 9 + 1px spacing
                        i = nextPercent;
                        continue;
                    }
                }
            }

            int charWidth = getCharPixelWidth(c);
            if (isBold && c != ' ') {
                charWidth += 1;
            }
            width += charWidth;
        }
        return width;
    }

    private static boolean isFontImageTag(String tag) {
        return tag.endsWith("_coin") || tag.endsWith("coin")
                || tag.startsWith("mini") || tag.equals("copper_coin")
                || tag.equals("iron_coin") || tag.equals("gold_coin")
                || tag.equals("diamond_coin") || tag.equals("netherite_coin");
    }

    public static int getCharPixelWidth(char c) {
        if (c == ' ' || c == '\u00A0') return 4;
        if (c == '!' || c == '.' || c == ',' || c == ':' || c == ';' || c == 'i' || c == '|' || c == '¦') return 2;
        if (c == '\'' || c == '`' || c == 'l') return 3;
        if (c == 'I' || c == '[' || c == ']' || c == 't' || c == 'і' || c == 'ї') return 4;
        if (c == 'f' || c == 'k' || c == 'к' || c == '(' || c == ')' || c == '{' || c == '}' || c == '<' || c == '>') return 5;
        if (c == '@' || c == '~') return 7;
        if (c == 'ж' || c == 'ш' || c == 'щ' || c == 'ы' || c == 'ю'
                || c == 'Ж' || c == 'М' || c == 'Ш' || c == 'Щ' || c == 'Ю') return 7;
        // Значки и спецсимволы юникода (✌, ⚔, ✦, ⏱, ⚖, ☠, ❤, ⚑, ◆, ▸, ☯ и т.д.) — 8px глиф + 2px отступ
        if (c >= 0x2000 && c <= 0x2BFF) return 10;
        // ItemsAdder / кастомные шрифты из ресурс-пака (Private Use Area: E000-F8FF, scale_ratio: 9)
        if (c >= 0xE000 && c <= 0xF8FF) return 9;
        // Стандартные символы ASCII и кириллицы (а-я, А-Я, 0-9, A-Z, a-z и т.д.)
        return 6;
    }

    /**
     * Центрирует строку относительно целевой пиксельной ширины контента с автоматическим
     * добавлением отступов слева и справа, чтобы нижняя строка (и значки) не съедались
     * краями скорборда, даже если плейсхолдеры короткие.
     * Использует неразрывные пробелы (\u00A0), которые клиент Minecraft гарантированно
     * учитывает при расчёте ширины окна скорборда и никогда не обрезает в конце строки.
     */
    public static String center(String legacyText, int targetPixelWidth) {
        return center(legacyText, targetPixelWidth, DEFAULT_MIN_PADDING_SPACES);
    }

    /**
     * Центрирует строку с указанным минимальным количеством пробелов отступа по бокам.
     */
    public static String center(String legacyText, int targetPixelWidth, int minPaddingSpaces) {
        if (legacyText == null || legacyText.isEmpty()) return "";
        int textWidth = getPixelWidth(legacyText);
        int minPaddingPx = Math.max(0, minPaddingSpaces) * 4;

        // Если контент плейсхолдеров короче нижней строки (или с учётом отступа),
        // целевой шириной становится сама нижняя строка + отступы с обеих сторон.
        int effectiveTargetWidth = Math.max(targetPixelWidth, textWidth + (minPaddingPx * 2));
        int diff = effectiveTargetWidth - textWidth;

        int leftSpaces = Math.max(minPaddingSpaces, (int) Math.round((diff / 2.0) / 4.0));
        // Гарантируем увеличенный отступ справа (неразрывные пробелы), чтобы клиент Minecraft
        // расширил фон скорборда и ни при каких условиях не обрезал правый край/значок
        int rightSpaces = Math.max(leftSpaces, minPaddingSpaces + 2);
        return "\u00A0".repeat(leftSpaces) + legacyText + "\u00A0".repeat(rightSpaces);
    }

    private Scoreboard createBoard(UUID uuid, Rendered rendered) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective("lt_sb", Criteria.DUMMY,
                LEGACY.deserialize(colorize(rendered.title())));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        lastTitles.put(uuid, rendered.title());
        prefixCaches.put(uuid, new LinePrefixCache(INVISIBLE.length));

        resizeBoard(board, uuid, 0, rendered.lines().size());
        refreshLines(board, uuid, rendered);
        return board;
    }

    /** Registers missing line teams, rewrites scores and hides lines that no longer exist. */
    private void resizeBoard(Scoreboard board, UUID uuid, int prevCount, int newCount) {
        Objective obj = board.getObjective("lt_sb");
        if (obj == null) return;
        LinePrefixCache cache = prefixCaches.computeIfAbsent(uuid, k -> new LinePrefixCache(INVISIBLE.length));
        int count = Math.min(newCount, INVISIBLE.length);
        for (int i = 0; i < count; i++) {
            String entry = INVISIBLE[i];
            if (board.getTeam("lt_" + i) == null) {
                Team team = board.registerNewTeam("lt_" + i);
                team.addEntry(entry);
                // Suffix never changes, so it is set once here instead of on every refresh.
                team.suffix(Component.empty());
            }
            obj.getScore(entry).setScore(newCount - i);
            obj.getScore(entry).numberFormat(NumberFormat.blank());
        }
        for (int i = count; i < Math.min(Math.max(prevCount, 0), INVISIBLE.length); i++) {
            board.resetScores(INVISIBLE[i]);
            cache.invalidate(i);
        }
    }

    private void refreshLines(Scoreboard board, UUID uuid, Rendered rendered) {
        Objective obj = board.getObjective("lt_sb");
        if (obj != null && !rendered.title().equals(lastTitles.get(uuid))) {
            obj.displayName(LEGACY.deserialize(colorize(rendered.title())));
            lastTitles.put(uuid, rendered.title());
        }
        LinePrefixCache cache = prefixCaches.computeIfAbsent(uuid, k -> new LinePrefixCache(INVISIBLE.length));
        List<String> lines = rendered.lines();
        for (int i = 0; i < lines.size() && i < INVISIBLE.length; i++) {
            // Lines are already PAPI-expanded; only colorize here.
            String processed = colorize(lines.get(i));
            if (!cache.update(i, processed)) continue;
            Team team = board.getTeam("lt_" + i);
            if (team != null) team.prefix(LEGACY.deserialize(processed));
        }
    }

    private String applyPAPI(Player player, String text) {
        if (papiIntegration != null) {
            try {
                return papiIntegration.apply(player, text);
            } catch (Exception ignored) {}
        }
        return text;
    }

    static String colorize(String text) {
        return text.replace('&', '§');
    }
}

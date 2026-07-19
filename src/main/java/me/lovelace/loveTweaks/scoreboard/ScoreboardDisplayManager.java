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

    public void updateScoreboard(Player player) {
        PlayerScoreboardState state = dataManager.getState(player.getUniqueId());

        if (!state.isScoreboardEnabled() || config.getDisabledWorlds().contains(player.getWorld().getName())) {
            removeScoreboard(player);
            return;
        }

        List<String> lines = buildLines(player, state);
        if (lines == null) {
            removeScoreboard(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        Scoreboard board = boards.get(uuid);
        int prevCount = lineCountCache.getOrDefault(uuid, -1);

        if (board == null || prevCount != lines.size()) {
            board = createBoard(player, lines);
            boards.put(uuid, board);
            lineCountCache.put(uuid, lines.size());
        } else {
            refreshLines(board, player, lines);
        }

        player.setScoreboard(board);
    }

    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        boards.remove(uuid);
        lineCountCache.remove(uuid);
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
        List<String> valid = new ArrayList<>();
        for (String id : state.getActivePlaceholders()) {
            ScoreboardPlaceholder ph = config.getPlaceholder(id);
            if (ph == null || !ph.isUnlocked(player)) continue;

            String expanded = applyPAPI(player, ph.template());
            if (expanded.isBlank()) continue;
            valid.add(expanded);
        }

        if (valid.isEmpty()) {
            state.ensureAutoDisable();
            return null;
        }

        String rawTop = applyPAPI(player, config.getTop());
        String rawBottom = applyPAPI(player, config.getBottom());

        // Ширина берётся из ВСЕХ строк — включая top/bottom, а не только плейсхолдеров.
        // Раньше top/bottom центрировались относительно ширины плейсхолдеров, поэтому если
        // сама строка top/bottom оказывалась шире всех плейсхолдеров, она вообще не получала
        // отступ (оставалась прижатой влево), а более короткие плейсхолдеры центрировались
        // по заведомо неверной (слишком маленькой) ширине. Учитывая top/bottom здесь, центр
        // всегда пересчитывается динамически от самой широкой строки скорборда.
        int widest = Math.max(visibleLength(rawTop), visibleLength(rawBottom));
        for (String line : valid) widest = Math.max(widest, visibleLength(line));

        String top = center(rawTop, widest);
        String bottom = center(rawBottom, widest);

        List<String> lines = new ArrayList<>();
        lines.add(top);
        String separator = config.getSeparator();
        for (int i = 0; i < valid.size(); i += 2) {
            lines.add(valid.get(i));
            if (i + 1 < valid.size()) lines.add(valid.get(i + 1));
            lines.add(separator);
        }
        lines.add(bottom);
        return lines;
    }

    // Matches both a plain legacy code (&a, §a, ...) and the hex-color sequence Bukkit uses
    // (&x&1&2&3&4&5&6 / §x§1§2§3§4§5§6) — the latter's leading "x" isn't a normal code char,
    // so without this it leaked into the visible-length count and threw off centering.
    private static final java.util.regex.Pattern COLOR_CODES = java.util.regex.Pattern.compile(
            "[&§]x([&§][0-9a-fA-F]){6}|[&§][0-9a-fk-orA-FK-OR]"
    );

    /** Visible character count of a legacy-colored string, ignoring `&`/`§` color codes. */
    private static int visibleLength(String legacyText) {
        return COLOR_CODES.matcher(legacyText).replaceAll("").length();
    }

    /** Pads a legacy-colored line with leading spaces so it centers against {@code targetWidth}. */
    private static String center(String legacyText, int targetWidth) {
        int pad = (targetWidth - visibleLength(legacyText)) / 2;
        return pad > 0 ? " ".repeat(pad) + legacyText : legacyText;
    }

    private Scoreboard createBoard(Player player, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Component titleComp = LEGACY.deserialize(colorize(applyPAPI(player, config.getTitle())));
        Objective obj = board.registerNewObjective("lt_sb", Criteria.DUMMY, titleComp);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < lines.size() && i < INVISIBLE.length; i++) {
            String entry = INVISIBLE[i];
            Team team = board.registerNewTeam("lt_" + i);
            team.addEntry(entry);
            setTeamPrefix(team, player, lines.get(i));
            obj.getScore(entry).setScore(lines.size() - i);
            obj.getScore(entry).numberFormat(NumberFormat.blank());
        }

        return board;
    }

    private void refreshLines(Scoreboard board, Player player, List<String> lines) {
        Objective obj = board.getObjective("lt_sb");
        if (obj != null) {
            obj.displayName(LEGACY.deserialize(colorize(applyPAPI(player, config.getTitle()))));
        }
        for (int i = 0; i < lines.size() && i < INVISIBLE.length; i++) {
            Team team = board.getTeam("lt_" + i);
            if (team != null) setTeamPrefix(team, player, lines.get(i));
        }
    }

    private void setTeamPrefix(Team team, Player player, String rawLine) {
        String processed = colorize(applyPAPI(player, rawLine));
        // Sidebar shows: prefix + entry (invisible) → only prefix is visible
        team.prefix(LEGACY.deserialize(processed));
        // Clear suffix so nothing trails after the invisible entry char
        team.suffix(Component.empty());
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

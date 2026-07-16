package me.lovelace.loveTweaks.scoreboard;

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

    List<String> buildLines(Player player, PlayerScoreboardState state) {
        List<String> lines = new ArrayList<>();
        List<String> active = state.getActivePlaceholders();

        if (active.isEmpty()) {
            lines.add(config.getFixedBottom());
            state.ensureAutoDisable();
            return lines;
        }

        lines.add("");

        int lastGroup = -1;
        int actualPlaceholdersAdded = 0;

        for (String phId : active) {
            ScoreboardPlaceholder ph = config.getPlaceholder(phId);
            if (ph == null) continue;

            if (!ph.conditionType().isMet(player)) {
                continue;
            }

            if (lastGroup != ph.sortGroup()) {
                if (lastGroup != -1) {
                    lines.add("");
                }
                lastGroup = ph.sortGroup();
            }

            String expanded = applyPAPI(player, ph.template());
            if (!expanded.isEmpty() && !expanded.equals(ph.template())) {
                lines.add(expanded);
                actualPlaceholdersAdded++;
            }
        }

        if (actualPlaceholdersAdded == 0) {
            lines.clear();
            lines.add("");
            state.ensureAutoDisable();
        } else {
            lines.add("");
        }

        lines.add(config.getFixedBottom());
        return lines;
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

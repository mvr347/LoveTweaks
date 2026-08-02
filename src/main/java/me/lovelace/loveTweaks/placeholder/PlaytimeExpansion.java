package me.lovelace.loveTweaks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Плейсхолдеры времени игры. Скорборд ссылается на %playtime_since_join%, но такого
 * расширения на сервере не было: PAPI отказывался грузить сторонние playertime/playerstats
 * из-за отсутствующих плагинов, и строка на табло оставалась нераскрытой.
 *
 * <p>Источник данных — ванильная статистика {@link Statistic#PLAY_ONE_MINUTE}. Вопреки имени,
 * она считает не минуты, а суммарные тики, проведённые на сервере, и доступна в том числе для
 * оффлайн-игроков.
 */
public final class PlaytimeExpansion extends PlaceholderExpansion {

    private static final long TICKS_PER_SECOND = 20L;

    private final LoveTweaks plugin;

    public PlaytimeExpansion(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playtime";
    }

    @Override
    public @NotNull String getAuthor() {
        // В plugin.yml автор не указан, поэтому пустой список подменяем — PAPI показывает
        // это поле в /papi info и пустая строка там выглядит как сбой.
        var authors = plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "Lovelace" : String.join(", ", authors);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Расширение живёт вместе с плагином и переживает /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return null;
        }

        return switch (params.toLowerCase()) {
            // Историческое имя из конфига скорборда: часы, проведённые на сервере.
            case "since_join", "hours" -> String.valueOf(playedSeconds(player) / 3600);
            case "minutes" -> String.valueOf(playedSeconds(player) / 60);
            case "seconds" -> String.valueOf(playedSeconds(player));
            case "formatted" -> formatDuration(playedSeconds(player));
            case "session" -> String.valueOf(sessionSeconds(player) / 60);
            case "session_formatted" -> formatDuration(sessionSeconds(player));
            case "first_join" -> firstJoin(player);
            default -> null;
        };
    }

    private long playedSeconds(OfflinePlayer player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_SECOND;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // Статистики может не быть — например, игрок ни разу не заходил.
            return 0L;
        }
    }

    /** Длительность текущей сессии; для оффлайн-игрока сессии нет. */
    private long sessionSeconds(OfflinePlayer player) {
        if (!player.isOnline() || player.getLastLogin() <= 0L) {
            return 0L;
        }
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - player.getLastLogin());
    }

    private String firstJoin(OfflinePlayer player) {
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0L) {
            return "—";
        }
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(firstPlayed));
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "ч " + minutes + "м";
        }
        if (minutes > 0) {
            return minutes + "м";
        }
        return totalSeconds + "с";
    }
}

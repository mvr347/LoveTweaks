package me.lovelace.loveTweaks.textures;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveTweaks.
 * <p>
 * Все base64-литералы текстур голов, зашитые в Java как значения по умолчанию для
 * {@code scoreboard.gui.*.material} (формат {@code basehead-<base64>}, см. {@link
 * me.lovelace.loveTweaks.utils.GuiItemUtil}), должны объявляться здесь, а не дублироваться по
 * месту использования — так плагин следует единой точке правды для GUI-текстур вместо
 * повторения одних и тех же строк в {@code load()} и {@code setGuiDefaults()}. Сами значения в
 * {@code config.yml} остаются как есть — это редактируемый админом конфиг, а не хардкод.
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    /** Кнопка профиля игрока (слот 0) в GUI настроек скорборда. */
    public static final String SCOREBOARD_PROFILE =
            HeadsConfig.get("scoreboard-profile", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MyNDJiZTA5MjI2YTNhZjlkYTM0YzZkMzA1YTEyMzA2M2FhZjQyMWFlZTAzZDk4M2MxYmY1MjE1YzQyMWU4In19fQ==");

    /** Кнопка «Назад» (слот 52) в GUI настроек скорборда. */
    public static final String SCOREBOARD_BACK =
            HeadsConfig.get("scoreboard-back", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==");

    /**
     * Кнопка «Закрыть» (слот 53) в GUI настроек скорборда. Также переиспользуется для
     * {@link #SCOREBOARD_PLACEHOLDER_BLOCKED} — в конфиге по умолчанию это один и тот же
     * значок (крестик).
     */
    public static final String SCOREBOARD_CLOSE =
            HeadsConfig.get("scoreboard-close", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==");

    /** Значок плейсхолдера, когда он включён (отображается на скорборде). */
    public static final String SCOREBOARD_PLACEHOLDER_ON =
            HeadsConfig.get("scoreboard-placeholder-on", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODg5MDgyNTQ1MWMwMWJkMDNiMDMwNzkwNjIxYWI3NTM0NDgzMTlmODQ3NDliYjAyYzkwZjNhMjg0ODliZDcyIn19fQ==");

    /** Значок плейсхолдера, когда он выключен. */
    public static final String SCOREBOARD_PLACEHOLDER_OFF =
            HeadsConfig.get("scoreboard-placeholder-off", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmZWY3NTNkNWI0ZmYyYTljYWU3NWJjMmVkZWIzMTUzMDI1YWJjNWNjMjc0NDI4NWYzMTk2NGY5NDA4YTFmIn19fQ==");

    /** Значок плейсхолдера, когда он заблокирован (не выполнен requirement или достигнут максимум). */
    public static final String SCOREBOARD_PLACEHOLDER_BLOCKED = SCOREBOARD_CLOSE;

    /** Кнопка «Вкл/Выкл» (слот 51), когда скорборд включён и есть что показывать. */
    public static final String SCOREBOARD_TOGGLE_ON =
            HeadsConfig.get("scoreboard-toggle-on", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2FmNmMzY2FjOTRjODk0NGQwNDQxNTBjMWRkNWU0ZTZhYjUzYTAxMzgyZGFlNDYzOTE0ZmIzYmU1YTI3MzE5ZCJ9fX0=");

    /** Кнопка «Вкл/Выкл», когда скорборд выключен вручную, но есть включённые плейсхолдеры. */
    public static final String SCOREBOARD_TOGGLE_OFF =
            HeadsConfig.get("scoreboard-toggle-off", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM1MzQ1MjkwZDdlNDRkZWEzYTg5MGQ0ZDAxNDBmYzEwYTUyYTkwOTc2NzQzOGYwZjViNWQyODc3Y2JhNDg0YyJ9fX0=");

    /** Кнопка «Вкл/Выкл», когда ни один плейсхолдер не включён — скорборду нечего показывать. */
    public static final String SCOREBOARD_TOGGLE_EMPTY =
            HeadsConfig.get("scoreboard-toggle-empty", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjNjMDA1NmI3YjI4MWZlMmQ0ZmRkNjc1NzdiMDI2ZWE3NDIyNmYzNjQ5YTFkNTBkYjI3NDI1YmNmYjRiMGE5YyJ9fX0=");
}

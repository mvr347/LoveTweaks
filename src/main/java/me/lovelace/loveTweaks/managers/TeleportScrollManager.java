package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер сессий телепортации через свиток.
 *
 * Жизненный цикл сессии:
 * 1. WAITING_FOR_INPUT  — ждём ник от игрока в чат (10 сек)
 * 2. COUNTING_DOWN      — обратный отсчёт 5 сек, проверяем движение/PVP/невидимость каждую секунду
 * 3. Успех или отмена   — сессия удаляется
 */
public class TeleportScrollManager {

    private static final int CHAT_TIMEOUT_TICKS = 200; // 10 секунд
    private static final int COUNTDOWN_SECONDS = 5;
    // Порог АФК: если игрок не двигался дольше этого времени — считается АФК
    private static final long AFK_THRESHOLD_MS = 15_000L;

    private final LoveTweaks plugin;

    // Активные сессии: UUID инициатора → сессия.
    // ConcurrentHashMap обязателен: onPlayerChat читает isWaitingForInput() из асинхронного
    // потока чата, пока главный поток параллельно пишет в эту же карту (put/remove) —
    // обычный HashMap в таких условиях даёт неопределённое поведение (порча структуры,
    // видимость изменений между потоками), что и приводило к "залипанию" сессий.
    private final Map<UUID, TeleportSession> sessions = new ConcurrentHashMap<>();
    // Время последнего реального движения по позиции (не поворота камеры)
    private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();

    public TeleportScrollManager(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    /** Обновляет время последнего движения. Вызывается из listener'а при PlayerMoveEvent. */
    public void updateMoveTime(UUID uuid) {
        lastMoveTime.put(uuid, System.currentTimeMillis());
    }

    /** Регистрирует игрока при входе с текущим временем, чтобы только что зашедший не считался АФК. */
    public void registerJoin(UUID uuid) {
        lastMoveTime.put(uuid, System.currentTimeMillis());
    }

    public void removePlayer(UUID uuid) {
        lastMoveTime.remove(uuid);
    }

    private Component msg(String key) {
        return GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getTeleportScrollConfig().message(key));
    }

    private Component msg(String key, String placeholder, String value) {
        return GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getTeleportScrollConfig().message(key).replace(placeholder, value));
    }

    private boolean isAfk(Player player) {
        Long last = lastMoveTime.get(player.getUniqueId());
        if (last == null) return false;
        return (System.currentTimeMillis() - last) >= AFK_THRESHOLD_MS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Запускает фазу ожидания ввода ника.
     * Вызывается из TeleportScrollListener при ПКМ со свитком.
     */
    public void startInputPhase(Player initiator) {
        // Если у игрока уже есть активная сессия — отменяем старую
        cancelSession(initiator.getUniqueId(), null, null);

        TeleportSession session = new TeleportSession(initiator.getUniqueId());
        sessions.put(initiator.getUniqueId(), session);

        initiator.sendActionBar(msg("prompt"));

        // Таймер отмены если игрок не ввёл ник
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeleportSession s = sessions.get(initiator.getUniqueId());
            // Проверяем, что сессия всё ещё в фазе ожидания (не перешла в отсчёт)
            if (s != null && s.isWaitingForInput()) {
                sessions.remove(initiator.getUniqueId());
                Player p = Bukkit.getPlayer(initiator.getUniqueId());
                if (p != null) {
                    p.sendActionBar(msg("timeout"));
                }
            }
        }, CHAT_TIMEOUT_TICKS);

        session.setChatTimeoutTask(timeoutTask);
    }

    /**
     * Обрабатывает введённый ник из чата.
     * Возвращает true если сессия для этого игрока была активна (чтобы listener мог отменить событие).
     */
    public boolean handleChatInput(Player initiator, String input) {
        TeleportSession session = sessions.get(initiator.getUniqueId());
        if (session == null || !session.isWaitingForInput()) {
            return false;
        }

        // Отменяем таймер ожидания ввода
        session.cancelChatTimeoutTask();

        Player target = Bukkit.getPlayerExact(input.trim());

        if (target == null || !target.isOnline()) {
            sessions.remove(initiator.getUniqueId());
            initiator.sendActionBar(msg("player-not-found", "<player>", input.trim()));
            return true;
        }

        if (target.getUniqueId().equals(initiator.getUniqueId())) {
            sessions.remove(initiator.getUniqueId());
            initiator.sendActionBar(msg("cannot-target-self"));
            return true;
        }

        if (isAfk(target)) {
            sessions.remove(initiator.getUniqueId());
            initiator.sendActionBar(msg("target-afk", "<player>", target.getName()));
            return true;
        }

        // Фиксируем начальные позиции для проверки движения
        session.beginCountdown(target.getUniqueId(), initiator.getLocation(), target.getLocation());
        startCountdown(initiator, target, session);

        return true;
    }

    /**
     * Возвращает true, если для данного UUID есть активная сессия в фазе ожидания ввода.
     * Используется listener'ом чтобы решить, отменять ли chat event.
     */
    public boolean isWaitingForInput(UUID uuid) {
        TeleportSession s = sessions.get(uuid);
        return s != null && s.isWaitingForInput();
    }

    /**
     * Отменяет все активные задачи и удаляет сессию.
     * @param reason  null — тихая отмена (например, при логауте)
     */
    public void cancelSession(UUID initiatorUuid, String reasonForInitiator, String reasonForTarget) {
        TeleportSession session = sessions.remove(initiatorUuid);
        if (session == null) {
            return;
        }
        session.cancelAllTasks();

        if (reasonForInitiator != null) {
            Player initiator = Bukkit.getPlayer(initiatorUuid);
            if (initiator != null) {
                initiator.sendActionBar(GuiItemUtil.colorize(reasonForInitiator));
            }
        }

        if (reasonForTarget != null && session.getTargetUuid() != null) {
            Player target = Bukkit.getPlayer(session.getTargetUuid());
            if (target != null) {
                target.sendActionBar(GuiItemUtil.colorize(reasonForTarget));
            }
        }
    }

    /**
     * Проверяет, есть ли у данного UUID активная сессия (в любой фазе).
     * Используется при логауте игрока чтобы отменить его сессию.
     */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Отменяет все сессии, у которых данный UUID является целью телепортации (игрок 2).
     * Нужно вызывать при выходе игрока с сервера, чтобы отсчёт не продолжал ждать/сверять
     * позицию офлайн-игрока до следующей проверки — сессия закрывается немедленно.
     */
    public void cancelSessionsTargeting(UUID targetUuid) {
        for (Map.Entry<UUID, TeleportSession> entry : sessions.entrySet()) {
            if (targetUuid.equals(entry.getValue().getTargetUuid())) {
                cancelSession(entry.getKey(), plugin.getLoveTweaksConfig().getTeleportScrollConfig().message("cancelled-target"), null);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренняя логика
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Запускает отсчёт как цепочку одноразовых {@code runTaskLater}-задач, а не повторяющийся
     * {@code runTaskTimer}. Каждый шаг сам планирует следующий только если сессия всё ещё
     * активна — если что-то пойдёт не так с отменой (например, сессия удалена из map, но
     * ссылка на BukkitTask почему-то не была отменена), цепочка просто не продолжится сама
     * по себе, в отличие от повторяющегося таймера, который тикает независимо от состояния
     * сессии, пока его явно не cancel()-нуть. Это исключает саму возможность "бесконечной"
     * телепортации из-за незакрытой задачи.
     */
    private void startCountdown(Player initiator, Player target, TeleportSession session) {
        final UUID initiatorUuid = initiator.getUniqueId();
        final UUID targetUuid = target.getUniqueId();
        final String initiatorName = initiator.getName();

        scheduleCountdownStep(initiatorUuid, targetUuid, initiatorName, session, COUNTDOWN_SECONDS);
    }

    private void scheduleCountdownStep(UUID initiatorUuid, UUID targetUuid, String initiatorName,
                                        TeleportSession session, int secondsLeft) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
                runCountdownStep(initiatorUuid, targetUuid, initiatorName, secondsLeft), 20L);
        session.setCountdownTask(task);
    }

    private void runCountdownStep(UUID initiatorUuid, UUID targetUuid, String initiatorName, int secondsLeft) {
        // Проверяем что сессия всё ещё актуальна (могла быть отменена, например, при выходе игрока)
        TeleportSession current = sessions.get(initiatorUuid);
        if (current == null) {
            return;
        }

        Player p = Bukkit.getPlayer(initiatorUuid);
        Player t = Bukkit.getPlayer(targetUuid);

        // Один из игроков вышел с сервера
        if (p == null || t == null) {
            cancelSession(initiatorUuid, null, null);
            return;
        }

        // Проверяем условия каждую секунду
        String failReason = checkConditions(p, t, current);
        if (failReason != null) {
            var tsConfig = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            cancelSession(initiatorUuid,
                    tsConfig.message("cancelled-initiator").replace("<reason>", failReason),
                    tsConfig.message("cancelled-target"));
            return;
        }

        // Обновляем позиции для следующей проверки движения
        current.updatePositions(p.getLocation(), t.getLocation());

        if (secondsLeft <= 0) {
            // Время вышло — телепортируем
            performTeleport(p, t, current, initiatorUuid);
            return;
        }

        // Отображаем таймер обоим игрокам
        p.sendActionBar(msg("countdown-initiator", "<seconds>", String.valueOf(secondsLeft)));
        t.sendActionBar(msg("countdown-target", "<player>", initiatorName));

        // Планируем следующий шаг только пока сессия жива — цепочка не может продолжиться сама по себе
        scheduleCountdownStep(initiatorUuid, targetUuid, initiatorName, current, secondsLeft - 1);
    }

    private void performTeleport(Player initiator, Player target, TeleportSession session, UUID initiatorUuid) {
        // Финальная проверка условий перед телепортацией
        String failReason = checkConditions(initiator, target, session);
        if (failReason != null) {
            var tsConfig = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            cancelSession(initiatorUuid,
                    tsConfig.message("cancelled-initiator").replace("<reason>", failReason),
                    tsConfig.message("cancelled-target"));
            return;
        }

        sessions.remove(initiatorUuid);
        session.cancelAllTasks();

        // Телепортируем на главном потоке (мы уже на нём, т.к. runTaskLater)
        initiator.teleport(target.getLocation());

        // Убираем свиток из руки
        removeScrollFromHand(initiator);

        initiator.sendActionBar(msg("success-initiator"));
        target.sendActionBar(msg("success-target", "<player>", initiator.getName()));
    }

    /**
     * Проверяет все условия для телепортации.
     * @return строка с причиной отказа или null если всё ок
     */
    private String checkConditions(Player initiator, Player target, TeleportSession session) {
        var tsConfig = plugin.getLoveTweaksConfig().getTeleportScrollConfig();

        // Проверка движения — сравниваем с позицией прошлой секунды
        if (hasMoved(initiator.getLocation(), session.getLastInitiatorLocation())) {
            return tsConfig.message("reason-moved-self");
        }
        if (hasMoved(target.getLocation(), session.getLastTargetLocation())) {
            return tsConfig.message("reason-moved-target").replace("<player>", target.getName());
        }

        // Проверка невидимости
        if (initiator.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return tsConfig.message("reason-invisible-self");
        }
        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return tsConfig.message("reason-invisible-target").replace("<player>", target.getName());
        }

        if (isInCombat(initiator)) {
            return tsConfig.message("reason-pvp-self");
        }
        if (isInCombat(target)) {
            return tsConfig.message("reason-pvp-target").replace("<player>", target.getName());
        }

        return null;
    }

    /**
     * Находится ли игрок в бою. Если LoveCore установлен, спрашиваем его {@code CombatState} —
     * он знает и про метку стороннего боевого плагина, и про войну/осаду, чего одна метка не
     * знает. Ядра нет или служба ещё не поднялась — падаем на прежнюю проверку метки
     * {@code in_combat}, которой DeluxeCombat и большинство combat-плагинов помечают игроков.
     */
    private boolean isInCombat(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                java.util.Optional<Boolean> fromCore = dev.lovelace.lovecore.api.LoveCore
                        .service(dev.lovelace.lovecore.api.combat.CombatState.class)
                        .map(state -> state.inCombat(player.getUniqueId()));
                if (fromCore.isPresent()) {
                    return fromCore.get();
                }
            } catch (Throwable ignored) {
                // Ядро есть, но служба ещё не поднялась или контракт изменился — падаем на метку.
            }
        }
        return hasCombatMetadata(player);
    }

    private boolean hasCombatMetadata(Player player) {
        List<MetadataValue> values = player.getMetadata("in_combat");
        for (MetadataValue value : values) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Сравнивает блочные координаты двух Location.
     * Дробные части намеренно игнорируем — небольшое покачивание камеры
     * без смены блока не должно отменять телепортацию.
     */
    private boolean hasMoved(Location current, Location last) {
        if (last == null) {
            return false;
        }
        // Сравниваем с точностью до блока (floor), чтобы микродвижения не мешали
        return current.getBlockX() != last.getBlockX()
                || current.getBlockY() != last.getBlockY()
                || current.getBlockZ() != last.getBlockZ();
    }

    /**
     * Убирает свиток из основной руки игрока.
     * Так как maxStackSize = 1, просто очищаем слот.
     */
    private void removeScrollFromHand(Player player) {
        player.getInventory().setItemInMainHand(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренний класс — данные одной сессии
    // ─────────────────────────────────────────────────────────────────────────

    private static class TeleportSession {

        private final UUID initiatorUuid;
        private UUID targetUuid;

        // Позиции прошлой секунды для проверки движения
        private Location lastInitiatorLocation;
        private Location lastTargetLocation;

        // true пока ждём ввода ника в чат, false после начала отсчёта
        private boolean waitingForInput = true;

        private BukkitTask chatTimeoutTask;
        private BukkitTask countdownTask;

        TeleportSession(UUID initiatorUuid) {
            this.initiatorUuid = initiatorUuid;
        }

        void beginCountdown(UUID targetUuid, Location initiatorLoc, Location targetLoc) {
            this.targetUuid = targetUuid;
            this.lastInitiatorLocation = initiatorLoc;
            this.lastTargetLocation = targetLoc;
            this.waitingForInput = false;
        }

        void updatePositions(Location initiatorLoc, Location targetLoc) {
            this.lastInitiatorLocation = initiatorLoc;
            this.lastTargetLocation = targetLoc;
        }

        boolean isWaitingForInput() {
            return waitingForInput;
        }

        UUID getTargetUuid() {
            return targetUuid;
        }

        Location getLastInitiatorLocation() {
            return lastInitiatorLocation;
        }

        Location getLastTargetLocation() {
            return lastTargetLocation;
        }

        void setChatTimeoutTask(BukkitTask task) {
            this.chatTimeoutTask = task;
        }

        void setCountdownTask(BukkitTask task) {
            this.countdownTask = task;
        }

        void cancelChatTimeoutTask() {
            if (chatTimeoutTask != null && !chatTimeoutTask.isCancelled()) {
                chatTimeoutTask.cancel();
            }
        }

        void cancelAllTasks() {
            cancelChatTimeoutTask();
            if (countdownTask != null && !countdownTask.isCancelled()) {
                countdownTask.cancel();
            }
        }
    }
}

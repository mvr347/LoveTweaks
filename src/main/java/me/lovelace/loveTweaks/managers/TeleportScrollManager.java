package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // Активные сессии: UUID инициатора → сессия
    private final Map<UUID, TeleportSession> sessions = new HashMap<>();
    // Время последнего реального движения по позиции (не поворота камеры)
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();

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

        initiator.sendActionBar(
                Component.text("✦ Введите ник игрока в чат ", NamedTextColor.GOLD)
                        .append(Component.text("(10 секунд)", NamedTextColor.YELLOW))
        );

        // Таймер отмены если игрок не ввёл ник
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeleportSession s = sessions.get(initiator.getUniqueId());
            // Проверяем, что сессия всё ещё в фазе ожидания (не перешла в отсчёт)
            if (s != null && s.isWaitingForInput()) {
                sessions.remove(initiator.getUniqueId());
                Player p = Bukkit.getPlayer(initiator.getUniqueId());
                if (p != null) {
                    p.sendActionBar(Component.text("✗ Время вышло!", NamedTextColor.RED));
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
            initiator.sendActionBar(
                    Component.text("✗ Игрок ", NamedTextColor.RED)
                            .append(Component.text(input.trim(), NamedTextColor.YELLOW))
                            .append(Component.text(" не найден или не в сети!", NamedTextColor.RED))
            );
            return true;
        }

        if (target.getUniqueId().equals(initiator.getUniqueId())) {
            sessions.remove(initiator.getUniqueId());
            initiator.sendActionBar(Component.text("✗ Нельзя телепортироваться к себе!", NamedTextColor.RED));
            return true;
        }

        if (isAfk(target)) {
            sessions.remove(initiator.getUniqueId());
            initiator.sendActionBar(
                    Component.text("✗ Игрок ", NamedTextColor.RED)
                            .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" АФК!", NamedTextColor.RED))
            );
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
                initiator.sendActionBar(Component.text("✗ " + reasonForInitiator, NamedTextColor.RED));
            }
        }

        if (reasonForTarget != null && session.getTargetUuid() != null) {
            Player target = Bukkit.getPlayer(session.getTargetUuid());
            if (target != null) {
                target.sendActionBar(Component.text("✗ Телепортация отменена!", NamedTextColor.RED));
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

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренняя логика
    // ─────────────────────────────────────────────────────────────────────────

    private void startCountdown(Player initiator, Player target, TeleportSession session) {
        final UUID initiatorUuid = initiator.getUniqueId();
        final UUID targetUuid = target.getUniqueId();
        final String initiatorName = initiator.getName();

        // Используем int[] для изменяемого счётчика внутри лямбды
        final int[] secondsLeft = {COUNTDOWN_SECONDS};

        BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Проверяем что сессия всё ещё актуальна
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
                cancelSession(initiatorUuid, "Телепортация отменена! " + failReason, "Телепортация отменена!");
                return;
            }

            // Обновляем позиции для следующей проверки движения
            current.updatePositions(p.getLocation(), t.getLocation());

            if (secondsLeft[0] <= 0) {
                // Время вышло — телепортируем
                performTeleport(p, t, current, initiatorUuid);
                return;
            }

            // Отображаем таймер обоим игрокам
            p.sendActionBar(
                    Component.text("✦ Телепортация через ", NamedTextColor.GOLD)
                            .append(Component.text(secondsLeft[0] + " сек", NamedTextColor.YELLOW))
                            .append(Component.text("... Не двигайтесь!", NamedTextColor.GOLD))
            );
            t.sendActionBar(
                    Component.text("✦ К вам телепортируется ", NamedTextColor.GOLD)
                            .append(Component.text(initiatorName, NamedTextColor.YELLOW))
                            .append(Component.text("... Не двигайтесь!", NamedTextColor.GOLD))
            );

            secondsLeft[0]--;

        }, 1L, 20L); // Небольшая задержка 1 тик чтобы countdownTask был присвоен до первого срабатывания

        session.setCountdownTask(countdownTask);
    }

    private void performTeleport(Player initiator, Player target, TeleportSession session, UUID initiatorUuid) {
        // Финальная проверка условий перед телепортацией
        String failReason = checkConditions(initiator, target, session);
        if (failReason != null) {
            cancelSession(initiatorUuid, "Телепортация отменена! " + failReason, "Телепортация отменена!");
            return;
        }

        sessions.remove(initiatorUuid);
        session.cancelAllTasks();

        // Телепортируем на главном потоке (мы уже на нём, т.к. runTaskTimer)
        initiator.teleport(target.getLocation());

        // Убираем свиток из руки
        removeScrollFromHand(initiator);

        initiator.sendActionBar(Component.text("✦ Телепортация выполнена!", NamedTextColor.GREEN));
        target.sendActionBar(
                Component.text(initiator.getName() + " ", NamedTextColor.YELLOW)
                        .append(Component.text("телепортировался к вам!", NamedTextColor.GREEN))
        );
    }

    /**
     * Проверяет все условия для телепортации.
     * @return строка с причиной отказа или null если всё ок
     */
    private String checkConditions(Player initiator, Player target, TeleportSession session) {
        // Проверка движения — сравниваем с позицией прошлой секунды
        if (hasMoved(initiator.getLocation(), session.getLastInitiatorLocation())) {
            return "(вы двигались)";
        }
        if (hasMoved(target.getLocation(), session.getLastTargetLocation())) {
            return "(" + target.getName() + " двигался)";
        }

        // Проверка невидимости
        if (initiator.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return "(вы в невидимости)";
        }
        if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return "(" + target.getName() + " в невидимости)";
        }

        // Мягкая интеграция с DeluxeCombat через Bukkit Metadata
        // DeluxeCombat помечает игроков в PVP через MetadataValue с ключом "in_combat"
        if (isInCombat(initiator)) {
            return "(вы в PvP)";
        }
        if (isInCombat(target)) {
            return "(" + target.getName() + " в PvP)";
        }

        return null;
    }

    /**
     * Проверяет, находится ли игрок в PvP-бою.
     * Работает через Bukkit Metadata — DeluxeCombat и большинство combat-плагинов
     * помечают игроков ключом "in_combat" при входе в схватку.
     */
    private boolean isInCombat(Player player) {
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

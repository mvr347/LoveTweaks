package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.CombatUtil;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
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
    // Активные сессии: UUID инициатора → сессия.
    private final Map<UUID, TeleportSession> sessions = new ConcurrentHashMap<>();
    // Время последнего реального движения по позиции (не поворота камеры) — bounded expiring cache
    private final com.github.benmanes.caffeine.cache.Cache<UUID, Long> lastMoveTime =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(java.time.Duration.ofMinutes(30))
                    .build();

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
        lastMoveTime.invalidate(uuid);
    }

    public void cancelAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            cancelSession(id, null, null);
        }
        lastMoveTime.invalidateAll();
    }

    private Component msg(String key) {
        return GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getTeleportScrollConfig().message(key));
    }

    private Component msg(String key, String placeholder, String value) {
        return GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getTeleportScrollConfig().message(key).replace(placeholder, value));
    }

    /**
     * Заменяет прямые вызовы player.sendActionBar(...) — уважает переключатель LoveNotify.
     * Не кэшируем Optional<LoveNotify> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore.
     */
    private void sendActionBar(Player player, Component component) {
        boolean allowed = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.notify.LoveNotify.class)
                .map(n -> n.isChannelEnabled(player.getUniqueId(), dev.lovelace.lovecore.api.notify.LoveNotify.Channel.ACTION_BAR))
                .orElse(true);
        if (allowed) {
            player.sendActionBar(component);
        }
    }

    private boolean isAfk(Player player) {
        Long last = lastMoveTime.getIfPresent(player.getUniqueId());
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
        if (isInCombat(initiator)) {
            var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            Component c = GuiItemUtil.colorize(cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-pvp-self")));
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
            return;
        }

        if (initiator.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            var cfg = plugin.getLoveTweaksConfig().getTeleportScrollConfig();
            Component c = GuiItemUtil.colorize(cfg.message("cancelled-initiator").replace("<reason>", cfg.message("reason-invisible-self")));
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
            return;
        }

        // Если у игрока уже есть активная сессия — отменяем старую
        cancelSession(initiator.getUniqueId(), null, null);

        TeleportSession session = new TeleportSession(initiator.getUniqueId());
        sessions.put(initiator.getUniqueId(), session);

        sendActionBar(initiator, msg("prompt"));
        initiator.sendMessage(msg("prompt"));
        try {
            initiator.playSound(initiator.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        } catch (Throwable ignored) {}

        // Таймер отмены если игрок не ввёл ник
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TeleportSession s = sessions.get(initiator.getUniqueId());
            // Проверяем, что сессия всё ещё в фазе ожидания (не перешла в отсчёт)
            if (s != null && s.isWaitingForInput()) {
                sessions.remove(initiator.getUniqueId());
                Player p = Bukkit.getPlayer(initiator.getUniqueId());
                if (p != null) {
                    p.sendMessage(msg("timeout"));
                    sendActionBar(p, msg("timeout"));
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
            Component c = msg("player-not-found", "<player>", input.trim());
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
            return true;
        }

        if (target.getUniqueId().equals(initiator.getUniqueId())) {
            sessions.remove(initiator.getUniqueId());
            Component c = msg("cannot-target-self");
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
            return true;
        }

        // Проверяем, что цель находится в мире 'world'
        if (!target.getWorld().getName().equalsIgnoreCase("world")) {
            sessions.remove(initiator.getUniqueId());
            Component c = msg("target-not-in-world", "<player>", target.getName());
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
            return true;
        }

        if (isAfk(target)) {
            sessions.remove(initiator.getUniqueId());
            Component c = msg("target-afk", "<player>", target.getName());
            initiator.sendMessage(c);
            sendActionBar(initiator, c);
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
                Component comp = GuiItemUtil.colorize(reasonForInitiator);
                sendActionBar(initiator, comp);
                initiator.sendMessage(comp);
                try {
                    initiator.playSound(initiator.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                } catch (Throwable ignored) {}
                spawnCancelParticles(initiator.getLocation());
            }
        }

        if (reasonForTarget != null && session.getTargetUuid() != null) {
            Player target = Bukkit.getPlayer(session.getTargetUuid());
            if (target != null) {
                Component comp = GuiItemUtil.colorize(reasonForTarget);
                sendActionBar(target, comp);
                target.sendMessage(comp);
                try {
                    target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                } catch (Throwable ignored) {}
                spawnCancelParticles(target.getLocation());
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
     * Запускает отсчёт с немедленным уведомлением цели и инициатора, а также визуальными эффектами.
     */
    private void startCountdown(Player initiator, Player target, TeleportSession session) {
        final UUID initiatorUuid = initiator.getUniqueId();
        final UUID targetUuid = target.getUniqueId();
        final String initiatorName = initiator.getName();

        // 1. Уведомление инициатора
        Component initMsg = msg("countdown-initiator", "<seconds>", String.valueOf(COUNTDOWN_SECONDS));
        sendActionBar(initiator, initMsg);
        initiator.sendMessage(initMsg);

        // 2. ОБЯЗАТЕЛЬНОЕ уведомление игрока-цели (в чат и экшнбар)
        Component targetMsg = msg("countdown-target", "<player>", initiatorName);
        sendActionBar(target, targetMsg);
        target.sendMessage(targetMsg);

        // 3. Звуки запуска отсчёта
        try {
            initiator.playSound(initiator.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
            target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        } catch (Throwable ignored) {}

        // 4. Визуальные частицы вокруг обоих игроков
        spawnCountdownParticles(initiator.getLocation());
        spawnCountdownParticles(target.getLocation());

        scheduleCountdownStep(initiatorUuid, targetUuid, initiatorName, session, COUNTDOWN_SECONDS - 1);
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
        sendActionBar(p, msg("countdown-initiator", "<seconds>", String.valueOf(secondsLeft)));
        sendActionBar(t, msg("countdown-target", "<player>", initiatorName));

        // Звуки тика и визуальные частицы ауры
        try {
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
            t.playSound(t.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
        } catch (Throwable ignored) {}

        spawnCountdownParticles(p.getLocation());
        spawnCountdownParticles(t.getLocation());

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

        Location fromLoc = initiator.getLocation().clone();
        Location toLoc = target.getLocation().clone();

        // Визуальный и звуковой эффект на месте отправления
        spawnTeleportBurst(fromLoc);
        try {
            fromLoc.getWorld().playSound(fromLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        } catch (Throwable ignored) {}

        // Телепортируем на главном потоке (мы уже на нём, т.к. runTaskLater)
        initiator.teleport(toLoc);

        // Визуальный и звуковой эффект на месте прибытия
        spawnTeleportBurst(toLoc);
        try {
            toLoc.getWorld().playSound(toLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            toLoc.getWorld().playSound(toLoc, org.bukkit.Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.4f);
            toLoc.getWorld().playSound(toLoc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        } catch (Throwable ignored) {}

        // Убираем свиток из руки
        removeScrollFromHand(initiator);

        Component succInit = msg("success-initiator");
        sendActionBar(initiator, succInit);
        initiator.sendMessage(succInit);

        // ОБЯЗАТЕЛЬНОЕ уведомление цели телепортации в чат и экшнбар
        Component succTarg = msg("success-target", "<player>", initiator.getName());
        sendActionBar(target, succTarg);
        target.sendMessage(succTarg);
    }

    private void spawnCountdownParticles(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        org.bukkit.World w = loc.getWorld();
        for (int i = 0; i < 12; i++) {
            double angle = (2 * Math.PI * i) / 12.0;
            double x = Math.cos(angle) * 0.75;
            double z = Math.sin(angle) * 0.75;
            w.spawnParticle(org.bukkit.Particle.PORTAL, loc.clone().add(x, 0.2 + (i * 0.12), z), 1, 0, 0, 0, 0);
            w.spawnParticle(org.bukkit.Particle.ENCHANT, loc.clone().add(x, 0.4, z), 1, 0, 0, 0, 0.1);
        }
    }

    private void spawnTeleportBurst(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        org.bukkit.World w = loc.getWorld();
        Location center = loc.clone().add(0, 1.0, 0);
        w.spawnParticle(org.bukkit.Particle.PORTAL, center, 45, 0.5, 0.7, 0.5, 0.8);
        w.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, center, 25, 0.4, 0.6, 0.4, 0.1);
        w.spawnParticle(org.bukkit.Particle.ENCHANT, center, 35, 0.6, 0.8, 0.6, 0.3);
        w.spawnParticle(org.bukkit.Particle.FLASH, center, 1, 0, 0, 0, 0);
    }

    private void spawnCancelParticles(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        org.bukkit.World w = loc.getWorld();
        w.spawnParticle(org.bukkit.Particle.SMOKE, loc.clone().add(0, 1.0, 0), 15, 0.3, 0.4, 0.3, 0.05);
    }

    /**
     * Проверяет все условия для телепортации.
     * @return строка с причиной отказа или null если всё ок
     */
    private String checkConditions(Player initiator, Player target, TeleportSession session) {
        var tsConfig = plugin.getLoveTweaksConfig().getTeleportScrollConfig();

        // Проверка наличия свитка в руке (защита от перекладывания в сундук во время отсчёта)
        if (!me.lovelace.loveTweaks.items.TeleportScroll.isScroll(initiator.getInventory().getItemInMainHand())) {
            return tsConfig.message("reason-dropped");
        }

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

        // Проверка мира цели — цель должна оставаться в мире world
        if (!target.getWorld().getName().equalsIgnoreCase("world")) {
            return tsConfig.message("reason-wrong-world").replace("<player>", target.getName());
        }

        return null;
    }

    /** Находится ли игрок в бою — общая проверка, см. {@link CombatUtil#isInCombat(Player)}. */
    private boolean isInCombat(Player player) {
        return CombatUtil.isInCombat(player);
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
        if (current.getWorld() == null || last.getWorld() == null || !current.getWorld().equals(last.getWorld())) {
            return true;
        }
        // Сравниваем с точностью до блока (floor), чтобы микродвижения не мешали
        return current.getBlockX() != last.getBlockX()
                || current.getBlockY() != last.getBlockY()
                || current.getBlockZ() != last.getBlockZ();
    }

    /**
     * Убирает свиток из основной руки игрока.
     */
    private void removeScrollFromHand(Player player) {
        var item = player.getInventory().getItemInMainHand();
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(item);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }
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

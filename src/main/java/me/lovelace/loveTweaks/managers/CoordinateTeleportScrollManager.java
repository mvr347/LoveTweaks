package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.CoordinateScrollDefinition;
import me.lovelace.loveTweaks.utils.CombatUtil;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер координатного свитка телепортации: ПКМ → отсчёт {@code cast-time-seconds}
 * (отменяется движением/невидимостью/PvP — та же защита от побега с боя, что и у обычного
 * свитка через {@link TeleportScrollManager}) → телепортация к точке из конфига.
 *
 * Точка назначения проверяется на лимиты безопасности (мин/макс Y, граница мира, дистанция
 * от спавна, разрешённые миры) ДВАЖДЫ: перед стартом отсчёта (мгновенный отказ игроку) и
 * ещё раз прямо перед самой телепортацией (на случай если конфиг перезагрузили или граница
 * мира сдвинулась за время отсчёта) — так лимиты нельзя обойти, просто удерживая свиток
 * во время live-reload.
 */
public class CoordinateTeleportScrollManager {

    private final LoveTweaks plugin;

    // Активные отсчёты: UUID игрока → сессия.
    private final Map<UUID, CastSession> sessions = new ConcurrentHashMap<>();
    // Время последнего успешного использования каждого свитка каждым игроком — для кулдауна.
    private final Map<UUID, Map<String, Long>> lastUse = new ConcurrentHashMap<>();

    public CoordinateTeleportScrollManager(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    private CoordinateTeleportScrollConfig cfg() {
        return plugin.getLoveTweaksConfig().getCoordTeleportScrollConfig();
    }

    private Component msg(String key) {
        return GuiItemUtil.colorize(cfg().message(key));
    }

    private Component msg(String key, String placeholder, String value) {
        return GuiItemUtil.colorize(cfg().message(key).replace(placeholder, value));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный API
    // ─────────────────────────────────────────────────────────────────────────

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Запускает использование свитка: проверяет все guard rail'ы и, если всё в порядке,
     * стартует отсчёт. Вызывается из {@code CoordinateTeleportScrollListener} при ПКМ.
     */
    public void startCast(Player player, String scrollId) {
        CoordinateTeleportScrollConfig config = cfg();

        if (!config.isEnabled()) {
            return;
        }

        CoordinateScrollDefinition def = config.getScroll(scrollId);
        if (def == null) {
            // Свиток был выдан со старой версией конфига и его id больше не существует.
            player.sendActionBar(msg("scroll-unknown"));
            return;
        }

        if (def.hasPermissionNode() && !player.hasPermission(def.permission())) {
            player.sendActionBar(msg("no-permission"));
            return;
        }

        long remainingCooldown = remainingCooldownSeconds(player.getUniqueId(), scrollId, config.getCooldownSeconds());
        if (remainingCooldown > 0) {
            player.sendActionBar(msg("on-cooldown", "<seconds>", String.valueOf(remainingCooldown)));
            return;
        }

        String failReason = validateDestination(def, config);
        if (failReason != null) {
            player.sendActionBar(msg("destination-blocked"));
            plugin.getLogger().warning("[coord-teleport-scroll] Точка '" + scrollId + "' не прошла проверку лимитов ("
                    + failReason + ") — телепортация для " + player.getName() + " отклонена.");
            return;
        }

        // Если у игрока уже есть активный отсчёт по любому свитку — отменяем старый молча.
        cancelSession(player.getUniqueId(), null);

        CastSession session = new CastSession(scrollId, player.getLocation());
        sessions.put(player.getUniqueId(), session);

        if (config.getCastTimeSeconds() <= 0) {
            // Мгновенная телепортация, если отсчёт отключён в конфиге (cast-time-seconds: 0).
            performTeleport(player, session);
            return;
        }

        scheduleCountdownStep(player.getUniqueId(), session, config.getCastTimeSeconds());
    }

    /**
     * Отменяет активный отсчёт (например, при выходе игрока или смене предмета в руке).
     * @param reasonKey  null — тихая отмена, иначе ключ сообщения из {@code messages.reason-*}
     */
    public void cancelSession(UUID uuid, String reasonKey) {
        CastSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        session.cancelTask();

        if (reasonKey != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendActionBar(GuiItemUtil.colorize(cfg().message("cancelled").replace("<reason>", cfg().message(reasonKey))));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренняя логика
    // ─────────────────────────────────────────────────────────────────────────

    private void scheduleCountdownStep(UUID uuid, CastSession session, int secondsLeft) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
                runCountdownStep(uuid, secondsLeft), 20L);
        session.setTask(task);
    }

    private void runCountdownStep(UUID uuid, int secondsLeft) {
        CastSession session = sessions.get(uuid);
        if (session == null) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            sessions.remove(uuid);
            return;
        }

        String failReason = checkAbortConditions(player, session);
        if (failReason != null) {
            cancelSession(uuid, failReason);
            return;
        }

        session.updateLocation(player.getLocation());

        if (secondsLeft <= 0) {
            performTeleport(player, session);
            return;
        }

        player.sendActionBar(msg("countdown", "<seconds>", String.valueOf(secondsLeft)));
        scheduleCountdownStep(uuid, session, secondsLeft - 1);
    }

    private void performTeleport(Player player, CastSession session) {
        UUID uuid = player.getUniqueId();
        CoordinateTeleportScrollConfig config = cfg();

        CoordinateScrollDefinition def = config.getScroll(session.scrollId());
        if (def == null) {
            cancelSession(uuid, null);
            player.sendActionBar(msg("scroll-unknown"));
            return;
        }

        // Финальная проверка условий и лимитов прямо перед телепортацией — конфиг мог
        // перезагрузиться или граница мира сдвинуться, пока шёл отсчёт.
        String abortReason = checkAbortConditions(player, session);
        if (abortReason != null) {
            cancelSession(uuid, abortReason);
            return;
        }
        String limitReason = validateDestination(def, config);
        if (limitReason != null) {
            cancelSession(uuid, null);
            player.sendActionBar(msg("destination-blocked"));
            return;
        }

        Location destination = toLocation(def);
        if (destination == null) {
            cancelSession(uuid, null);
            player.sendActionBar(msg("destination-blocked"));
            return;
        }

        sessions.remove(uuid);
        session.cancelTask();

        player.teleport(destination);
        removeScrollFromHand(player);
        markUsed(uuid, def.id());

        player.sendActionBar(msg("success"));
    }

    /** Проверки, идентичные обычному свитку: движение / невидимость / бой — прерывают отсчёт. */
    private String checkAbortConditions(Player player, CastSession session) {
        if (hasMoved(player.getLocation(), session.lastLocation())) {
            return "reason-moved";
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return "reason-invisible";
        }
        if (CombatUtil.isInCombat(player)) {
            return "reason-pvp";
        }
        return null;
    }

    /**
     * Проверяет точку назначения на все guard rail'ы из {@code coord-teleport-scroll.limits}.
     * @return человекочитаемая причина отказа (для логов) или null если всё в порядке.
     */
    private String validateDestination(CoordinateScrollDefinition def, CoordinateTeleportScrollConfig config) {
        World world = Bukkit.getWorld(def.world());
        if (world == null) {
            return "мир '" + def.world() + "' не загружен";
        }

        if (!config.getAllowedWorlds().isEmpty() && !config.getAllowedWorlds().contains(world.getName())) {
            return "мир '" + world.getName() + "' не входит в allowed-worlds";
        }

        if (def.y() < config.getMinY() || def.y() > config.getMaxY()) {
            return "y=" + def.y() + " вне диапазона [" + config.getMinY() + ", " + config.getMaxY() + "]";
        }

        Location destination = new Location(world, def.x(), def.y(), def.z());

        if (config.isRespectWorldBorder()) {
            WorldBorder border = world.getWorldBorder();
            if (!border.isInside(destination)) {
                return "точка вне границы мира (world border)";
            }
        }

        if (config.getMaxDistanceFromSpawn() > 0) {
            Location spawn = world.getSpawnLocation();
            double dx = destination.getX() - spawn.getX();
            double dz = destination.getZ() - spawn.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > config.getMaxDistanceFromSpawn()) {
                return "дистанция от спавна " + (long) horizontalDistance + " блоков превышает max-distance-from-spawn";
            }
        }

        return null;
    }

    private Location toLocation(CoordinateScrollDefinition def) {
        World world = Bukkit.getWorld(def.world());
        if (world == null) {
            return null;
        }
        return new Location(world, def.x(), def.y(), def.z(), def.yaw(), def.pitch());
    }

    private long remainingCooldownSeconds(UUID uuid, String scrollId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Map<String, Long> perScroll = lastUse.get(uuid);
        if (perScroll == null) {
            return 0;
        }
        Long last = perScroll.get(scrollId);
        if (last == null) {
            return 0;
        }
        long elapsedSeconds = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsedSeconds);
    }

    private void markUsed(UUID uuid, String scrollId) {
        lastUse.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(scrollId, System.currentTimeMillis());
    }

    /**
     * Сравнивает блочные координаты двух Location — дробные части (покачивание камеры)
     * намеренно игнорируем, как и в {@link TeleportScrollManager}.
     */
    private boolean hasMoved(Location current, Location last) {
        if (last == null) {
            return false;
        }
        return current.getBlockX() != last.getBlockX()
                || current.getBlockY() != last.getBlockY()
                || current.getBlockZ() != last.getBlockZ();
    }

    /** Убирает свиток из основной руки игрока. Так как maxStackSize = 1, просто очищаем слот. */
    private void removeScrollFromHand(Player player) {
        player.getInventory().setItemInMainHand(null);
    }

    /** Отменяет активный отсчёт при выходе игрока с сервера, чтобы не висела задача-"призрак". */
    public void onQuit(UUID uuid) {
        cancelSession(uuid, null);
        lastUse.remove(uuid);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренний класс — данные одной сессии отсчёта
    // ─────────────────────────────────────────────────────────────────────────

    private static final class CastSession {
        private final String scrollId;
        private Location lastLocation;
        private BukkitTask task;

        CastSession(String scrollId, Location initialLocation) {
            this.scrollId = scrollId;
            this.lastLocation = initialLocation;
        }

        String scrollId() { return scrollId; }
        Location lastLocation() { return lastLocation; }
        void updateLocation(Location location) { this.lastLocation = location; }
        void setTask(BukkitTask task) { this.task = task; }

        void cancelTask() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }
}

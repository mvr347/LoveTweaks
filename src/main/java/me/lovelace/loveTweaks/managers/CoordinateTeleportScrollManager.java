package me.lovelace.loveTweaks.managers;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.utils.CombatUtil;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Менеджер сессий телепортации по координатам.
 * Жизненный цикл:
 * 1. ПКМ со свитком -> WAITING_FOR_INPUT (10 секунд на ввод координат в чат)
 * 2. Ввод координат (X Z или X Y Z) -> валидация и поиск безопасной точки в мире world
 * 3. COUNTING_DOWN -> обратный отсчет (5 сек, отменяется движением/PvP/невидимостью/сменой предмета)
 * 4. Успех -> телепортация и изъятие свитка.
 */
public class CoordinateTeleportScrollManager {

    private static final int CHAT_TIMEOUT_TICKS = 200; // 10 секунд
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d*\\.?\\d+");

    private final LoveTweaks plugin;

    // Активные сессии: UUID игрока -> сессия
    private final Map<UUID, CoordTeleportSession> sessions = new ConcurrentHashMap<>();
    // Время последнего использования игроком (для кулдауна) — bounded expiring cache
    private final com.github.benmanes.caffeine.cache.Cache<UUID, Long> lastUse =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(java.time.Duration.ofMinutes(30))
                    .build();

    public CoordinateTeleportScrollManager(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    public void cancelAll() {
        for (UUID id : new java.util.ArrayList<>(sessions.keySet())) {
            cancelSession(id, null);
        }
        lastUse.invalidateAll();
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

    private void sendActionBar(Player player, Component component) {
        boolean allowed = dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.notify.LoveNotify.class)
                .map(n -> n.isChannelEnabled(player.getUniqueId(), dev.lovelace.lovecore.api.notify.LoveNotify.Channel.ACTION_BAR))
                .orElse(true);
        if (allowed) {
            player.sendActionBar(component);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичный API
    // ─────────────────────────────────────────────────────────────────────────

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean isWaitingForInput(UUID uuid) {
        CoordTeleportSession session = sessions.get(uuid);
        return session != null && session.isWaitingForInput();
    }

    /**
     * Начинает фазу ожидания ввода координат в чат по ПКМ.
     */
    public void startInputPhase(Player player) {
        CoordinateTeleportScrollConfig config = cfg();
        if (!config.isEnabled()) {
            return;
        }

        if (isInCombat(player)) {
            Component c = GuiItemUtil.colorize(config.message("cancelled").replace("<reason>", config.message("reason-pvp")));
            player.sendMessage(c);
            sendActionBar(player, c);
            return;
        }

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            Component c = GuiItemUtil.colorize(config.message("cancelled").replace("<reason>", config.message("reason-invisible")));
            player.sendMessage(c);
            sendActionBar(player, c);
            return;
        }

        long remainingCooldown = remainingCooldownSeconds(player.getUniqueId(), config.getCooldownSeconds());
        if (remainingCooldown > 0) {
            Component c = msg("on-cooldown", "<seconds>", String.valueOf(remainingCooldown));
            player.sendMessage(c);
            sendActionBar(player, c);
            return;
        }

        cancelSession(player.getUniqueId(), null);

        CoordTeleportSession session = new CoordTeleportSession(player.getUniqueId());
        sessions.put(player.getUniqueId(), session);

        sendActionBar(player, msg("prompt"));
        player.sendMessage(msg("prompt"));
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        } catch (Throwable ignored) {}

        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CoordTeleportSession s = sessions.get(player.getUniqueId());
            if (s != null && s.isWaitingForInput()) {
                sessions.remove(player.getUniqueId());
                Player p = Bukkit.getPlayer(player.getUniqueId());
                if (p != null) {
                    p.sendMessage(msg("timeout"));
                    sendActionBar(p, msg("timeout"));
                }
            }
        }, CHAT_TIMEOUT_TICKS);

        session.setChatTimeoutTask(timeoutTask);
    }

    /**
     * Обрабатывает сообщение игрока из чата.
     */
    public boolean handleChatInput(Player player, String rawInput) {
        CoordTeleportSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isWaitingForInput()) {
            return false;
        }

        session.cancelChatTimeoutTask();
        String input = rawInput.trim();

        if (input.equalsIgnoreCase("отмена") || input.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            sendActionBar(player, msg("chat-cancelled"));
            return true;
        }

        CoordinateTeleportScrollConfig config = cfg();
        Location targetLocation = parseCoordinates(player, input, config);
        if (targetLocation == null) {
            sessions.remove(player.getUniqueId());
            return true;
        }

        session.beginCountdown(targetLocation, player.getLocation());

        if (config.getCastTimeSeconds() <= 0) {
            performTeleport(player, session);
            return true;
        }

        scheduleCountdownStep(player.getUniqueId(), session, config.getCastTimeSeconds());
        return true;
    }

    public void cancelSession(UUID uuid, String reasonKey) {
        CoordTeleportSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        session.cancelAllTasks();

        if (reasonKey != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Component c = GuiItemUtil.colorize(cfg().message("cancelled").replace("<reason>", cfg().message(reasonKey)));
                sendActionBar(player, c);
                player.sendMessage(c);
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                } catch (Throwable ignored) {}
                spawnCancelParticles(player.getLocation());
            }
        }
    }

    public void onQuit(UUID uuid) {
        cancelSession(uuid, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренняя логика
    // ─────────────────────────────────────────────────────────────────────────

    private Location parseCoordinates(Player player, String input, CoordinateTeleportScrollConfig config) {
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(input);
        while (matcher.find()) {
            try {
                numbers.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {}
        }

        if (numbers.size() != 2 && numbers.size() != 3) {
            sendActionBar(player, msg("invalid-format"));
            return null;
        }

        String targetWorldName = config.getTargetWorld();
        World targetWorld = targetWorldName != null && !targetWorldName.isBlank() ? Bukkit.getWorld(targetWorldName) : null;
        if (targetWorld == null) {
            targetWorld = Bukkit.getWorld("world");
            if (targetWorld == null && !Bukkit.getWorlds().isEmpty()) {
                targetWorld = Bukkit.getWorlds().get(0);
            }
        }

        if (targetWorld == null || (!config.getAllowedWorlds().isEmpty() && !config.getAllowedWorlds().contains(targetWorld.getName()))) {
            sendActionBar(player, msg("world-not-allowed"));
            return null;
        }

        double destX = Math.floor(numbers.get(0)) + 0.5;
        double destZ = numbers.size() == 2 ? Math.floor(numbers.get(1)) + 0.5 : Math.floor(numbers.get(2)) + 0.5;

        int blockX = (int) Math.floor(destX);
        int blockZ = (int) Math.floor(destZ);

        if (config.isRespectWorldBorder()) {
            WorldBorder border = targetWorld.getWorldBorder();
            Location testLoc = new Location(targetWorld, destX, 100, destZ);
            if (!border.isInside(testLoc)) {
                sendActionBar(player, msg("destination-blocked"));
                return null;
            }
        }

        if (config.getMaxDistanceFromSpawn() > 0) {
            Location spawn = targetWorld.getSpawnLocation();
            double dx = destX - spawn.getX();
            double dz = destZ - spawn.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > config.getMaxDistanceFromSpawn()) {
                sendActionBar(player, msg("destination-blocked"));
                return null;
            }
        }

        // Находим верхний блок поверхности мира (WORLD_SURFACE)
        int highestY = targetWorld.getHighestBlockYAt(blockX, blockZ, HeightMap.WORLD_SURFACE);
        if (highestY < config.getMinY() || highestY > config.getMaxY()) {
            sendActionBar(player, msg("destination-blocked"));
            return null;
        }

        // Сканируем сверху вниз в поисках твердой земли (суши)
        org.bukkit.block.Block groundBlock = null;
        for (int y = highestY; y >= config.getMinY(); y--) {
            org.bukkit.block.Block b = targetWorld.getBlockAt(blockX, y, blockZ);
            org.bukkit.Material mat = b.getType();

            // Если на поверхности вода / водоем
            if (isWaterOrFluid(b)) {
                sendActionBar(player, msg("destination-water"));
                return null;
            }

            // Если на поверхности лава или опасности
            if (isLavaOrHazard(mat)) {
                sendActionBar(player, msg("destination-unsafe"));
                return null;
            }

            // Найдена твердая суша
            if (mat.isSolid() && !isLeaves(mat)) {
                groundBlock = b;
                break;
            }
        }

        if (groundBlock == null) {
            sendActionBar(player, msg("destination-blocked"));
            return null;
        }

        int groundY = groundBlock.getY();

        // Если игрок указал Y вручную (формат X Y Z) — запрещаем попадание в шахты/пещеры под сушу
        if (numbers.size() == 3) {
            double requestedY = numbers.get(1);
            if (requestedY < groundY - 1) {
                sendActionBar(player, msg("destination-underground"));
                return null;
            }
            if (requestedY > groundY + 4) {
                sendActionBar(player, msg("destination-blocked"));
                return null;
            }
        }

        double destY = groundY + 1.0;

        if (destY < config.getMinY() || destY > config.getMaxY()) {
            sendActionBar(player, msg("destination-blocked"));
            return null;
        }

        // Проверяем, что пространство для ног и головы свободно от воды и опасностей
        org.bukkit.block.Block feetBlock = targetWorld.getBlockAt(blockX, (int) destY, blockZ);
        org.bukkit.block.Block headBlock = targetWorld.getBlockAt(blockX, (int) destY + 1, blockZ);

        if (isWaterOrFluid(feetBlock) || isWaterOrFluid(headBlock)) {
            sendActionBar(player, msg("destination-water"));
            return null;
        }

        if (isLavaOrHazard(feetBlock.getType()) || isLavaOrHazard(headBlock.getType())) {
            sendActionBar(player, msg("destination-unsafe"));
            return null;
        }

        if (feetBlock.getType().isSolid() || headBlock.getType().isSolid()) {
            sendActionBar(player, msg("destination-blocked"));
            return null;
        }

        return new Location(targetWorld, destX, destY, destZ, player.getYaw(), player.getPitch());
    }

    private boolean isWaterOrFluid(org.bukkit.block.Block block) {
        org.bukkit.Material mat = block.getType();
        if (mat == org.bukkit.Material.WATER || mat == org.bukkit.Material.BUBBLE_COLUMN
                || mat == org.bukkit.Material.KELP || mat == org.bukkit.Material.KELP_PLANT
                || mat == org.bukkit.Material.SEAGRASS || mat == org.bukkit.Material.TALL_SEAGRASS) {
            return true;
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) {
            return true;
        }
        return false;
    }

    private boolean isLavaOrHazard(org.bukkit.Material mat) {
        return mat == org.bukkit.Material.LAVA || mat == org.bukkit.Material.FIRE
                || mat == org.bukkit.Material.SOUL_FIRE || mat == org.bukkit.Material.CAMPFIRE
                || mat == org.bukkit.Material.SOUL_CAMPFIRE || mat == org.bukkit.Material.CACTUS
                || mat == org.bukkit.Material.SWEET_BERRY_BUSH || mat == org.bukkit.Material.WITHER_ROSE
                || mat == org.bukkit.Material.POWDER_SNOW;
    }

    private boolean isLeaves(org.bukkit.Material mat) {
        return mat.name().endsWith("_LEAVES");
    }

    private void scheduleCountdownStep(UUID uuid, CoordTeleportSession session, int secondsLeft) {
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () ->
                runCountdownStep(uuid, secondsLeft), 20L);
        session.setCountdownTask(task);
    }

    private void runCountdownStep(UUID uuid, int secondsLeft) {
        CoordTeleportSession session = sessions.get(uuid);
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

        sendActionBar(player, msg("countdown", "<seconds>", String.valueOf(secondsLeft)));
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.6f);
        } catch (Throwable ignored) {}
        spawnCountdownParticles(player.getLocation());

        scheduleCountdownStep(uuid, session, secondsLeft - 1);
    }

    private void performTeleport(Player player, CoordTeleportSession session) {
        UUID uuid = player.getUniqueId();
        String abortReason = checkAbortConditions(player, session);
        if (abortReason != null) {
            cancelSession(uuid, abortReason);
            return;
        }

        Location dest = session.getDestination();
        if (dest == null || dest.getWorld() == null) {
            cancelSession(uuid, null);
            sendActionBar(player, msg("destination-blocked"));
            return;
        }

        sessions.remove(uuid);
        session.cancelAllTasks();

        Location fromLoc = player.getLocation().clone();
        spawnTeleportBurst(fromLoc);
        try {
            fromLoc.getWorld().playSound(fromLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        } catch (Throwable ignored) {}

        removeScrollFromHand(player);
        player.teleport(dest);
        markUsed(uuid);

        spawnTeleportBurst(dest);
        try {
            dest.getWorld().playSound(dest, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            dest.getWorld().playSound(dest, org.bukkit.Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.4f);
            dest.getWorld().playSound(dest, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        } catch (Throwable ignored) {}

        Component succ = msg("success");
        sendActionBar(player, succ);
        player.sendMessage(succ);
    }

    private void spawnCountdownParticles(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
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
        World w = loc.getWorld();
        Location center = loc.clone().add(0, 1.0, 0);
        w.spawnParticle(org.bukkit.Particle.PORTAL, center, 45, 0.5, 0.7, 0.5, 0.8);
        w.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, center, 25, 0.4, 0.6, 0.4, 0.1);
        w.spawnParticle(org.bukkit.Particle.ENCHANT, center, 35, 0.6, 0.8, 0.6, 0.3);
        w.spawnParticle(org.bukkit.Particle.FLASH, center, 1, 0, 0, 0, 0);
    }

    private void spawnCancelParticles(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        World w = loc.getWorld();
        w.spawnParticle(org.bukkit.Particle.SMOKE, loc.clone().add(0, 1.0, 0), 15, 0.3, 0.4, 0.3, 0.05);
    }

    private boolean isWorldAllowed(World world, CoordinateTeleportScrollConfig config) {
        List<String> allowed = config.getAllowedWorlds();
        if (allowed == null || allowed.isEmpty() || allowed.contains("*")) {
            return true;
        }
        for (String w : allowed) {
            if (w.equalsIgnoreCase(world.getName())) {
                return true;
            }
        }
        return false;
    }

    private String checkAbortConditions(Player player, CoordTeleportSession session) {
        if (!CoordinateTeleportScroll.isScroll(player.getInventory().getItemInMainHand())) {
            return "reason-dropped";
        }
        if (hasMoved(player.getLocation(), session.getLastLocation())) {
            return "reason-moved";
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return "reason-invisible";
        }
        if (isInCombat(player)) {
            return "reason-pvp";
        }
        return null;
    }

    private boolean isInCombat(Player player) {
        return CombatUtil.isInCombat(player);
    }

    private boolean hasMoved(Location current, Location last) {
        if (last == null) {
            return false;
        }
        if (current.getWorld() == null || last.getWorld() == null || !current.getWorld().equals(last.getWorld())) {
            return true;
        }
        return current.getBlockX() != last.getBlockX()
                || current.getBlockY() != last.getBlockY()
                || current.getBlockZ() != last.getBlockZ();
    }

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

    private long remainingCooldownSeconds(UUID uuid, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Long last = lastUse.getIfPresent(uuid);
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }

    private void markUsed(UUID uuid) {
        lastUse.put(uuid, System.currentTimeMillis());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренний класс сессии
    // ─────────────────────────────────────────────────────────────────────────

    private static final class CoordTeleportSession {
        private final UUID playerUuid;
        private Location destination;
        private Location lastLocation;
        private boolean waitingForInput = true;

        private BukkitTask chatTimeoutTask;
        private BukkitTask countdownTask;

        CoordTeleportSession(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }

        void beginCountdown(Location destination, Location initialLocation) {
            this.destination = destination;
            this.lastLocation = initialLocation;
            this.waitingForInput = false;
        }

        boolean isWaitingForInput() { return waitingForInput; }
        Location getDestination() { return destination; }
        Location getLastLocation() { return lastLocation; }
        void updateLocation(Location loc) { this.lastLocation = loc; }

        void setChatTimeoutTask(BukkitTask task) { this.chatTimeoutTask = task; }
        void setCountdownTask(BukkitTask task) { this.countdownTask = task; }

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

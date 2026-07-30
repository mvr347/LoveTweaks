package me.lovelace.loveTweaks.post;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.InventorySerializationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the whole Royal Post lifecycle: recipient chat-input sessions on the compose GUI,
 * spawning/moving carrier-pigeon entities, delivering (or looting, if shot down) their payload,
 * and a lightweight last-known-location tracker used to aim pigeons at offline recipients.
 *
 * <p>The pigeon flies toward the recipient's last-seen location on this server (updated on
 * join/quit), not their actual claim/priv — there is no LoveClaims integration for aiming.
 * There is, however, a LoveCore integration for risk: flying over territory hostile to the
 * recipient ({@link dev.lovelace.lovecore.api.territory.TerritoryOracle#hostileFor}) makes the
 * pigeon vulnerable to being shot down on its own, same outcome as an arrow from a player —
 * cargo drops where it falls, sender gets notified. See {@link #tickFlights()}.
 */
public class PostManager {

    private final LoveTweaks plugin;
    private final File dataFile;

    private final Map<UUID, PostGUIHolder> openSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PostFlight> activeFlights = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastKnownLocation = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> pendingMailbox = new ConcurrentHashMap<>();

    public PostManager(LoveTweaks plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "post-data.yml");
        load();
    }

    // ─── Compose session ──────────────────────────────────────────────────

    public void openCompose(Player player) {
        PostGUIHolder holder = new PostGUIHolder(player.getUniqueId());
        openSessions.put(player.getUniqueId(), holder);
        PostGUI.open(player, holder, plugin);
    }

    public PostGUIHolder getSession(UUID playerUuid) {
        return openSessions.get(playerUuid);
    }

    public boolean isAwaitingRecipientInput(UUID playerUuid) {
        PostGUIHolder holder = openSessions.get(playerUuid);
        return holder != null && holder.isAwaitingRecipientInput();
    }

    public void requestRecipientInput(Player player, PostGUIHolder holder) {
        holder.setAwaitingRecipientInput(true);
        player.sendMessage("§eВведите ник получателя в чат:");
    }

    public void handleChatInput(Player player, String message) {
        PostGUIHolder holder = openSessions.get(player.getUniqueId());
        if (holder == null || !holder.isAwaitingRecipientInput()) {
            return;
        }
        String name = message.trim();
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target.getName() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage("§cИгрок §e" + name + "§c никогда не заходил на сервер. Попробуйте снова:");
            return;
        }
        holder.setRecipientName(target.getName());
        holder.setAwaitingRecipientInput(false);
        PostGUI.refreshControls(holder, plugin);
        player.sendMessage("§aПолучатель установлен: §e" + target.getName());
    }

    /** Returns deposited items to the player when they close the GUI without sending. */
    public void cancelCompose(Player player, PostGUIHolder holder) {
        if (openSessions.remove(player.getUniqueId()) == null) {
            return;
        }
        Inventory inv = holder.getInventory();
        for (int slot = PostGUI.DEPOSIT_START; slot <= PostGUI.DEPOSIT_END; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                giveOrDrop(player, item);
            }
        }
    }

    public void trySend(Player player, PostGUIHolder holder) {
        List<ItemStack> items = new ArrayList<>();
        Inventory inv = holder.getInventory();
        for (int slot = PostGUI.DEPOSIT_START; slot <= PostGUI.DEPOSIT_END; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        if (items.isEmpty()) {
            player.sendMessage("§cВложите хотя бы один предмет перед отправкой.");
            return;
        }
        String recipientName = holder.getRecipientName();
        if (recipientName == null || recipientName.isBlank()) {
            player.sendMessage("§cУкажите получателя.");
            return;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientName);
        UUID recipientUuid = recipient.getUniqueId();

        Location start = player.getLocation();
        Location target = resolveTargetLocation(recipientUuid);
        if (target == null) {
            player.sendMessage("§cНеизвестно местоположение получателя. Дождитесь, пока он зайдёт на сервер.");
            return;
        }
        if (target.getWorld() == null || start.getWorld() == null || !target.getWorld().equals(start.getWorld())) {
            player.sendMessage("§cПолучатель находится в другом мире — отправка невозможна.");
            return;
        }

        long cost = plugin.getLoveTweaksConfig().getPostCost();
        var economy = dev.lovelace.lovecore.api.LoveCore
                .service(dev.lovelace.lovecore.api.economy.LoveEconomy.class);
        if (economy.isEmpty() || !economy.get().has(player, cost)) {
            player.sendMessage("§cУ вас недостаточно монет для отправки голубя.");
            return;
        }
        economy.get().charge(player, cost);

        for (int slot = PostGUI.DEPOSIT_START; slot <= PostGUI.DEPOSIT_END; slot++) {
            inv.setItem(slot, null);
        }
        openSessions.remove(player.getUniqueId());
        player.closeInventory();

        spawnFlight(player.getName(), recipientUuid, recipient.getName(), items.toArray(new ItemStack[0]), start, target);
        player.sendMessage("§aПочтовый голубь отправлен получателю §e" + recipient.getName() + "§a!");
    }

    private Location resolveTargetLocation(UUID recipientUuid) {
        Player online = Bukkit.getPlayer(recipientUuid);
        if (online != null) {
            return online.getLocation();
        }
        return lastKnownLocation.get(recipientUuid);
    }

    // ─── Flight ────────────────────────────────────────────────────────────

    private void spawnFlight(String senderName, UUID recipientUuid, String recipientName,
                              ItemStack[] items, Location start, Location target) {
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        Bat bat = world.spawn(start.clone().add(0, 1.5, 0), Bat.class, b -> {
            b.setAI(false);
            b.setAwake(true);
            b.setInvisible(true);
            b.setSilent(true);
            b.setPersistent(true);
            b.setRemoveWhenFarAway(false);
            b.setGravity(false);
            b.customName(Component.text("Почтовый голубь → " + recipientName));
        });

        long durationMillis = plugin.getLoveTweaksConfig().getPostFlightSeconds() * 1000L;
        PostFlight flight = new PostFlight(bat.getUniqueId(), items, senderName, recipientUuid, recipientName,
                start, target, System.currentTimeMillis(), durationMillis);
        activeFlights.put(bat.getUniqueId(), flight);
    }

    public boolean isPigeon(UUID entityUuid) {
        return activeFlights.containsKey(entityUuid);
    }

    /** Ticked periodically from the main plugin's scheduler. */
    public void tickFlights() {
        if (activeFlights.isEmpty()) {
            return;
        }
        List<UUID> arrived = new ArrayList<>();
        for (PostFlight flight : new ArrayList<>(activeFlights.values())) {
            Entity entity = Bukkit.getEntity(flight.entityUuid());
            if (entity == null || entity.isDead() || !entity.isValid()) {
                arrived.add(flight.entityUuid());
                continue;
            }
            double progress = flight.progress();
            if (progress >= 1.0) {
                deliver(flight);
                entity.remove();
                arrived.add(flight.entityUuid());
                continue;
            }
            Location start = flight.start();
            Location target = flight.target();
            double x = start.getX() + (target.getX() - start.getX()) * progress;
            double y = start.getY() + (target.getY() - start.getY()) * progress + Math.sin(progress * Math.PI) * 3.0;
            double z = start.getZ() + (target.getZ() - start.getZ()) * progress;
            Location current = new Location(start.getWorld(), x, y, z);
            entity.teleport(current);
            if (start.getWorld() != null) {
                start.getWorld().spawnParticle(Particle.CLOUD, current, 1, 0, 0, 0, 0);
            }
            if (rollHostileTerritoryShootdown(flight, current)) {
                entity.remove();
                arrived.add(flight.entityUuid());
            }
        }
        arrived.forEach(activeFlights::remove);
    }

    /**
     * Над территорией, враждебной получателю, голубь рискует быть сбит без стрелка — тем же
     * исходом, что и от игрока: груз падает там, где голубь был, отправитель узнаёт причину.
     * Шанс за тик подобран так, чтобы полностью враждебный маршрут (все ~200 тиков 20-секундного
     * полёта) сбивал голубя примерно в половине случаев — риск заметный, но не гарантированный.
     *
     * @return true, если голубь сбит этим тиком (вызывающий должен убрать сущность)
     */
    private boolean rollHostileTerritoryShootdown(PostFlight flight, Location current) {
        if (!isHostileTerritory(flight.recipientUuid(), current)) {
            return false;
        }
        double chancePerTick = plugin.getConfig().getDouble("post.hostile-territory-shootdown-chance-per-tick", 0.0035);
        if (ThreadLocalRandom.current().nextDouble() >= chancePerTick) {
            return false;
        }
        handleShotDownByTerritory(flight, current);
        return true;
    }

    private boolean isHostileTerritory(UUID recipientId, Location location) {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") == null) {
            return false;
        }
        try {
            return dev.lovelace.lovecore.api.LoveCore
                    .service(dev.lovelace.lovecore.api.territory.TerritoryOracle.class)
                    .map(oracle -> oracle.hostileFor(recipientId, location))
                    .orElse(false);
        } catch (Throwable t) {
            return false;
        }
    }

    private void handleShotDownByTerritory(PostFlight flight, Location location) {
        activeFlights.remove(flight.entityUuid());
        for (ItemStack item : flight.items()) {
            location.getWorld().dropItemNaturally(location, item);
        }
        Player sender = Bukkit.getPlayerExact(flight.senderName());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§cВаш почтовый голубь к §e" + flight.recipientName()
                    + " §cбыл сбит над вражеской территорией! Посылка выпала на землю.");
        }
    }

    private void deliver(PostFlight flight) {
        Player recipient = Bukkit.getPlayer(flight.recipientUuid());
        if (recipient != null && recipient.isOnline()) {
            for (ItemStack item : flight.items()) {
                giveOrDrop(recipient, item);
            }
            recipient.sendMessage("§6Вам пришла посылка от §e" + flight.senderName() + "§6!");
        } else {
            Inventory temp = Bukkit.createInventory(null, 9);
            for (int i = 0; i < flight.items().length && i < 9; i++) {
                temp.setItem(i, flight.items()[i]);
            }
            String base64 = InventorySerializationUtil.inventoryToBase64(temp);
            pendingMailbox.computeIfAbsent(flight.recipientUuid(), key -> new ArrayList<>()).add(base64);
            save();
        }

        Player sender = Bukkit.getPlayerExact(flight.senderName());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§aПосылка получателю §e" + flight.recipientName() + " §aдоставлена!");
        }
    }

    public void handleShotDown(UUID entityUuid, Location deathLocation, String shooterName) {
        PostFlight flight = activeFlights.remove(entityUuid);
        if (flight == null) {
            return;
        }
        for (ItemStack item : flight.items()) {
            deathLocation.getWorld().dropItemNaturally(deathLocation, item);
        }
        Player sender = Bukkit.getPlayerExact(flight.senderName());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§cВаш почтовый голубь к §e" + flight.recipientName()
                    + " §cбыл сбит игроком §e" + shooterName + "§c! Посылка выпала на землю.");
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // ─── Location tracking + mailbox delivery on join ─────────────────────

    public void trackLocation(Player player) {
        lastKnownLocation.put(player.getUniqueId(), player.getLocation());
        save();
    }

    public void deliverPendingMail(Player player) {
        List<String> mail = pendingMailbox.remove(player.getUniqueId());
        if (mail == null || mail.isEmpty()) {
            return;
        }
        int delivered = 0;
        for (String base64 : mail) {
            try {
                ItemStack[] contents = InventorySerializationUtil.inventoryFromBase64(base64);
                for (ItemStack item : contents) {
                    if (item != null && item.getType() != Material.AIR) {
                        giveOrDrop(player, item);
                    }
                }
                delivered++;
            } catch (IOException exception) {
                plugin.getLogger().warning("Не удалось восстановить посылку для " + player.getName() + ": " + exception.getMessage());
            }
        }
        if (delivered > 0) {
            player.sendMessage("§6Пока вас не было, пришла почта! (" + delivered + " посылок)");
        }
        save();
    }

    // ─── Persistence ────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection locations = yaml.getConfigurationSection("locations");
        if (locations != null) {
            for (String key : locations.getKeys(false)) {
                ConfigurationSection section = locations.getConfigurationSection(key);
                if (section == null) continue;
                World world = Bukkit.getWorld(section.getString("world", ""));
                if (world == null) continue;
                Location location = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
                lastKnownLocation.put(UUID.fromString(key), location);
            }
        }

        ConfigurationSection mailbox = yaml.getConfigurationSection("mailbox");
        if (mailbox != null) {
            for (String key : mailbox.getKeys(false)) {
                pendingMailbox.put(UUID.fromString(key), new ArrayList<>(mailbox.getStringList(key)));
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Location> entry : lastKnownLocation.entrySet()) {
            Location loc = entry.getValue();
            if (loc.getWorld() == null) continue;
            String path = "locations." + entry.getKey();
            yaml.set(path + ".world", loc.getWorld().getName());
            yaml.set(path + ".x", loc.getX());
            yaml.set(path + ".y", loc.getY());
            yaml.set(path + ".z", loc.getZ());
        }
        for (Map.Entry<UUID, List<String>> entry : pendingMailbox.entrySet()) {
            yaml.set("mailbox." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить post-data.yml: " + exception.getMessage());
        }
    }
}

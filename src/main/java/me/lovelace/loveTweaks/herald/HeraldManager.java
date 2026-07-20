package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Holds the current "Голос Королевства" state (active buyer + 3 announcements + expiry) and the
 * per-player chat-input sessions used to capture those 3 announcement lines after purchase
 * (mirrors TeleportScrollManager's chat-capture flow).
 */
public class HeraldManager {

    private static final long VOICE_DURATION_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final LoveTweaks plugin;
    private final File dataFile;

    private String buyerName;
    private List<String> messages = new ArrayList<>();
    private long expiresAt;

    private final Map<UUID, List<String>> pendingInput = new HashMap<>();

    public HeraldManager(LoveTweaks plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "herald-data.yml");
        load();
    }

    public boolean isActive() {
        return buyerName != null && !messages.isEmpty() && System.currentTimeMillis() < expiresAt;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public List<String> getMessages() {
        return List.copyOf(messages);
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void activate(String buyerName, List<String> messages) {
        this.buyerName = buyerName;
        this.messages = new ArrayList<>(messages);
        this.expiresAt = System.currentTimeMillis() + VOICE_DURATION_MILLIS;
        save();
    }

    public void clear() {
        this.buyerName = null;
        this.messages = new ArrayList<>();
        this.expiresAt = 0;
        save();
    }

    public boolean isAwaitingInput(UUID uuid) {
        return pendingInput.containsKey(uuid);
    }

    public void startAnnouncementInput(Player player) {
        pendingInput.put(player.getUniqueId(), new ArrayList<>());
        player.sendMessage(colorize("&eВведите объявление &f1&e из &f3 &eв чат:"));
    }

    public void cancelInput(UUID uuid) {
        pendingInput.remove(uuid);
    }

    /**
     * Feeds one line of chat input into the player's pending session. Once 3 lines are collected,
     * activates the voice and clears the session.
     */
    public void handleChatInput(Player player, String message) {
        List<String> lines = pendingInput.get(player.getUniqueId());
        if (lines == null) {
            return;
        }
        lines.add(sanitize(message));
        if (lines.size() < 3) {
            player.sendMessage(colorize("&eВведите объявление &f" + (lines.size() + 1) + "&e из &f3 &eв чат:"));
            return;
        }
        pendingInput.remove(player.getUniqueId());
        activate(player.getName(), lines);
        player.sendMessage(colorize("&aГолос Королевства активирован на &f24 часа&a!"));
    }

    public void broadcast() {
        if (!isActive()) {
            return;
        }
        for (String message : messages) {
            Component line = colorize("&6&l[Голос Королевства] &f" + buyerName + "&7: &e" + message);
            plugin.getServer().broadcast(line);
        }
    }

    private static Component colorize(String text) {
        return LEGACY.deserialize(text.replace('&', '§'));
    }

    /** Strips color-code characters from player-supplied announcement text before storing it. */
    private static String sanitize(String text) {
        String cleaned = text.replace('&', ' ').replace('§', ' ').trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        buyerName = yaml.getString("buyer-name");
        messages = new ArrayList<>(yaml.getStringList("messages"));
        expiresAt = yaml.getLong("expires-at", 0);
        if (buyerName != null && !isActive()) {
            clear();
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buyer-name", buyerName);
        yaml.set("messages", messages);
        yaml.set("expires-at", expiresAt);
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить herald-data.yml: " + exception.getMessage());
        }
    }
}

package me.lovelace.loveTweaks.herald;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.integration.ChatFilterIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Holds the Herald's independent announcement slots (see {@link HeraldSlot}), the pricing
 * formula, the purchase flow (funds + profanity check), persistence, and the once-a-minute
 * round-robin broadcast rotation.
 */
public class HeraldManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public enum PurchaseResult {
        SUCCESS, SLOT_TAKEN, EMPTY_MESSAGE, TOO_LONG, INSUFFICIENT_FUNDS, REJECTED_PROFANITY
    }

    private final LoveTweaks plugin;
    private final ChatFilterIntegration chatFilter;
    private final File dataFile;

    private final List<HeraldSlot> slots = new ArrayList<>();
    private int rotatorIndex;

    public HeraldManager(LoveTweaks plugin, ChatFilterIntegration chatFilter) {
        this.plugin = plugin;
        this.chatFilter = chatFilter;
        this.dataFile = new File(plugin.getDataFolder(), "herald-data.yml");
        resize(plugin.getLoveTweaksConfig().getHeraldSlots());
        load();
    }

    /** Keeps the slot list in sync with a (re)loaded {@code herald.slots} count. */
    public void resize(int newSize) {
        newSize = Math.max(1, newSize);
        while (slots.size() < newSize) slots.add(new HeraldSlot());
        while (slots.size() > newSize) slots.remove(slots.size() - 1);
        if (rotatorIndex >= slots.size()) rotatorIndex = 0;
    }

    public int slotCount() {
        return slots.size();
    }

    public HeraldSlot getSlot(int index) {
        return slots.get(index);
    }

    public long computeCost(int durationMinutes, int messageLength) {
        var cfg = plugin.getLoveTweaksConfig();
        int minDuration = cfg.getHeraldMinDurationMinutes();
        int maxDuration = cfg.getHeraldMaxDurationMinutes();
        int maxLength = cfg.getHeraldMaxMessageLength();
        long minCost = cfg.getHeraldMinCost();
        long maxCost = cfg.getHeraldMaxCost();

        double durationFactor = maxDuration > minDuration
                ? clamp01((durationMinutes - minDuration) / (double) (maxDuration - minDuration))
                : 1.0;
        double lengthFactor = maxLength > 0 ? clamp01(messageLength / (double) maxLength) : 0.0;
        double blend = (durationFactor + lengthFactor) / 2.0;

        long cost = minCost + Math.round((maxCost - minCost) * blend);
        return Math.max(minCost, Math.min(maxCost, cost));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Attempts to purchase {@code slotIndex} for {@code player}. Money is charged before the
     * profanity check, and is never refunded if the check rejects the message — the slot simply
     * never activates, matching the "money already paid is confiscated" spec.
     */
    public PurchaseResult purchase(Player player, int slotIndex, int durationMinutes, String rawMessage) {
        HeraldSlot slot = slots.get(slotIndex);
        if (slot.isActive()) {
            return PurchaseResult.SLOT_TAKEN;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            return PurchaseResult.EMPTY_MESSAGE;
        }
        int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
        if (message.length() > maxLength) {
            return PurchaseResult.TOO_LONG;
        }

        long cost = computeCost(durationMinutes, message.length());
        var economy = LoveCore.service(LoveEconomy.class);
        if (economy.isEmpty() || !economy.get().has(player, cost)) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }
        economy.get().charge(player, cost);

        if (chatFilter.isProfane(message)) {
            return PurchaseResult.REJECTED_PROFANITY;
        }

        long durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes);
        slot.activate(player.getUniqueId(), player.getName(), message, durationMillis);
        save();
        return PurchaseResult.SUCCESS;
    }

    public void clear() {
        for (HeraldSlot slot : slots) slot.clear();
        save();
    }

    /** Advances the rotation by exactly one slot and broadcasts it if it's active. Called once a minute. */
    public void tickBroadcast() {
        if (slots.isEmpty()) return;

        HeraldSlot slot = slots.get(rotatorIndex);
        rotatorIndex = (rotatorIndex + 1) % slots.size();

        if (slot.isActive()) {
            broadcast(slot);
        } else if (slot.ownerId() != null) {
            slot.clear();
            save();
        }
    }

    private void broadcast(HeraldSlot slot) {
        String template = plugin.getLoveTweaksConfig().getHeraldGuiConfig().message("broadcast-format");
        String text = template
                .replace("<buyer>", slot.ownerName() == null ? "" : slot.ownerName())
                .replace("<message>", slot.message() == null ? "" : slot.message());
        plugin.getServer().broadcast(colorize(text));
    }

    private static Component colorize(String text) {
        return LEGACY.deserialize(text.replace('&', '§'));
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection slotsSection = yaml.getConfigurationSection("slots");
        if (slotsSection == null) return;

        for (String key : slotsSection.getKeys(false)) {
            int index;
            try {
                index = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                continue;
            }
            if (index < 0 || index >= slots.size()) continue;

            ConfigurationSection s = slotsSection.getConfigurationSection(key);
            if (s == null) continue;

            String ownerIdRaw = s.getString("owner-id");
            String ownerName = s.getString("owner-name");
            String message = s.getString("message");
            long expiresAt = s.getLong("expires-at", 0);
            if (ownerIdRaw == null || message == null || expiresAt <= System.currentTimeMillis()) {
                continue;
            }
            try {
                UUID ownerId = UUID.fromString(ownerIdRaw);
                HeraldSlot slot = slots.get(index);
                slot.activate(ownerId, ownerName, message, expiresAt - System.currentTimeMillis());
            } catch (IllegalArgumentException exception) {
                // corrupt entry — skip
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < slots.size(); i++) {
            HeraldSlot slot = slots.get(i);
            if (!slot.isActive()) continue;
            String path = "slots." + i;
            yaml.set(path + ".owner-id", slot.ownerId().toString());
            yaml.set(path + ".owner-name", slot.ownerName());
            yaml.set(path + ".message", slot.message());
            yaml.set(path + ".expires-at", slot.expiresAt());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить herald-data.yml: " + exception.getMessage());
        }
    }
}

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
 * formula, the purchase flow (profanity check, then funds), persistence, and the once-a-minute
 * round-robin broadcast rotation.
 */
public class HeraldManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public enum PurchaseResult {
        SUCCESS, SLOT_TAKEN, EMPTY_MESSAGE, TOO_LONG, INSUFFICIENT_FUNDS, REJECTED_PROFANITY, ALREADY_OWNS_SLOT
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
     * Attempts to purchase {@code slotIndex} for {@code player}. The profanity check runs before
     * money changes hands: a rejected message costs the player nothing, so LoveChatFilter (when
     * present) is checked first and only a message that passes gets to the funds check and charge.
     */
    public synchronized PurchaseResult purchase(Player player, int slotIndex, int durationMinutes, String rawMessage) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return PurchaseResult.SLOT_TAKEN;
        }
        HeraldSlot slot = slots.get(slotIndex);
        if (slot.isActive()) {
            return PurchaseResult.SLOT_TAKEN;
        }
        // 2026-09-26: один игрок мог выкупить все 3 слота глашатая сразу - проверялась только
        // занятость КОНКРЕТНОГО слота, но не то, что этот же player.getUniqueId() уже владеет
        // другим активным слотом. Лимит - один активный слот на игрока одновременно.
        if (playerOwnsAnySlot(player.getUniqueId())) {
            return PurchaseResult.ALREADY_OWNS_SLOT;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            return PurchaseResult.EMPTY_MESSAGE;
        }
        int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
        if (message.length() > maxLength) {
            return PurchaseResult.TOO_LONG;
        }

        if (chatFilter.isProfane(message)) {
            return PurchaseResult.REJECTED_PROFANITY;
        }

        long cost = computeCost(durationMinutes, message.length());
        var economy = LoveCore.service(LoveEconomy.class);
        if (economy.isEmpty() || !economy.get().has(player, cost)) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }
        // charge() is the authority; has() above is only a pre-check.
        if (!economy.get().charge(player, cost)) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        long durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes);
        slot.activate(player.getUniqueId(), player.getName(), message, durationMillis);
        save();
        return PurchaseResult.SUCCESS;
    }

    /** True if {@code playerId} already owns an active slot (see the one-slot-per-player limit in {@link #purchase}). */
    public synchronized boolean playerOwnsAnySlot(UUID playerId) {
        for (HeraldSlot other : slots) {
            if (other.isActive() && playerId.equals(other.ownerId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        for (HeraldSlot slot : slots) slot.clear();
        save();
    }

    /** Advances the rotation by exactly one slot and broadcasts it if it's active. Called once a minute. */
    public synchronized void tickBroadcast() {
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

    /**
     * Server-declared hunt announcement, called via reflection from LoveBehavior's
     * LoveHuntBridge shortly after a Terrible-politeness player (with an active LoveHunt
     * auto-bounty) logs in. Independent of the purchasable slots above - not tied to any slot,
     * not persisted, just a one-off broadcast using its own {@code herald.gui.hunt-announcement-format}.
     */
    public void announceHuntedPlayer(String targetName) {
        String template = plugin.getLoveTweaksConfig().getHeraldGuiConfig().message("hunt-announcement-format");
        String text = template.replace("<player>", targetName == null ? "" : targetName);
        plugin.getServer().broadcast(colorize(text));
    }

    /**
     * Server-declared announcement that the contract board was refreshed, called via reflection
     * from LoveContracts' daily rotation. Same one-off broadcast as {@link #announceHuntedPlayer},
     * using {@code herald.gui.messages.contracts-announcement-format}.
     */
    public void announceContractsRotation() {
        String template = plugin.getLoveTweaksConfig().getHeraldGuiConfig().message("contracts-announcement-format");
        plugin.getServer().broadcast(colorize(template));
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

    private synchronized void load() {
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

    private synchronized void save() {
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
            if (dataFile.getParentFile() != null) {
                dataFile.getParentFile().mkdirs();
            }
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Не удалось сохранить herald-data.yml: " + exception.getMessage());
        }
    }
}

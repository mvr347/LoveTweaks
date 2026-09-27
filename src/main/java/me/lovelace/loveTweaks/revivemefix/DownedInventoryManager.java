package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link InventorySnapshot} per Downed player between the moment they go Downed and
 * the moment ReviveMe either revives them or finalizes their death.
 * <p>
 * {@link #take(UUID)} is the only way the death path should ever consume a snapshot: it removes
 * the entry atomically, so a duplicate/second death event for the same player finds nothing left
 * to drop instead of dropping the same items twice.
 */
final class DownedInventoryManager {

    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();

    /** Saves a snapshot if none exists for this player yet. */
    void save(Player player) {
        snapshots.putIfAbsent(player.getUniqueId(), InventorySnapshot.capture(player));
    }

    /** Forces updating an existing snapshot with the player's current inventory. */
    void update(Player player) {
        snapshots.computeIfPresent(player.getUniqueId(), (uuid, prev) -> InventorySnapshot.capture(player));
    }

    /** Saves or updates the snapshot. */
    void saveOrUpdate(Player player) {
        snapshots.put(player.getUniqueId(), InventorySnapshot.capture(player));
    }

    /** Atomically removes and returns the snapshot, or {@code null} if none exists. */
    InventorySnapshot take(UUID uuid) {
        return snapshots.remove(uuid);
    }

    boolean contains(UUID uuid) {
        return snapshots.containsKey(uuid);
    }

    void remove(UUID uuid) {
        snapshots.remove(uuid);
    }

    void clear() {
        snapshots.clear();
    }

    /** Drops snapshots nobody ever consumed (ReviveMe revive/death events never fired for them). */
    void cleanupExpired(long timeoutSeconds, java.util.function.Consumer<UUID> onExpired) {
        if (timeoutSeconds <= 0) {
            return;
        }
        Iterator<Map.Entry<UUID, InventorySnapshot>> it = snapshots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, InventorySnapshot> entry = it.next();
            if (entry.getValue().isExpired(timeoutSeconds)) {
                it.remove();
                if (onExpired != null) {
                    onExpired.accept(entry.getKey());
                }
            }
        }
    }
}

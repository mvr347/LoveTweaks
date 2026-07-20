package me.lovelace.loveTweaks.post;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** State of one in-flight carrier pigeon package. */
public record PostFlight(
        UUID entityUuid,
        ItemStack[] items,
        String senderName,
        UUID recipientUuid,
        String recipientName,
        Location start,
        Location target,
        long startMillis,
        long durationMillis
) {
    public double progress() {
        return Math.min(1.0, (System.currentTimeMillis() - startMillis) / (double) durationMillis);
    }
}

package me.lovelace.loveTweaks.herald;

import java.util.UUID;

/** One of the Herald's independent announcement slots — its own owner, message and expiry. */
public final class HeraldSlot {

    private UUID ownerId;
    private String ownerName;
    private String message;
    private long expiresAt;

    public boolean isActive() {
        return ownerId != null && message != null && !message.isEmpty()
                && System.currentTimeMillis() < expiresAt;
    }

    public void activate(UUID ownerId, String ownerName, String message, long durationMillis) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.message = message;
        this.expiresAt = System.currentTimeMillis() + durationMillis;
    }

    public void clear() {
        this.ownerId = null;
        this.ownerName = null;
        this.message = null;
        this.expiresAt = 0;
    }

    public UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public String message() { return message; }
    public long expiresAt() { return expiresAt; }
}

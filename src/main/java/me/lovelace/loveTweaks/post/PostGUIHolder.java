package me.lovelace.loveTweaks.post;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Backs one player's open "compose" session at the Royal Post NPC — the recipient name and
 * whether we're currently waiting for the player to type it in chat live on this holder, while
 * the deposited items live directly in the backing inventory's deposit slots.
 */
public class PostGUIHolder implements InventoryHolder {

    private final UUID playerUuid;
    private Inventory inventory;
    private String recipientName;
    private boolean awaitingRecipientInput;

    public PostGUIHolder(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public boolean isAwaitingRecipientInput() {
        return awaitingRecipientInput;
    }

    public void setAwaitingRecipientInput(boolean awaitingRecipientInput) {
        this.awaitingRecipientInput = awaitingRecipientInput;
    }
}

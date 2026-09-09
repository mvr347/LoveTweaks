package me.lovelace.loveTweaks.herald;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Per-session state for the Herald purchase GUI: which slot is being bought, the currently
 * selected duration, and the pending announcement text (set either by dropping a book on the
 * book slot or by typing it in chat — both funnel into the same field).
 */
public class HeraldPurchaseHolder implements InventoryHolder {

    public enum TextSource {
        NONE,
        CHAT,
        BOOK
    }

    private Inventory inventory;
    private final int slotIndex;
    private int durationMinutes;
    private String pendingMessage;
    private TextSource textSource = TextSource.NONE;

    public HeraldPurchaseHolder(int slotIndex, int initialDurationMinutes) {
        this.slotIndex = slotIndex;
        this.durationMinutes = initialDurationMinutes;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public int slotIndex() { return slotIndex; }
    public int durationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String pendingMessage() { return pendingMessage; }
    public void setPendingMessage(String pendingMessage) { this.pendingMessage = pendingMessage; }
    public TextSource getTextSource() { return textSource; }
    public void setTextSource(TextSource textSource) { this.textSource = textSource != null ? textSource : TextSource.NONE; }
}

package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardGUIHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final Map<Integer, String> slotToSection = new HashMap<>();
    private Inventory inventory;

    public ScoreboardGUIHolder(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
    public UUID getPlayerUuid() { return playerUuid; }

    public void mapSlot(int slot, String sectionId) { slotToSection.put(slot, sectionId); }
    public String getSectionAt(int slot) { return slotToSection.get(slot); }
    public void clearSlotMap() { slotToSection.clear(); }
}

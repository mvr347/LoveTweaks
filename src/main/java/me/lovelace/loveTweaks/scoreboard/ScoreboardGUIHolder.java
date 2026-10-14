package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Holder shared by the main /scoreboard menu and the category submenus. */
public class ScoreboardGUIHolder implements InventoryHolder {

    public enum Type { MAIN, CATEGORY }

    public enum Kind { PLACEHOLDER, CATEGORY, PRESET, VIEW_TOGGLE, PAGE_PREV, PAGE_NEXT }

    public record Action(Kind kind, String ref) {}

    private final UUID playerUuid;
    private final Type type;
    private final String categoryId;
    private int page;
    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    public ScoreboardGUIHolder(UUID playerUuid) {
        this(playerUuid, Type.MAIN, null);
    }

    public ScoreboardGUIHolder(UUID playerUuid, Type type, String categoryId) {
        this.playerUuid = playerUuid;
        this.type = type;
        this.categoryId = categoryId;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
    public UUID getPlayerUuid() { return playerUuid; }
    public Type getType() { return type; }
    public String getCategoryId() { return categoryId; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public void mapSlot(int slot, Kind kind, String ref) { actions.put(slot, new Action(kind, ref)); }
    public Action getAction(int slot) { return actions.get(slot); }
    public void clearSlotMap() { actions.clear(); }
}

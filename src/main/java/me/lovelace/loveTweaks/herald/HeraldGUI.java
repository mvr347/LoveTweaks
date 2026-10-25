package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Overview screen opened by right-clicking the Herald NPC: one info card plus one card per
 * announcement slot (free → click to buy, occupied → read-only). Follows this project's shared
 * GUI style: configurable item buttons and gray glass filler, contiguous content in the working zone (see lovetweaks-gui-style).
 */
public final class HeraldGUI {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_INFO = 0;
    public static final int[] SLOT_POSITIONS = {12, 13, 14};
    public static final int SLOT_CLOSE = 26;

    private HeraldGUI() {}

    public static void open(Player player, HeraldManager manager, LoveTweaks plugin) {
        HeraldGUIHolder holder = new HeraldGUIHolder();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        Component title = GuiItemUtil.colorize(heraldGui.overviewTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(heraldGui.fillerMaterial(), heraldGui.fillerName(), List.of());

        // Header: слоты 0-8 (слот 0 — инфо/тема, слоты 1-8 — стекло)
        inv.setItem(SLOT_INFO, buildInfoItem(player, plugin, heraldGui));
        for (int slot = 1; slot <= 8; slot++) {
            inv.setItem(slot, filler);
        }

        // Рабочая зона: слоты 9-17 (боковые стенки и незанятые слоты — пусто/null, стекло запрещено)
        int slotCount = Math.min(manager.slotCount(), SLOT_POSITIONS.length);
        for (int i = 0; i < slotCount; i++) {
            inv.setItem(SLOT_POSITIONS[i], buildSlotItem(player, plugin, manager, i, heraldGui));
        }

        // Footer: слоты 18-26 (18-25 — стекло, 26 — Close)
        for (int slot = 18; slot < 26; slot++) {
            inv.setItem(slot, filler);
        }
        inv.setItem(SLOT_CLOSE, GuiItemUtil.buildItem(player, heraldGui.closeMaterial(), heraldGui.closeName(), heraldGui.closeLore()));

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(Player player, LoveTweaks plugin, HeraldGuiConfig heraldGui) {
        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.infoLore()) {
            lore.add(line.replace("<max>", String.valueOf(plugin.getLoveTweaksConfig().getHeraldSlots())));
        }
        return GuiItemUtil.buildItem(player, heraldGui.infoMaterial(), heraldGui.infoName(), lore);
    }

    private static ItemStack buildSlotItem(Player player, LoveTweaks plugin, HeraldManager manager, int index, HeraldGuiConfig heraldGui) {
        HeraldSlot slot = manager.getSlot(index);
        String indexLabel = String.valueOf(index + 1);

        if (slot.isActive()) {
            long remainingMinutes = Duration.ofMillis(slot.expiresAt() - System.currentTimeMillis()).toMinutes();
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.slotOccupiedLore()) {
                lore.add(line
                        .replace("<index>", indexLabel)
                        .replace("<buyer>", slot.ownerName() == null ? "" : slot.ownerName())
                        .replace("<message>", slot.message() == null ? "" : slot.message())
                        .replace("<remaining>", String.valueOf(Math.max(0, remainingMinutes))));
            }
            String name = heraldGui.slotOccupiedName().replace("<index>", indexLabel);
            return GuiItemUtil.buildItem(player, heraldGui.slotOccupiedMaterial(), name, lore);
        }

        HeraldManager.Unavailability blocked = manager.unavailability(player, index);
        if (blocked != HeraldManager.Unavailability.NONE) {
            // HEAD_INACTIVE-style button: same name, reason in the lore, one shared "blocked" texture.
            String reason = reasonText(heraldGui, plugin, blocked, index);
            List<String> inactiveLore = new ArrayList<>();
            for (String line : heraldGui.slotUnavailableLore()) {
                inactiveLore.add(line.replace("<index>", indexLabel).replace("<reason>", reason));
            }
            String inactiveName = heraldGui.slotUnavailableName().replace("<index>", indexLabel);
            return GuiItemUtil.buildItem(player, heraldGui.confirmNoFundsMaterial(), inactiveName, inactiveLore);
        }

        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.slotFreeLore()) {
            lore.add(line.replace("<index>", indexLabel));
        }
        String name = heraldGui.slotFreeName().replace("<index>", indexLabel);
        return GuiItemUtil.buildItem(player, heraldGui.slotFreeMaterial(), name, lore);
    }

    /** Plain (uncolored) reason line for an unavailable free slot; empty for {@link HeraldManager.Unavailability#NONE}. */
    public static String reasonText(HeraldGuiConfig heraldGui, LoveTweaks plugin,
                                    HeraldManager.Unavailability reason, int slotIndex) {
        return switch (reason) {
            case SLOT_LOCKED -> heraldGui.message("reason-slot-locked").replace("<prev>", String.valueOf(slotIndex));
            case OWNS_SLOT -> heraldGui.message("reason-already-owns-slot");
            case NO_FUNDS -> heraldGui.message("reason-no-funds")
                    .replace("<min>", heraldGui.formatCost(plugin.getLoveTweaksConfig().getHeraldMinCost()));
            case NO_ECONOMY -> heraldGui.message("reason-no-economy");
            case NONE -> "";
        };
    }
}

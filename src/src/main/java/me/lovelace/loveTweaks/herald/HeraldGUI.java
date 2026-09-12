package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
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
 * GUI style: base64-head buttons reusing the scoreboard GUI's already-verified textures, gray
 * glass filler everywhere else, contiguous content in the working zone (see lovetweaks-gui-style).
 */
public final class HeraldGUI {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_INFO = 0;
    public static final int[] SLOT_POSITIONS = {12, 13, 14};
    public static final int SLOT_CLOSE = 26;

    private HeraldGUI() {}

    public static void open(Player player, HeraldManager manager, LoveTweaks plugin) {
        HeraldGUIHolder holder = new HeraldGUIHolder();
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        Component title = GuiItemUtil.colorize(heraldGui.overviewTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(sbConfig.getFillerMaterial(), sbConfig.getFillerName(), List.of());

        // Header: слоты 0-8 (слот 0 — инфо/тема, слоты 1-8 — стекло)
        inv.setItem(SLOT_INFO, buildInfoItem(player, plugin, sbConfig, heraldGui));
        for (int slot = 1; slot <= 8; slot++) {
            inv.setItem(slot, filler);
        }

        // Рабочая зона: слоты 9-17 (боковые стенки и незанятые слоты — пусто/null, стекло запрещено)
        int slotCount = Math.min(manager.slotCount(), SLOT_POSITIONS.length);
        for (int i = 0; i < slotCount; i++) {
            inv.setItem(SLOT_POSITIONS[i], buildSlotItem(player, manager, i, sbConfig, heraldGui));
        }

        // Footer: слоты 18-26 (18-25 — стекло, 26 — Close)
        for (int slot = 18; slot < 26; slot++) {
            inv.setItem(slot, filler);
        }
        inv.setItem(SLOT_CLOSE, GuiItemUtil.buildItem(player, sbConfig.getCloseMaterial(), sbConfig.getCloseName(), sbConfig.getCloseLore()));

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui) {
        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.infoLore()) {
            lore.add(line.replace("<max>", String.valueOf(plugin.getLoveTweaksConfig().getHeraldSlots())));
        }
        return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderOnMaterial(), heraldGui.infoName(), lore);
    }

    private static ItemStack buildSlotItem(Player player, HeraldManager manager, int index, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui) {
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
            return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderOffMaterial(), name, lore);
        }

        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.slotFreeLore()) {
            lore.add(line.replace("<index>", indexLabel));
        }
        String name = heraldGui.slotFreeName().replace("<index>", indexLabel);
        return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderOnMaterial(), name, lore);
    }
}

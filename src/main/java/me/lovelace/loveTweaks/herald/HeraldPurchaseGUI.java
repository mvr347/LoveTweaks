package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-slot purchase flow: drag a written book onto {@link #SLOT_BOOK} or click
 * {@link #SLOT_WRITE_CHAT} to type the announcement in chat, cycle {@link #SLOT_DURATION} to
 * pick how long it runs, then {@link #SLOT_CONFIRM} to pay and publish. Reopening this GUI
 * (e.g. after typing in chat) preserves the {@link HeraldPurchaseHolder} state passed in.
 */
public final class HeraldPurchaseGUI {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_BOOK = 10;
    public static final int SLOT_WRITE_CHAT = 12;
    public static final int SLOT_DURATION = 14;
    public static final int SLOT_CONFIRM = 16;
    public static final int SLOT_BACK = 18;
    public static final int SLOT_CLOSE = 26;

    private HeraldPurchaseGUI() {}

    public static void open(Player player, LoveTweaks plugin, HeraldPurchaseHolder holder) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        Component title = GuiItemUtil.colorize(heraldGui.purchaseTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(sbConfig.getFillerMaterial(), sbConfig.getFillerName(), List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }

        inv.setItem(SLOT_BOOK, buildBookItem(plugin, sbConfig, heraldGui));
        inv.setItem(SLOT_WRITE_CHAT, GuiItemUtil.buildItem(sbConfig.getToggleOnMaterial(), heraldGui.writeChatName(), heraldGui.writeChatLore()));
        inv.setItem(SLOT_DURATION, buildDurationItem(plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_CONFIRM, buildConfirmItem(plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_BACK, GuiItemUtil.buildItem(sbConfig.getBackMaterial(), sbConfig.getBackName(), sbConfig.getBackLore()));
        inv.setItem(SLOT_CLOSE, GuiItemUtil.buildItem(sbConfig.getCloseMaterial(), sbConfig.getCloseName(), sbConfig.getCloseLore()));

        player.openInventory(inv);
    }

    /** Rebuilds the duration/confirm/book items in-place after a state change, without reopening the inventory. */
    public static void refresh(Inventory inv, LoveTweaks plugin, HeraldPurchaseHolder holder) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        inv.setItem(SLOT_BOOK, buildBookItem(plugin, sbConfig, heraldGui));
        inv.setItem(SLOT_DURATION, buildDurationItem(plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_CONFIRM, buildConfirmItem(plugin, sbConfig, heraldGui, holder));
    }

    private static ItemStack buildBookItem(LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui) {
        List<String> lore = new ArrayList<>();
        int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
        for (String line : heraldGui.bookHintLore()) {
            lore.add(line.replace("<max>", String.valueOf(maxLength)));
        }
        return GuiItemUtil.buildItem(sbConfig.getToggleOffMaterial(), heraldGui.bookHintName(), lore);
    }

    private static ItemStack buildDurationItem(LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        var cfg = plugin.getLoveTweaksConfig();
        String message = holder.pendingMessage();
        int messageLength = message == null ? 0 : message.length();
        long cost = plugin.getHeraldManager().computeCost(holder.durationMinutes(), messageLength);

        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.durationLore()) {
            lore.add(line
                    .replace("<cost>", String.valueOf(cost))
                    .replace("<min>", String.valueOf(cfg.getHeraldMinDurationMinutes()))
                    .replace("<max>", String.valueOf(cfg.getHeraldMaxDurationMinutes())));
        }
        String name = heraldGui.durationName().replace("<minutes>", String.valueOf(holder.durationMinutes()));
        return GuiItemUtil.buildItem(sbConfig.getToggleOnMaterial(), name, lore);
    }

    private static ItemStack buildConfirmItem(LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        String message = holder.pendingMessage();
        if (message == null || message.isEmpty()) {
            return GuiItemUtil.buildItem(sbConfig.getPlaceholderBlockedMaterial(), heraldGui.confirmDisabledName(), heraldGui.confirmDisabledLore());
        }

        long cost = plugin.getHeraldManager().computeCost(holder.durationMinutes(), message.length());
        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.confirmLore()) {
            lore.add(line
                    .replace("<message>", message)
                    .replace("<minutes>", String.valueOf(holder.durationMinutes()))
                    .replace("<cost>", String.valueOf(cost)));
        }
        return GuiItemUtil.buildItem(sbConfig.getPlaceholderOnMaterial(), heraldGui.confirmName(), lore);
    }
}

package me.lovelace.loveTweaks.herald;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
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
    public static final int SLOT_INFO = 0;
    public static final int SLOT_BOOK = 10;
    public static final int SLOT_WRITE_CHAT = 12;
    public static final int SLOT_DURATION = 14;
    public static final int SLOT_CONFIRM = 16;
    public static final int SLOT_BACK = 25;
    public static final int SLOT_CLOSE = 26;

    private HeraldPurchaseGUI() {}

    public static void open(Player player, LoveTweaks plugin, HeraldPurchaseHolder holder) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        Component title = GuiItemUtil.colorize(heraldGui.purchaseTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(sbConfig.getFillerMaterial(), sbConfig.getFillerName(), List.of());

        // Header: слоты 0-8 (слот 0 — инфо о покупке слота, слоты 1-8 — стекло)
        inv.setItem(SLOT_INFO, buildPurchaseInfoItem(player, plugin, sbConfig, heraldGui, holder));
        for (int slot = 1; slot <= 8; slot++) {
            inv.setItem(slot, filler);
        }

        // Рабочая зона: слоты 9-17 (боковые стенки и разделители — пусто/null, стекло запрещено)
        inv.setItem(SLOT_BOOK, buildBookItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_WRITE_CHAT, buildWriteChatItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_DURATION, buildDurationItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_CONFIRM, buildConfirmItem(player, plugin, sbConfig, heraldGui, holder));

        // Footer: слоты 18-26 (18-24 — стекло, 25 — Back, 26 — Close)
        for (int slot = 18; slot <= 24; slot++) {
            inv.setItem(slot, filler);
        }
        inv.setItem(SLOT_BACK, GuiItemUtil.buildItem(player, sbConfig.getBackMaterial(), sbConfig.getBackName(), sbConfig.getBackLore()));
        inv.setItem(SLOT_CLOSE, GuiItemUtil.buildItem(player, sbConfig.getCloseMaterial(), sbConfig.getCloseName(), sbConfig.getCloseLore()));

        player.openInventory(inv);
    }

    private static ItemStack buildPurchaseInfoItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        String indexLabel = String.valueOf(holder.slotIndex() + 1);
        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.slotFreeLore()) {
            lore.add(line.replace("<index>", indexLabel));
        }
        String name = heraldGui.slotFreeName().replace("<index>", indexLabel);
        return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderOnMaterial(), name, lore);
    }

    /** Rebuilds the dynamic items in-place after a state change, without reopening the inventory. */
    public static void refresh(Player player, Inventory inv, LoveTweaks plugin, HeraldPurchaseHolder holder) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        HeraldGuiConfig heraldGui = plugin.getLoveTweaksConfig().getHeraldGuiConfig();

        inv.setItem(SLOT_BOOK, buildBookItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_WRITE_CHAT, buildWriteChatItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_DURATION, buildDurationItem(player, plugin, sbConfig, heraldGui, holder));
        inv.setItem(SLOT_CONFIRM, buildConfirmItem(player, plugin, sbConfig, heraldGui, holder));
    }

    private static ItemStack buildBookItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        String message = holder.pendingMessage();
        String safeMsg = message == null ? "" : message;

        if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.CHAT) {
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.bookDisabledLore()) {
                lore.add(line.replace("<message>", safeMsg));
            }
            return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderBlockedMaterial(), heraldGui.bookDisabledName(), lore);
        }

        if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.BOOK) {
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.bookActiveLore()) {
                lore.add(line.replace("<message>", safeMsg));
            }
            return GuiItemUtil.buildItem(player, sbConfig.getToggleOnMaterial(), heraldGui.bookActiveName(), lore);
        }

        List<String> lore = new ArrayList<>();
        int maxLength = plugin.getLoveTweaksConfig().getHeraldMaxMessageLength();
        for (String line : heraldGui.bookHintLore()) {
            lore.add(line.replace("<max>", String.valueOf(maxLength)));
        }
        return GuiItemUtil.buildItem(player, sbConfig.getToggleOffMaterial(), heraldGui.bookHintName(), lore);
    }

    private static ItemStack buildWriteChatItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        String message = holder.pendingMessage();
        String safeMsg = message == null ? "" : message;

        if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.BOOK) {
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.writeChatDisabledLore()) {
                lore.add(line.replace("<message>", safeMsg));
            }
            return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderBlockedMaterial(), heraldGui.writeChatDisabledName(), lore);
        }

        if (holder.getTextSource() == HeraldPurchaseHolder.TextSource.CHAT) {
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.writeChatActiveLore()) {
                lore.add(line.replace("<message>", safeMsg));
            }
            return GuiItemUtil.buildItem(player, sbConfig.getToggleOnMaterial(), heraldGui.writeChatActiveName(), lore);
        }

        return GuiItemUtil.buildItem(player, sbConfig.getToggleOnMaterial(), heraldGui.writeChatName(), heraldGui.writeChatLore());
    }

    private static ItemStack buildDurationItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        var cfg = plugin.getLoveTweaksConfig();
        String message = holder.pendingMessage();
        int messageLength = message == null ? 0 : message.length();
        long cost = plugin.getHeraldManager().computeCost(holder.durationMinutes(), messageLength);
        String formattedCost = heraldGui.formatCost(cost);

        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.durationLore()) {
            lore.add(line
                    .replace("<cost>", formattedCost)
                    .replace("<min>", String.valueOf(cfg.getHeraldMinDurationMinutes()))
                    .replace("<max>", String.valueOf(cfg.getHeraldMaxDurationMinutes())));
        }
        String name = heraldGui.durationName().replace("<minutes>", String.valueOf(holder.durationMinutes()));
        return GuiItemUtil.buildItem(player, sbConfig.getToggleOnMaterial(), name, lore);
    }

    private static ItemStack buildConfirmItem(Player player, LoveTweaks plugin, ScoreboardConfig sbConfig, HeraldGuiConfig heraldGui, HeraldPurchaseHolder holder) {
        String message = holder.pendingMessage();
        if (message == null || message.isEmpty()) {
            return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderBlockedMaterial(), heraldGui.confirmDisabledName(), heraldGui.confirmDisabledLore());
        }

        long cost = plugin.getHeraldManager().computeCost(holder.durationMinutes(), message.length());
        String formattedCost = heraldGui.formatCost(cost);

        var economy = LoveCore.service(LoveEconomy.class);
        boolean hasFunds = economy.isEmpty() || economy.get().has(player, cost);

        if (!hasFunds) {
            List<String> lore = new ArrayList<>();
            for (String line : heraldGui.confirmNoFundsLore()) {
                lore.add(line
                        .replace("<message>", message)
                        .replace("<minutes>", String.valueOf(holder.durationMinutes()))
                        .replace("<cost>", formattedCost));
            }
            return GuiItemUtil.buildItem(player, heraldGui.confirmNoFundsMaterial(), heraldGui.confirmNoFundsName(), lore);
        }

        List<String> lore = new ArrayList<>();
        for (String line : heraldGui.confirmLore()) {
            lore.add(line
                    .replace("<message>", message)
                    .replace("<minutes>", String.valueOf(holder.durationMinutes()))
                    .replace("<cost>", formattedCost));
        }
        return GuiItemUtil.buildItem(player, sbConfig.getPlaceholderOnMaterial(), heraldGui.confirmName(), lore);
    }
}

package me.lovelace.loveTweaks.post;

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
 * Standalone 27-slot menu opened by right-clicking the Postmaster NPC. Follows this project's
 * shared GUI style: base64-head buttons (reusing the scoreboard GUI's already-verified
 * on/off/close textures — see lovetweaks-gui-style), gray-glass filler everywhere except the
 * open deposit area, and the special-button/close slots (24/26) fixed by the standard
 * 27-slot footer layout.
 */
public final class PostGUI {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_RECIPIENT = 4;
    public static final int SLOT_SEND = 24;
    public static final int SLOT_CANCEL = 26;
    public static final int DEPOSIT_START = 9;
    public static final int DEPOSIT_END = 17; // inclusive

    private PostGUI() {}

    public static void open(Player player, PostGUIHolder holder, LoveTweaks plugin) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        Component title = GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getPostGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(sbConfig.getFillerMaterial(), sbConfig.getFillerName(), List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot < DEPOSIT_START || slot > DEPOSIT_END) {
                inv.setItem(slot, filler);
            }
        }

        refreshControls(holder, plugin);
        player.openInventory(inv);
    }

    /** Redraws only the control slots (recipient/send/cancel), leaving the deposit area untouched. */
    public static void refreshControls(PostGUIHolder holder, LoveTweaks plugin) {
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        Inventory inv = holder.getInventory();
        inv.setItem(SLOT_RECIPIENT, buildRecipientItem(holder, sbConfig));
        inv.setItem(SLOT_SEND, GuiItemUtil.buildItem(sbConfig.getPlaceholderOnMaterial(), "&aОтправить голубя",
                List.of("", "&7Стоимость: &f" + plugin.getLoveTweaksConfig().getPostCost() + " монет", "",
                        "&aЛКМ &7— отправить посылку")));
        inv.setItem(SLOT_CANCEL, GuiItemUtil.buildItem(sbConfig.getCloseMaterial(), "&cОтменить",
                List.of("", "&7Закрыть и забрать вложенные предметы", "", "&aЛКМ &7— отменить")));
    }

    private static ItemStack buildRecipientItem(PostGUIHolder holder, ScoreboardConfig sbConfig) {
        String recipient = holder.getRecipientName();
        List<String> lore = new ArrayList<>();
        if (recipient == null || recipient.isBlank()) {
            lore.add("");
            lore.add("&7Получатель не указан");
            lore.add("");
            lore.add("&aЛКМ &7— указать получателя в чате");
            return GuiItemUtil.buildItem(sbConfig.getPlaceholderOffMaterial(), "&eПолучатель: &7не указан", lore);
        }
        lore.add("");
        lore.add("&7Получатель: &f" + recipient);
        lore.add("");
        lore.add("&aЛКМ &7— сменить получателя");
        return GuiItemUtil.buildItem(sbConfig.getPlaceholderOnMaterial(), "&eПолучатель: &f" + recipient, lore);
    }
}

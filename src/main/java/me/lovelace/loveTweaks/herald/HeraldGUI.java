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
 * Standalone 27-slot menu opened by right-clicking the Herald NPC. Follows this project's
 * shared GUI style: base64-head buttons (reusing the scoreboard GUI's already-verified
 * on/blocked/close textures — see lovetweaks-gui-style), gray-glass filler everywhere else,
 * and the special-button/close slots (24/26) fixed by the standard 27-slot footer layout.
 */
public final class HeraldGUI {

    public static final int GUI_SIZE = 27;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_BUY = 24;
    public static final int SLOT_CLOSE = 26;

    private HeraldGUI() {}

    public static void open(Player player, HeraldManager manager, LoveTweaks plugin) {
        HeraldGUIHolder holder = new HeraldGUIHolder();
        ScoreboardConfig sbConfig = plugin.getScoreboardConfig();
        Component title = GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getHeraldGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = GuiItemUtil.buildItem(sbConfig.getFillerMaterial(), sbConfig.getFillerName(), List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }

        inv.setItem(SLOT_INFO, buildInfoItem(manager, sbConfig));
        inv.setItem(SLOT_BUY, buildBuyItem(manager, plugin, sbConfig));
        inv.setItem(SLOT_CLOSE, GuiItemUtil.buildItem(sbConfig.getCloseMaterial(), sbConfig.getCloseName(), sbConfig.getCloseLore()));

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(HeraldManager manager, ScoreboardConfig sbConfig) {
        List<String> lore = new ArrayList<>();
        if (manager.isActive()) {
            lore.add("&7Голос принадлежит: &f" + manager.getBuyerName());
            lore.add("");
            for (String line : manager.getMessages()) {
                lore.add("&e▸ &f" + line);
            }
            lore.add("");
            long remainingMinutes = Duration.ofMillis(manager.getExpiresAt() - System.currentTimeMillis()).toMinutes();
            lore.add("&7Осталось: &f" + Math.max(0, remainingMinutes) + " мин.");
            return GuiItemUtil.buildItem(sbConfig.getPlaceholderOnMaterial(), "&6Голос Королевства &aактивен", lore);
        }
        lore.add("&7Сейчас никто не выкупил голос.");
        return GuiItemUtil.buildItem(sbConfig.getPlaceholderOffMaterial(), "&6Голос Королевства &7свободен", lore);
    }

    private static ItemStack buildBuyItem(HeraldManager manager, LoveTweaks plugin, ScoreboardConfig sbConfig) {
        if (manager.isActive()) {
            List<String> lore = List.of("", "&cНедоступно", "&7Голос уже выкуплен на сутки.");
            return GuiItemUtil.buildItem(sbConfig.getPlaceholderBlockedMaterial(), "&8Купить голос Королевства", lore);
        }
        List<String> lore = List.of(
                "",
                "&7Стоимость: &f" + plugin.getLoveTweaksConfig().getHeraldCost() + " монет",
                "",
                "&aЛКМ &7— выкупить голос на 24 часа"
        );
        return GuiItemUtil.buildItem(sbConfig.getPlaceholderOnMaterial(), "&aКупить голос Королевства", lore);
    }
}

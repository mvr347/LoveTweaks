package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.LoveTweaksConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class HeraldGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int GUI_SIZE = 27;
    public static final int SLOT_INFO = 13;
    public static final int SLOT_BUY = 22;

    private HeraldGUI() {}

    public static void open(Player player, HeraldManager manager, LoveTweaksConfig config) {
        HeraldGUIHolder holder = new HeraldGUIHolder();
        Component title = colorize(config.getHeraldGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }

        inv.setItem(SLOT_INFO, buildInfoItem(manager));
        inv.setItem(SLOT_BUY, buildBuyItem(manager, config));

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(HeraldManager manager) {
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
            return buildItem(Material.WRITTEN_BOOK, "&6Голос Королевства &aактивен", lore);
        }
        lore.add("&7Сейчас никто не выкупил голос.");
        return buildItem(Material.BOOK, "&6Голос Королевства &7свободен", lore);
    }

    private static ItemStack buildBuyItem(HeraldManager manager, LoveTweaksConfig config) {
        if (manager.isActive()) {
            List<String> lore = List.of("&7Голос уже выкуплен на сутки.", "&7Попробуйте позже.");
            return buildItem(Material.GRAY_DYE, "&7Недоступно", lore);
        }
        List<String> lore = List.of(
                "&7Стоимость: &f" + config.getHeraldCost() + " монет",
                "",
                "&aЛКМ &7— выкупить голос на 24 часа"
        );
        return buildItem(Material.EMERALD, "&aКупить голос Королевства", lore);
    }

    private static ItemStack buildItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(colorize(displayName));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(colorize(line));
        }
        meta.lore(loreComponents);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static Component colorize(String text) {
        return LEGACY.deserialize(text.replace('&', '§'));
    }
}

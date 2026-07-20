package me.lovelace.loveTweaks.post;

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

import java.util.ArrayList;
import java.util.List;

public final class PostGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int GUI_SIZE = 27;
    public static final int SLOT_RECIPIENT = 4;
    public static final int SLOT_CANCEL = 20;
    public static final int SLOT_SEND = 24;
    public static final int DEPOSIT_START = 9;
    public static final int DEPOSIT_END = 17; // inclusive

    private PostGUI() {}

    public static void open(Player player, PostGUIHolder holder, LoveTweaksConfig config) {
        Component title = colorize(config.getPostGuiTitle());
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot < DEPOSIT_START || slot > DEPOSIT_END) {
                inv.setItem(slot, filler);
            }
        }

        refreshControls(holder, config);
        player.openInventory(inv);
    }

    /** Redraws only the control slots (recipient/cancel/send), leaving the deposit area untouched. */
    public static void refreshControls(PostGUIHolder holder, LoveTweaksConfig config) {
        Inventory inv = holder.getInventory();
        inv.setItem(SLOT_RECIPIENT, buildRecipientItem(holder));
        inv.setItem(SLOT_CANCEL, buildItem(Material.BARRIER, "&cОтменить",
                List.of("&7Закрыть и забрать вложенные предметы")));
        inv.setItem(SLOT_SEND, buildItem(Material.FEATHER, "&aОтправить голубя",
                List.of("&7Стоимость: &f" + config.getPostCost() + " монет", "", "&aЛКМ &7— отправить посылку")));
    }

    private static ItemStack buildRecipientItem(PostGUIHolder holder) {
        String recipient = holder.getRecipientName();
        List<String> lore = new ArrayList<>();
        if (recipient == null || recipient.isBlank()) {
            lore.add("&7Получатель не указан");
            lore.add("");
            lore.add("&aЛКМ &7— указать получателя в чате");
            return buildItem(Material.PAPER, "&eПолучатель: &7не указан", lore);
        }
        lore.add("&7Получатель: &f" + recipient);
        lore.add("");
        lore.add("&aЛКМ &7— сменить получателя");
        return buildItem(Material.WRITABLE_BOOK, "&eПолучатель: &f" + recipient, lore);
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

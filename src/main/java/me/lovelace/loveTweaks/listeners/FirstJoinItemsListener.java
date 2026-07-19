package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.FirstJoinItem;
import me.lovelace.loveTweaks.items.TeleportScroll;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Выдаёт настроенный в config.yml (секция {@code first-join}) стартовый набор предметов
 * при самом первом заходе игрока на сервер.
 */
public class FirstJoinItemsListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final LoveTweaks plugin;

    public FirstJoinItemsListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // hasPlayedBefore() надёжно определяет самый первый вход — в отличие от собственного
        // флага, его не нужно отдельно хранить и он не может рассинхронизироваться.
        if (player.hasPlayedBefore()) {
            return;
        }
        if (!plugin.getLoveTweaksConfig().isFirstJoinEnabled()) {
            return;
        }

        for (FirstJoinItem item : plugin.getLoveTweaksConfig().getFirstJoinItems()) {
            giveItem(player, item);
        }
    }

    private void giveItem(Player player, FirstJoinItem item) {
        ItemStack stack = item.teleportScroll() ? TeleportScroll.create() : buildItem(item);
        stack.setAmount(Math.min(item.amount(), stack.getMaxStackSize()));

        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private ItemStack buildItem(FirstJoinItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (item.displayName() != null && !item.displayName().isBlank()) {
            meta.displayName(
                    LEGACY.deserialize(item.displayName()).decoration(TextDecoration.ITALIC, false)
            );
        }

        if (!item.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : item.lore()) {
                lore.add(LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        stack.setItemMeta(meta);
        return stack;
    }
}

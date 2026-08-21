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

    private final org.bukkit.NamespacedKey firstJoinKey;

    public FirstJoinItemsListener(LoveTweaks plugin) {
        this.plugin = plugin;
        this.firstJoinKey = new org.bukkit.NamespacedKey(plugin, "first_join_received");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isAuthenticated(event.getPlayer())) {
            giveFirstJoinItemsIfEligible(event.getPlayer());
        }
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        giveFirstJoinItemsIfEligible(event.player());
    }

    private void giveFirstJoinItemsIfEligible(Player player) {
        if (player.hasPlayedBefore() || player.getPersistentDataContainer().has(firstJoinKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return;
        }
        if (!plugin.getLoveTweaksConfig().isFirstJoinEnabled()) {
            return;
        }

        player.getPersistentDataContainer().set(firstJoinKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

        for (FirstJoinItem item : plugin.getLoveTweaksConfig().getFirstJoinItems()) {
            giveItem(player, item);
        }
    }

    /**
     * Не кэшируем Optional<AuthOracle> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore. Если LoveAuth не установлен, набор
     * выдаётся сразу на join, как и раньше.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
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

package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Выброшенные предметы могут потеряться (исчезнуть) или, если у них есть
 * прочность, сломаться при падении.
 */
public class ItemDropLossListener implements Listener {

    private final LoveTweaks plugin;

    public ItemDropLossListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getLoveTweaksConfig().isItemDropLossEnabled()) {
            return;
        }

        Item itemEntity = event.getItemDrop();
        ItemStack stack = itemEntity.getItemStack();
        Player player = event.getPlayer();

        if (hasDurability(stack)) {
            if (roll(plugin.getLoveTweaksConfig().getItemDropBreakChance())) {
                breakItem(player, itemEntity);
            }
        } else {
            if (roll(plugin.getLoveTweaksConfig().getItemDropLossChance())) {
                loseItem(player, itemEntity);
            }
        }
    }

    private boolean hasDurability(ItemStack stack) {
        return stack.getType().getMaxDurability() > 0 && stack.getItemMeta() instanceof Damageable;
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private void breakItem(Player player, Item itemEntity) {
        Location location = itemEntity.getLocation();
        itemEntity.remove();

        location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        location.getWorld().spawnParticle(Particle.CLOUD, location, 12, 0.2, 0.2, 0.2, 0);

        player.sendMessage(Component.text("Ваш предмет сломался при падении!", NamedTextColor.RED));
    }

    private void loseItem(Player player, Item itemEntity) {
        Location location = itemEntity.getLocation();
        itemEntity.remove();

        location.getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
        location.getWorld().spawnParticle(Particle.SMOKE, location, 10, 0.2, 0.2, 0.2, 0.02);

        player.sendMessage(Component.text("Ваш предмет потерялся при падении!", NamedTextColor.GRAY));
    }
}

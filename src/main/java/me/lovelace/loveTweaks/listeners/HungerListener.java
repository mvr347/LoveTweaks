package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class HungerListener implements Listener {

    private final LoveTweaks plugin;

    public HungerListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!item.getType().isEdible()) {
            return;
        }

        Player player = event.getPlayer();
        float saturationBefore = player.getSaturation();

        // Откладываем на 1 тик, чтобы ваниль успела применить насыщение от еды,
        // и только потом скорректировать его множителем из конфига
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            float gained = player.getSaturation() - saturationBefore;
            if (gained > 0) {
                float saturationMultiplier = plugin.getLoveTweaksConfig().getSaturationMultiplier();
                player.setSaturation(saturationBefore + gained * saturationMultiplier);
            }
        }, 1L);
    }
}

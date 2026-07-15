package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.ArrayList;

public class AchievementListener implements Listener {

    private final LoveTweaks plugin;

    public AchievementListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableVanillaAchievements()) {
            AdvancementProgress progress = event.getPlayer().getAdvancementProgress(event.getAdvancement());
            for (String criterion : new ArrayList<>(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        }
    }
}

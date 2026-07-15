package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.achievements.AchievementCriterion;
import me.lovelace.loveTweaks.achievements.CustomAchievement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class AchievementProgressListener implements Listener {

    private final LoveTweaks plugin;

    public AchievementProgressListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        for (CustomAchievement achievement : plugin.getCustomAchievementManager().getAchievements().values()) {
            if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                continue; // Player already has this achievement
            }

            for (AchievementCriterion criterion : achievement.criteria()) {
                if (criterion.type() == AchievementCriterion.CriterionType.BREAK_BLOCK &&
                    criterion.material() == event.getBlock().getType()) {

                    String criterionString = criterion.type().name() + ":" + criterion.material().name();
                    plugin.getPlayerAchievementData().incrementCriterionProgress(player.getUniqueId(), achievement.id(), criterionString, 1);

                    if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                        plugin.getCustomAchievementManager().grantAchievement(player, achievement.id());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack craftedItem = event.getRecipe().getResult();
        if (craftedItem == null) {
            return;
        }

        for (CustomAchievement achievement : plugin.getCustomAchievementManager().getAchievements().values()) {
            if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                continue; // Player already has this achievement
            }

            for (AchievementCriterion criterion : achievement.criteria()) {
                if (criterion.type() == AchievementCriterion.CriterionType.CRAFT_ITEM &&
                    criterion.material() == craftedItem.getType()) {

                    String criterionString = criterion.type().name() + ":" + criterion.material().name();
                    plugin.getPlayerAchievementData().incrementCriterionProgress(player.getUniqueId(), achievement.id(), criterionString, craftedItem.getAmount());

                    if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                        plugin.getCustomAchievementManager().grantAchievement(player, achievement.id());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            for (CustomAchievement achievement : plugin.getCustomAchievementManager().getAchievements().values()) {
                if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                    continue; // Player already has this achievement
                }

                for (AchievementCriterion criterion : achievement.criteria()) {
                    if (criterion.type() == AchievementCriterion.CriterionType.KILL_ENTITY &&
                        criterion.entityType() == event.getEntityType()) {

                        String criterionString = criterion.type().name() + ":" + criterion.entityType().name();
                        plugin.getPlayerAchievementData().incrementCriterionProgress(player.getUniqueId(), achievement.id(), criterionString, 1);

                        if (plugin.getCustomAchievementManager().hasAchievement(player, achievement.id())) {
                            plugin.getCustomAchievementManager().grantAchievement(player, achievement.id());
                        }
                    }
                }
            }
        }
    }
}

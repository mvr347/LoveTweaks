package me.lovelace.loveTweaks.enchantments;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Repeating task for "Притяжение": every few ticks pulls nearby dropped items toward players wearing enchanted
 * leggings. A task is used instead of PlayerMoveEvent so a standing player is also served and the pull is smooth.
 */
public final class MagnetTask implements Runnable {

    public static final long INTERVAL_TICKS = 4L;
    /** An item the wearer threw away is ignored for this long, otherwise it would be sucked straight back. */
    private static final int OWN_THROW_GRACE_TICKS = 40;

    private final LoveTweaks plugin;
    private final CustomEnchantManager manager;

    public MagnetTask(LoveTweaks plugin, CustomEnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            ItemStack leggings = player.getInventory().getLeggings();
            if (leggings == null || leggings.getType().isAir()) {
                continue;
            }
            int level = manager.getLevel(leggings, CustomEnchantType.PRITYAZHENIE);
            if (level <= 0) {
                continue;
            }
            pullItems(player, MagnetMath.radius(level));
        }
    }

    private void pullItems(Player player, double radius) {
        Location center = player.getLocation().add(0, 0.8, 0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Item item) || !item.isValid()) {
                continue;
            }
            if (item.getPickupDelay() > 0) {
                // not pickable yet (fresh drop or infinite delay): pulling it would only make it jitter
                continue;
            }
            if (player.getUniqueId().equals(item.getThrower()) && item.getTicksLived() < OWN_THROW_GRACE_TICKS) {
                continue;
            }
            Location at = item.getLocation();
            double[] v = MagnetMath.pull(center.getX() - at.getX(), center.getY() - at.getY(),
                    center.getZ() - at.getZ(), radius);
            if (v != null) {
                item.setVelocity(new Vector(v[0], v[1], v[2]));
            }
        }
    }
}

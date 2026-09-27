package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles physical and event drops for a downed player's captured inventory upon death.
 * <p>
 * Supported {@link DropMode} strategies:
 * <ul>
 *   <li>{@link DropMode#NATURAL}: Directly spawns items into the world via {@link World#dropItemNaturally}.
 *       This is completely immune to other plugins (DeluxeCombat, ReviveMe, CMI) clearing
 *       {@link PlayerDeathEvent#getDrops()} or enforcing {@link PlayerDeathEvent#setKeepInventory(true)}.</li>
 *   <li>{@link DropMode#EVENT_DROPS}: Populates {@link PlayerDeathEvent#getDrops()} and resets keepInventory.</li>
 *   <li>{@link DropMode#BOTH}: Both spawns into the world and provides event drops.</li>
 * </ul>
 */
public final class DeathDropHandler {

    public enum DropMode {
        NATURAL,
        EVENT_DROPS,
        BOTH;

        public static DropMode fromString(String name) {
            if (name == null) {
                return NATURAL;
            }
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return NATURAL;
            }
        }
    }

    private DeathDropHandler() {
    }

    /**
     * Executes the drop logic for a downed player's snapshot.
     */
    public static void dropSnapshot(Player player, Location deathLocation, InventorySnapshot snapshot,
                                    PlayerDeathEvent deathEvent, DropMode dropMode,
                                    boolean clearInventory, boolean dropExp) {
        if (player == null || snapshot == null) {
            return;
        }

        Location loc = deathLocation != null ? deathLocation : player.getLocation();
        World world = loc.getWorld();

        // 1. Gather all non-empty items
        List<ItemStack> itemsToDrop = new ArrayList<>();
        for (ItemStack item : snapshot.allItems()) {
            if (isValid(item)) {
                itemsToDrop.add(item.clone());
            }
        }

        // 2. Natural world drop (guaranteed to hit the ground)
        if (dropMode == DropMode.NATURAL || dropMode == DropMode.BOTH) {
            if (world != null) {
                for (ItemStack item : itemsToDrop) {
                    world.dropItemNaturally(loc, item);
                }
            }
        }

        // 3. Coordinate with PlayerDeathEvent
        if (deathEvent != null) {
            deathEvent.setKeepInventory(false);
            if (dropMode == DropMode.EVENT_DROPS || dropMode == DropMode.BOTH) {
                deathEvent.getDrops().clear();
                for (ItemStack item : itemsToDrop) {
                    deathEvent.getDrops().add(item.clone());
                }
            } else if (dropMode == DropMode.NATURAL) {
                // Clear event drops so vanilla/Paper does not duplicate items already dropped into the world
                deathEvent.getDrops().clear();
            }

            if (dropExp && snapshot.totalExperience() > 0) {
                deathEvent.setKeepLevel(false);
                deathEvent.setDroppedExp(Math.min(snapshot.totalExperience(), 100)); // vanilla cap is usually 100 exp or full
            }
        } else if (dropExp && world != null && snapshot.totalExperience() > 0) {
            ExperienceOrb orb = world.spawn(loc, ExperienceOrb.class);
            orb.setExperience(Math.min(snapshot.totalExperience(), 100));
        }

        // 4. Clear the player's live inventory to prevent item retention upon respawn
        if (clearInventory) {
            try {
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);
                player.getInventory().setItemInOffHand(null);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isValid(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }
}

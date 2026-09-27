package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;

/**
 * Configuration for the {@code revive-inventory-fix} section in LoveTweaks config.
 */
public final class ReviveMeInventoryFixConfig {

    private boolean enabled = true;
    private boolean requireReviveMe = true;
    private DeathDropHandler.DropMode dropMode = DeathDropHandler.DropMode.NATURAL;
    private boolean removeOnRevive = true;
    private boolean clearSnapshotOnQuit = false;
    private boolean clearInventoryOnDeath = true;
    private boolean forceDropOnDownedDeath = true;
    private boolean dropExp = false;
    private int snapshotTimeoutSeconds = 300;
    private EventPriority deathEventPriority = EventPriority.HIGHEST;
    private boolean debug = false;

    public void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        requireReviveMe = section.getBoolean("require-reviveme", true);
        dropMode = DeathDropHandler.DropMode.fromString(section.getString("drop-mode", "NATURAL"));
        removeOnRevive = section.getBoolean("remove-on-revive", true);
        clearSnapshotOnQuit = section.getBoolean("clear-snapshot-on-quit", false);
        clearInventoryOnDeath = section.getBoolean("clear-inventory-on-death", true);
        forceDropOnDownedDeath = section.getBoolean("force-drop-on-downed-death", true);
        dropExp = section.getBoolean("drop-exp", false);
        snapshotTimeoutSeconds = Math.max(0, section.getInt("snapshot-timeout-seconds", 300));
        debug = section.getBoolean("debug", false);

        String priorityName = section.getString("death-event-priority", "HIGHEST");
        try {
            deathEventPriority = EventPriority.valueOf(priorityName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            deathEventPriority = EventPriority.HIGHEST;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequireReviveMe() {
        return requireReviveMe;
    }

    public DeathDropHandler.DropMode getDropMode() {
        return dropMode;
    }

    public boolean isRemoveOnRevive() {
        return removeOnRevive;
    }

    public boolean isClearSnapshotOnQuit() {
        return clearSnapshotOnQuit;
    }

    public boolean isClearInventoryOnDeath() {
        return clearInventoryOnDeath;
    }

    public boolean isForceDropOnDownedDeath() {
        return forceDropOnDownedDeath;
    }

    public boolean isDropExp() {
        return dropExp;
    }

    public int getSnapshotTimeoutSeconds() {
        return snapshotTimeoutSeconds;
    }

    public EventPriority getDeathEventPriority() {
        return deathEventPriority;
    }

    public boolean isDebug() {
        return debug;
    }
}

package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;

/** Config for the {@code revive-inventory-fix} section — see {@code config.yml} for what each key means and why. */
final class ReviveMeInventoryFixConfig {

    private boolean enabled = true;
    private boolean requireReviveMe = true;
    private boolean removeOnRevive = true;
    private boolean clearSnapshotOnQuit = false;
    private int snapshotTimeoutSeconds = 300;
    private EventPriority deathEventPriority = EventPriority.HIGHEST;
    private boolean debug = false;

    void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        requireReviveMe = section.getBoolean("require-reviveme", true);
        removeOnRevive = section.getBoolean("remove-on-revive", true);
        clearSnapshotOnQuit = section.getBoolean("clear-snapshot-on-quit", false);
        snapshotTimeoutSeconds = Math.max(0, section.getInt("snapshot-timeout-seconds", 300));
        debug = section.getBoolean("debug", false);

        String priorityName = section.getString("death-event-priority", "HIGHEST");
        try {
            deathEventPriority = EventPriority.valueOf(priorityName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            deathEventPriority = EventPriority.HIGHEST;
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isRequireReviveMe() {
        return requireReviveMe;
    }

    boolean isRemoveOnRevive() {
        return removeOnRevive;
    }

    boolean isClearSnapshotOnQuit() {
        return clearSnapshotOnQuit;
    }

    int getSnapshotTimeoutSeconds() {
        return snapshotTimeoutSeconds;
    }

    EventPriority getDeathEventPriority() {
        return deathEventPriority;
    }

    boolean isDebug() {
        return debug;
    }
}

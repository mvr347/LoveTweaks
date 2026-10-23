package me.lovelace.loveTweaks.revivemefix;

import java.util.Locale;

/**
 * Which plugin provides the "downed / revive" mechanic ({@code revive.provider} in config.yml).
 * <p>
 * The ReviveMe workarounds in this package (death-drop snapshot, quit guard) exist only because of
 * ReviveMe bugs; LoveEssential's own revive module does not have them, so they are not registered for it.
 */
public enum ReviveProvider {
    REVIVEME("reviveme", true),
    LOVEESSENTIAL("loveessential", false);

    public static final String CONFIG_PATH = "revive.provider";

    private final String id;
    private final boolean needsReviveMeFixes;

    ReviveProvider(String id, boolean needsReviveMeFixes) {
        this.id = id;
        this.needsReviveMeFixes = needsReviveMeFixes;
    }

    public String id() {
        return id;
    }

    /** True if the ReviveMe death-drop fix and quit guard must be registered for this provider. */
    public boolean needsReviveMeFixes() {
        return needsReviveMeFixes;
    }

    /** Config value that follows {@code integrations.provider} of LoveCore. */
    public static final String GLOBAL = "global";

    /** Whether {@code raw} is blank or {@code global} (the global choice of LoveCore) or names a known provider. */
    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals(GLOBAL)) {
            return true;
        }
        for (ReviveProvider provider : values()) {
            if (provider.id.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the config value. {@code global}, blank or missing follows {@code integrations.provider} of LoveCore
     * (without LoveCore: {@link #REVIVEME}); unknown values fall back to the default, {@link #REVIVEME}.
     * Read when the config is loaded, so a change of the global choice needs a reload of LoveTweaks.
     */
    public static ReviveProvider parse(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase(GLOBAL)) {
            try {
                raw = dev.lovelace.lovecore.api.LoveCore.providerId(
                        dev.lovelace.lovecore.api.provider.ProviderKind.REVIVE, GLOBAL);
            } catch (LinkageError coreMissingOrOld) {
                return REVIVEME;
            }
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (ReviveProvider provider : values()) {
            if (provider.id.equals(value)) {
                return provider;
            }
        }
        return REVIVEME;
    }
}

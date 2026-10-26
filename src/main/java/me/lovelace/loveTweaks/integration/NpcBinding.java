package me.lovelace.loveTweaks.integration;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Pure (Bukkit-server-free) helpers for the NPC provider choice and the stored NPC binding.
 * <p>
 * The binding used to be a numeric Citizens id ({@code herald.npc-id}); it is now a String key
 * ({@code herald.npc-key}) because other providers (LoveEssential: {@code ext-xxxxxxxx}) use
 * non-numeric keys. A number is still a valid key for the {@code citizens} provider.
 */
public final class NpcBinding {

    public static final String PROVIDER_PATH = "integrations.npc-provider";
    public static final String DEFAULT_PROVIDER = "citizens";
    public static final String CITIZENS = "citizens";

    static final String KEY_PATH = "npc-key";
    static final String LEGACY_ID_PATH = "npc-id";

    private NpcBinding() {
    }

    /** Config value that follows {@code integrations.provider} of LoveCore. */
    public static final String GLOBAL = "global";

    /**
     * Normalises the configured provider id. {@code global}, blank or missing follows {@code integrations.provider}
     * of LoveCore (without LoveCore: {@code citizens}); an explicit id is trimmed and lower-cased.
     */
    public static String normalizeProvider(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.equals(GLOBAL)) {
            try {
                return dev.lovelace.lovecore.api.LoveCore.providerId(
                        dev.lovelace.lovecore.api.provider.ProviderKind.NPC, GLOBAL);
            } catch (LinkageError coreMissingOrOld) {
                return DEFAULT_PROVIDER;
            }
        }
        return value;
    }

    /** Reads {@code integrations.npc-provider} from the root config. */
    public static String providerFrom(ConfigurationSection root) {
        return normalizeProvider(root == null ? null : root.getString(PROVIDER_PATH));
    }

    /**
     * Reads the bound NPC key from the {@code herald} section. The new {@code npc-key} wins; the
     * legacy numeric {@code npc-id} (negative = unbound) is used only when no key is stored.
     *
     * @return the key, or an empty string when nothing is bound
     */
    public static String readKey(ConfigurationSection herald) {
        if (herald == null) {
            return "";
        }
        if (herald.isSet(KEY_PATH)) {
            String key = herald.getString(KEY_PATH, "");
            if (key != null && !key.isBlank()) {
                return key.trim();
            }
        }
        if (herald.isSet(LEGACY_ID_PATH)) {
            int legacy = herald.getInt(LEGACY_ID_PATH, -1);
            if (legacy >= 0) {
                return Integer.toString(legacy);
            }
        }
        return "";
    }

    /**
     * Moves a legacy {@code npc-id} into {@code npc-key} and removes the old key, so that unbinding
     * later cannot resurrect the stale id.
     *
     * @return true if the section was changed (and should be saved)
     */
    public static boolean migrateLegacy(ConfigurationSection herald) {
        if (herald == null || !herald.isSet(LEGACY_ID_PATH)) {
            return false;
        }
        String existing = herald.isSet(KEY_PATH) ? herald.getString(KEY_PATH, "") : "";
        if (existing == null || existing.isBlank()) {
            int legacy = herald.getInt(LEGACY_ID_PATH, -1);
            herald.set(KEY_PATH, legacy >= 0 ? Integer.toString(legacy) : "");
        }
        herald.set(LEGACY_ID_PATH, null);
        return true;
    }

    /** Writes a binding (empty key = unbound) and drops the legacy id. */
    public static void write(ConfigurationSection herald, String key, String name) {
        herald.set(KEY_PATH, key == null ? "" : key);
        herald.set("npc-name", name == null ? "" : name);
        herald.set(LEGACY_ID_PATH, null);
    }

    /** A Citizens NPC key is the numeric Citizens id written as text. */
    public static boolean isNumericKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Citizens requires a numeric key. LoveEssential accepts its stable keys and visible-number
     * aliases, so a numeric key alone does not establish that its provider is wrong.
     * Used only for a warning: the key is never rewritten.
     */
    public static boolean keyLooksForeign(String provider, String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        boolean numeric = isNumericKey(key);
        return CITIZENS.equals(provider) && !numeric;
    }

    /**
     * Whether an NPC seen by an event is the bound one. {@code NpcClickEvent} fires for every
     * provider, so the provider id must be compared as well as the key.
     */
    public static boolean matches(String configuredProvider, String boundKey, String npcProviderId, String npcKey) {
        if (boundKey == null || boundKey.isEmpty() || npcKey == null) {
            return false;
        }
        return configuredProvider.equals(normalizeProvider(npcProviderId)) && boundKey.equals(npcKey);
    }
}

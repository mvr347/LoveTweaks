package me.lovelace.loveTweaks.integration;

import dev.lovelace.lovecore.api.provider.Providers;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Pure (Bukkit-server-free) helpers for the stored NPC binding.
 * <p>
 * The binding used to be a numeric id ({@code herald.npc-id}); it is now a String key
 * ({@code herald.npc-key}): LoveEssential's {@code ext-xxxxxxxx} id, its name or its visible number.
 * 2026-11-21: LoveEssential is the only NPC provider, so there is no provider choice any more.
 */
public final class NpcBinding {

    /** The only supported NPC provider. */
    public static final String PROVIDER_ID = Providers.LOVE_ESSENTIAL;
    /** Leftover key of the removed provider switch: read only to report that its value is ignored. */
    public static final String LEGACY_PROVIDER_PATH = "integrations.npc-provider";

    static final String KEY_PATH = "npc-key";
    static final String LEGACY_ID_PATH = "npc-id";

    private NpcBinding() {
    }

    /**
     * The leftover {@code integrations.npc-provider} value that is ignored, or {@code null} when it is absent,
     * blank, {@code global} or already {@code loveessential}.
     */
    public static String ignoredProviderValue(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("global") || value.equalsIgnoreCase(PROVIDER_ID)) return null;
        return value;
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

    /**
     * Whether an NPC seen by an event is the bound one. {@code NpcClickEvent} fires for every
     * provider, so the provider id must be compared as well as the key.
     */
    public static boolean matches(String boundKey, String npcProviderId, String npcKey) {
        if (boundKey == null || boundKey.isEmpty() || npcKey == null) {
            return false;
        }
        return PROVIDER_ID.equalsIgnoreCase(npcProviderId == null ? "" : npcProviderId.trim()) && boundKey.equals(npcKey);
    }
}

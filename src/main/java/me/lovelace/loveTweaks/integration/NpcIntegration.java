package me.lovelace.loveTweaks.integration;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * NPC access through LoveCore's {@link NpcProvider} contract. The LoveEssential provider is resolved
 * on every use, never cached, because it registers late. If it is missing, one warning naming it is
 * logged and only the NPC feature (the Herald binding) stops working. 2026-11-21: the provider switch
 * was removed; a leftover {@code integrations.npc-provider} value is ignored and reported once.
 */
public final class NpcIntegration {

    /** Identification of an NPC for binding: provider key and display name. */
    public record NpcRef(String key, String name) {}

    private final java.util.function.Supplier<String> leftoverProvider;
    private final java.util.function.Function<String, Optional<NpcProvider>> resolver;
    private final java.util.function.Consumer<String> warn;
    private final java.util.function.BooleanSupplier warningsReady;
    private volatile boolean seenReady;
    private final Set<String> warned = new HashSet<>();

    public NpcIntegration(LoveTweaks plugin) {
        this(() -> plugin.getConfig().getString(NpcBinding.LEGACY_PROVIDER_PATH),
                id -> LoveCore.provider(NpcProvider.class, id), plugin.getLogger()::warning, startupGrace());
    }

    /** @param leftoverProvider supplies the raw value of the removed {@code integrations.npc-provider} key, or null */
    public NpcIntegration(java.util.function.Supplier<String> leftoverProvider,
                          java.util.function.Function<String, Optional<NpcProvider>> resolver,
                          java.util.function.Consumer<String> warn,
                          java.util.function.BooleanSupplier warningsReady) {
        this.leftoverProvider = leftoverProvider;
        this.resolver = resolver;
        this.warn = warn;
        this.warningsReady = warningsReady;
    }

    private static java.util.function.BooleanSupplier startupGrace() {
        int started = org.bukkit.Bukkit.getServer() == null ? 0 : org.bukkit.Bukkit.getCurrentTick();
        return () -> org.bukkit.Bukkit.getServer() == null || org.bukkit.Bukkit.getCurrentTick() - started >= 200;
    }

    public String providerId() {
        return NpcBinding.PROVIDER_ID;
    }

    /** Forgets which missing providers were already reported (after a config reload). */
    public synchronized void resetWarnings() {
        warned.clear();
    }

    /** Resolves the LoveEssential provider now; warns once when it is unavailable. */
    public Optional<NpcProvider> provider() {
        warnAboutLeftoverProvider();
        String id = providerId();
        Optional<NpcProvider> found = Optional.empty();
        try {
            found = resolver.apply(id);
        } catch (LinkageError | RuntimeException exception) {
            // LoveCore absent or incompatible: treated the same as a missing provider.
        }
        if (found.isEmpty()) {
            if (seenReady || warningsReady.getAsBoolean()) warnMissing(id);
        } else {
            seenReady = true;
            synchronized (this) { warned.remove(id); }
        }
        return found;
    }

    private void warnAboutLeftoverProvider() {
        String ignored = NpcBinding.ignoredProviderValue(leftoverProvider.get());
        if (ignored == null) return;
        synchronized (this) {
            if (!warned.add("leftover:" + ignored)) return;
        }
        warn.accept(NpcBinding.LEGACY_PROVIDER_PATH + ": значение '" + ignored + "' игнорируется, NPC берутся только из "
                + "LoveEssential; удалите ключ из config.yml.");
    }

    private void warnMissing(String id) {
        synchronized (this) {
            if (!warned.add(id)) {
                return;
            }
        }
        warn.accept("NPC-провайдер '" + id + "' недоступен: он не зарегистрирован в LoveCore "
                + "(установлен ли LoveEssential?). Привязка NPC Глашатая не работает, пока провайдер не появится.");
    }

    /** True if the entity is the NPC bound under {@code boundKey} in LoveEssential. */
    public boolean isBound(String boundKey, Entity entity) {
        if (boundKey == null || boundKey.isEmpty() || entity == null) {
            return false;
        }
        return provider()
                .flatMap(p -> p.byEntity(entity))
                .map(handle -> matches(boundKey, handle))
                .orElse(false);
    }

    /** Provider id and key comparison for an NPC coming from an event (events fire for every provider). */
    public boolean matches(String boundKey, NpcHandle handle) {
        return handle != null && NpcBinding.matches(boundKey, handle.providerId(), handle.key());
    }

    /** The NPC an admin is targeting (the provider's own notion: selected NPC or look direction). */
    public Optional<NpcRef> targeted(Player player) {
        return provider()
                .flatMap(p -> p.targeted(player))
                .map(handle -> new NpcRef(handle.key(), handle.name() == null ? "" : handle.name()));
    }

    /** Startup diagnostic: reports a leftover {@code integrations.npc-provider} value that is being ignored. */
    public void checkBinding(String boundKey) {
        warnAboutLeftoverProvider();
    }
}

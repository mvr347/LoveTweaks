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
 * NPC access through LoveCore's {@link NpcProvider} contract. The provider named in
 * {@code integrations.npc-provider} is resolved on every use, never cached, because providers
 * register late (LoveEssential, Citizens adapter). If it is missing, one warning naming it is
 * logged and only the NPC feature (the Herald binding) stops working.
 */
public final class NpcIntegration {

    /** Identification of an NPC for binding: provider key and display name. */
    public record NpcRef(String key, String name) {}

    private final java.util.function.Supplier<String> configured;
    private final java.util.function.Function<String, Optional<NpcProvider>> resolver;
    private final java.util.function.Consumer<String> warn;
    private final java.util.function.BooleanSupplier warningsReady;
    private volatile boolean seenReady;
    private final Set<String> warned = new HashSet<>();

    public NpcIntegration(LoveTweaks plugin) {
        this(() -> NpcBinding.providerFrom(plugin.getConfig()),
                id -> LoveCore.provider(NpcProvider.class, id), plugin.getLogger()::warning, startupGrace());
    }

    public NpcIntegration(java.util.function.Supplier<String> configured,
                          java.util.function.Function<String, Optional<NpcProvider>> resolver,
                          java.util.function.Consumer<String> warn,
                          java.util.function.BooleanSupplier warningsReady) {
        this.configured = configured;
        this.resolver = resolver;
        this.warn = warn;
        this.warningsReady = warningsReady;
    }

    private static java.util.function.BooleanSupplier startupGrace() {
        int started = org.bukkit.Bukkit.getServer() == null ? 0 : org.bukkit.Bukkit.getCurrentTick();
        return () -> org.bukkit.Bukkit.getServer() == null || org.bukkit.Bukkit.getCurrentTick() - started >= 200;
    }

    public String providerId() {
        return configured.get();
    }

    /** Forgets which missing providers were already reported (after a config reload). */
    public synchronized void resetWarnings() {
        warned.clear();
    }

    /** Resolves the configured provider now; warns once per provider id when it is unavailable. */
    public Optional<NpcProvider> provider() {
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

    private void warnMissing(String id) {
        synchronized (this) {
            if (!warned.add(id)) {
                return;
            }
        }
        warn.accept("NPC-провайдер '" + id + "' (integrations.npc-provider) недоступен: "
                + "он не зарегистрирован в LoveCore. Привязка NPC Глашатая не работает, пока провайдер не появится.");
    }

    /** True if the entity is the NPC bound under {@code boundKey} in the configured provider. */
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
        return handle != null && NpcBinding.matches(providerId(), boundKey, handle.providerId(), handle.key());
    }

    /** The NPC an admin is targeting (the provider's own notion: selected NPC or look direction). */
    public Optional<NpcRef> targeted(Player player) {
        return provider()
                .flatMap(p -> p.targeted(player))
                .map(handle -> new NpcRef(handle.key(), handle.name() == null ? "" : handle.name()));
    }

    /** One-time diagnostic: warns when the stored key obviously belongs to another provider. */
    public void checkBinding(String boundKey) {
        if (NpcBinding.keyLooksForeign(providerId(), boundKey)) {
            warn.accept("herald.npc-key '" + boundKey + "' не похож на ключ NPC провайдера '"
                    + providerId() + "'. После смены integrations.npc-provider привяжите NPC заново: "
                    + "/lovetweaksadmin herald bind.");
        }
    }
}

package me.lovelace.loveTweaks.integration;

import static org.junit.jupiter.api.Assertions.*;

import dev.lovelace.lovecore.api.npc.NpcProvider;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Optional;

class NpcIntegrationTest {
    @Test
    void lateProviderRecoversAndSubsequentLossIsReportedOnlyOnce() {
        boolean[] ready = {false}, grace = {false};
        var warnings = new ArrayList<String>();
        var provider =
                (NpcProvider)
                        Proxy.newProxyInstance(
                                NpcProvider.class.getClassLoader(),
                                new Class<?>[] {NpcProvider.class},
                                (p, m, a) -> null);
        var integration =
                new NpcIntegration(
                        () -> null,
                        id -> ready[0] ? Optional.of(provider) : Optional.empty(),
                        warnings::add,
                        () -> grace[0]);
        assertTrue(integration.provider().isEmpty());
        assertTrue(warnings.isEmpty());
        grace[0] = true;
        integration.provider();
        integration.provider();
        assertEquals(1, warnings.size());
        ready[0] = true;
        assertSame(provider, integration.provider().orElseThrow());
        ready[0] = false;
        integration.provider();
        integration.provider();
        assertEquals(2, warnings.size());
    }

    @Test
    void leftoverProviderValueIsIgnoredWithOneWarning() {
        var warnings = new ArrayList<String>();
        var provider = (NpcProvider) Proxy.newProxyInstance(NpcProvider.class.getClassLoader(),
                new Class<?>[] {NpcProvider.class}, (p, m, a) -> null);
        var asked = new ArrayList<String>();
        var integration = new NpcIntegration(() -> "citizens", id -> { asked.add(id); return Optional.of(provider); },
                warnings::add, () -> true);
        assertSame(provider, integration.provider().orElseThrow());
        integration.provider();
        assertEquals(java.util.List.of("loveessential", "loveessential"), asked);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("citizens"), warnings.get(0));
    }
}

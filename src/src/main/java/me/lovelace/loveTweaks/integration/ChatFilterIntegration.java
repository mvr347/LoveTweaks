package me.lovelace.loveTweaks.integration;

import org.bukkit.Bukkit;

/**
 * Pure-reflection bridge into LoveChatFilter's optional {@code LoveChatFilterAPI} service (no
 * compile-time dependency), mirroring {@link CitizensIntegration}: if LoveChatFilter isn't
 * installed, isn't enabled, or hasn't registered the service yet, {@link #isProfane} just
 * returns {@code false} rather than failing — the Herald keeps working, minus the check.
 */
public final class ChatFilterIntegration {

    private static final String API_CLASS = "me.lovelace.lovechatfilter.api.LoveChatFilterAPI";

    public boolean isProfane(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Object service = Bukkit.getServicesManager().load(apiClass);
            if (service == null) {
                return false;
            }
            Object result = apiClass.getMethod("isProfane", String.class).invoke(service, text);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
}

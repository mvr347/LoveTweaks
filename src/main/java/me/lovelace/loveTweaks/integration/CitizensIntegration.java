package me.lovelace.loveTweaks.integration;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.lang.reflect.Method;

/**
 * Pure-reflection bridge into Citizens (no compile-time dependency), mirroring
 * LoveHunt's CitizensIntegration, so the plugin loads fine whether or not Citizens is present.
 */
public final class CitizensIntegration {
    private final Plugin citizensPlugin;

    public CitizensIntegration() {
        this.citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
    }

    public boolean isAvailable() {
        return citizensPlugin != null && citizensPlugin.isEnabled();
    }

    public boolean isNpc(Entity entity) {
        return npcOf(entity) != null;
    }

    public Integer npcId(Entity entity) {
        Object npc = npcOf(entity);
        if (npc == null) {
            return null;
        }
        Object id = invoke(npc, "getId");
        return id instanceof Integer value ? value : null;
    }

    public String npcName(Entity entity) {
        Object npc = npcOf(entity);
        if (npc == null) {
            return null;
        }
        Object name = invoke(npc, "getName");
        return name == null ? null : name.toString();
    }

    /** Идентификация NPC для привязки — id и имя уже извлечены из хэндла Citizens. */
    public record NpcRef(int id, String name) {}

    /**
     * NPC для админской привязки. Сначала берём выделенный NPC: Citizens выделяет его сам
     * после /npc create и /npc select, и все его собственные команды работают именно так.
     * Если выделения нет — падаем на луч взгляда.
     * <p>
     * Одного луча не хватало: сразу после /npc create NPC стоит внутри игрока, и луч из
     * глаз вперёд по нему не попадает, поэтому привязка сразу после создания не работала.
     */
    public NpcRef bindTarget(Player player, double distance) {
        if (!isAvailable() || player == null) {
            return null;
        }
        Object npc = selectedNpc(player);
        if (npc == null) {
            Entity looked = lookedAtNpc(player, distance);
            npc = looked == null ? null : npcOf(looked);
        }
        if (npc == null) {
            return null;
        }
        Object id = invoke(npc, "getId");
        if (!(id instanceof Integer idValue)) {
            return null;
        }
        Object name = invoke(npc, "getName");
        return new NpcRef(idValue, name == null ? "" : name.toString());
    }

    private Object selectedNpc(Player player) {
        try {
            Class<?> apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object selector = apiClass.getMethod("getDefaultNPCSelector").invoke(null);
            if (selector == null) {
                return null;
            }
            return selector.getClass().getMethod("getSelected", CommandSender.class).invoke(selector, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    /**
     * Resolves the NPC the player is currently looking at. Used for admin NPC binding instead of
     * Citizens' own selector API, so we don't have to reflect into that API surface too.
     */
    public Entity lookedAtNpc(Player player, double distance) {
        if (!isAvailable() || player == null) {
            return null;
        }
        // rayTraceEntities принимает int, поэтому дробную дистанцию округляем вверх - без
        // явного приведения класс не компилируется (lossy conversion from double to int).
        RayTraceResult trace = player.rayTraceEntities((int) Math.ceil(distance));
        if (trace == null) {
            return null;
        }
        Entity hit = trace.getHitEntity();
        return hit != null && isNpc(hit) ? hit : null;
    }

    private Object npcOf(Entity entity) {
        if (!isAvailable() || entity == null) {
            return null;
        }
        try {
            Class<?> apiClass = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = apiClass.getMethod("getNPCRegistry").invoke(null);
            Object isNpc = registry.getClass().getMethod("isNPC", Entity.class).invoke(registry, entity);
            if (!(isNpc instanceof Boolean bool) || !bool) {
                return null;
            }
            return registry.getClass().getMethod("getNPC", Entity.class).invoke(registry, entity);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}

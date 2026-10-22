package me.lovelace.loveTweaks.listeners;

import dev.lovelace.lovecore.api.npc.NpcClick;
import dev.lovelace.lovecore.api.npc.NpcClickEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.herald.HeraldGUI;
import me.lovelace.loveTweaks.herald.HeraldManager;
import me.lovelace.loveTweaks.integration.NpcIntegration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Herald NPC interaction through LoveCore's NPC contract. Kept apart from {@link HeraldListener}
 * so that a missing LoveCore API only disables the NPC click, never the rest of the Herald.
 */
public final class HeraldNpcListener implements Listener {

    private final LoveTweaks plugin;
    private final HeraldManager manager;
    private final NpcIntegration npcs;

    public HeraldNpcListener(LoveTweaks plugin, HeraldManager manager, NpcIntegration npcs) {
        this.plugin = plugin;
        this.manager = manager;
        this.npcs = npcs;
    }

    /** NpcClickEvent fires for every provider; {@link NpcIntegration#matches} drops foreign ones. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onNpcClick(NpcClickEvent event) {
        if (!plugin.getLoveTweaksConfig().isHeraldEnabled()) {
            return;
        }
        String boundKey = plugin.getLoveTweaksConfig().getHeraldNpcKey();
        if (boundKey.isEmpty() || !npcs.matches(boundKey, event.npc())) {
            return;
        }
        event.setCancelled(true);
        if (event.click() == NpcClick.RIGHT) {
            HeraldGUI.open(event.getPlayer(), manager, plugin);
        }
    }

    /** Guards the NPC from damage so it cannot be attacked instead of opening the menu. */
    @EventHandler(ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getLoveTweaksConfig().isHeraldEnabled()) {
            return;
        }
        String boundKey = plugin.getLoveTweaksConfig().getHeraldNpcKey();
        if (boundKey.isEmpty()) {
            return;
        }
        if (npcs.isBound(boundKey, event.getEntity())) {
            event.setCancelled(true);
        }
    }
}

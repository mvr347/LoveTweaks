package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AutoRespawnListener implements Listener {

    private final LoveTweaks plugin;

    public AutoRespawnListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR: должны увидеть смерть последними. Другие плагины, управляющие смертью
     * (реанимация/keep-inventory/death-chest — ReviveMe, CMI и т.п.), нередко сами планируют
     * свою пост-смертную обработку (выдачу дропа, снятие "даунстейта") через
     * {@code runTaskLater(..., 1L)}. Если наш форс-респавн выполнится раньше их задачи на том
     * же тике, `player.isDead()` станет false до того, как они это ожидают, и их часть работы
     * (в т.ч. реальная выдача предметов на землю) тихо не выполнится — баг 2026-09:
     * "после смерти предметы не выпадают на пол". Задержка теперь настраиваемая и по
     * умолчанию больше одного тика именно для того, чтобы такие чужие задачи гарантированно
     * успели отработать первыми (Bukkit исполняет более ранний тик целиком, прежде чем
     * наступит более поздний — так что запас в тиках, а не приоритет, даёт детерминированный
     * порядок между задачами РАЗНЫХ плагинов).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getLoveTweaksConfig().isAutoRespawnEnabled()) {
            return;
        }

        Player player = event.getEntity();
        long delay = plugin.getLoveTweaksConfig().getAutoRespawnDelayTicks();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getLoveTweaksConfig().isAutoRespawnEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isDead()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            }, 1L);
        }
    }
}

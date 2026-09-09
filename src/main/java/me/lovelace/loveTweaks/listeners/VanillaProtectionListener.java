package me.lovelace.loveTweaks.listeners;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Iterator;

/**
 * Обработчик отключения ванильных ограничений:
 * 1. Ванильные достижения (Advancements) — полное отключение критериев, аннулирование прогресса и скрытие анонсов.
 * 2. Ванильные жалобы (Chat Reporting) — лишение сообщений криптографических подписей.
 * 3. Ванильные команды жалоб, друзей и достижений.
 */
public class VanillaProtectionListener implements Listener {

    private final LoveTweaks plugin;

    public VanillaProtectionListener(LoveTweaks plugin) {
        this.plugin = plugin;
        applyGameRules();
    }

    /**
     * Применяет настройки игровых правил ко всем загруженным мирам.
     */
    public void applyGameRules() {
        if (!plugin.getLoveTweaksConfig().isDisableAdvancements()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            setAdvancementGameRule(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAdvancements()) {
            setAdvancementGameRule(event.getWorld());
        }
    }

    @SuppressWarnings("deprecation")
    private void setAdvancementGameRule(World world) {
        try {
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        } catch (Throwable ignored) {}
    }

    /**
     * Блокирует получение любых критериев достижений.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAdvancementGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAdvancements()) {
            event.setCancelled(true);
        }
    }

    /**
     * Предотвращает оповещения о выполнении достижений.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableAdvancements()) {
            event.message(null);
        }
    }

    /**
     * Сбрасывает любые сохраненные достижения при входе игрока.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableAdvancements()) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Iterator<Advancement> it = Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement adv = it.next();
                AdvancementProgress progress = player.getAdvancementProgress(adv);
                if (!progress.getAwardedCriteria().isEmpty()) {
                    for (String criterion : progress.getAwardedCriteria()) {
                        progress.revokeCriteria(criterion);
                    }
                }
            }
        });
    }

    /**
     * Предотвращает возможность жалоб на сообщения (No Chat Reports).
     * Отменяет подписанное событие и рассылает его как неподписанное системное сообщение всем получателям.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableChatReports()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Component rendered = event.renderer().render(player, player.displayName(), event.message(), player);

        for (Audience viewer : event.viewers()) {
            viewer.sendMessage(rendered);
        }
    }

    /**
     * Блокирует ванильные команды жалоб, достижений и друзей.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableFriendsAndReports()) {
            return;
        }

        String msg = event.getMessage().trim();
        if (msg.isEmpty()) {
            return;
        }
        if (msg.startsWith("/")) {
            msg = msg.substring(1).trim();
        }
        if (msg.isEmpty()) {
            return;
        }

        String[] parts = msg.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        for (String blocked : plugin.getLoveTweaksConfig().getBlockedVanillaCommands()) {
            if (cmd.equalsIgnoreCase(blocked)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getDisabledVanillaCommandMessage()));
                return;
            }
        }
    }
}

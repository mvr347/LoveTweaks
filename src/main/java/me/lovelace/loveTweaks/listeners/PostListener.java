package me.lovelace.loveTweaks.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.post.PostGUI;
import me.lovelace.loveTweaks.post.PostGUIHolder;
import me.lovelace.loveTweaks.post.PostManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public class PostListener implements Listener {

    private final LoveTweaks plugin;
    private final PostManager manager;
    private final CitizensIntegration citizens;

    public PostListener(LoveTweaks plugin, PostManager manager, CitizensIntegration citizens) {
        this.plugin = plugin;
        this.manager = manager;
        this.citizens = citizens;
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!plugin.getLoveTweaksConfig().isPostEnabled()) {
            return;
        }
        int boundNpcId = plugin.getLoveTweaksConfig().getPostNpcId();
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != boundNpcId) {
            return;
        }
        event.setCancelled(true);
        manager.openCompose(event.getPlayer());
    }

    /** Гвардируем NPC от урона, чтобы его нельзя было случайно атаковать вместо открытия меню. */
    @EventHandler(ignoreCancelled = true)
    public void onNpcDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getLoveTweaksConfig().isPostEnabled()) {
            return;
        }
        int boundNpcId = plugin.getLoveTweaksConfig().getPostNpcId();
        if (boundNpcId < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getEntity());
        if (npcId != null && npcId == boundNpcId) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PostGUIHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getInventory());

        if (clickedTop) {
            int slot = event.getSlot();
            boolean isDepositSlot = slot >= PostGUI.DEPOSIT_START && slot <= PostGUI.DEPOSIT_END;
            if (!isDepositSlot) {
                event.setCancelled(true);
                if (slot == PostGUI.SLOT_RECIPIENT) {
                    manager.requestRecipientInput(player, holder);
                } else if (slot == PostGUI.SLOT_CANCEL) {
                    player.closeInventory();
                } else if (slot == PostGUI.SLOT_SEND) {
                    manager.trySend(player, holder);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PostGUIHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            manager.cancelCompose(player, holder);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!manager.isAwaitingRecipientInput(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        final String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.handleChatInput(player, message));
    }

    /** Разрешаем стрелам сбивать голубя, но защищаем его от прочего урона (падение, огонь и т.д.). */
    @EventHandler(ignoreCancelled = true)
    public void onPigeonDamage(EntityDamageEvent event) {
        if (!manager.isPigeon(event.getEntity().getUniqueId())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPigeonDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!manager.isPigeon(entity.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        String shooterName = "неизвестный стрелок";
        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                shooterName = player.getName();
            }
        }
        manager.handleShotDown(entity.getUniqueId(), entity.getLocation(), shooterName);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.trackLocation(event.getPlayer());
        manager.deliverPendingMail(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.trackLocation(event.getPlayer());
    }
}

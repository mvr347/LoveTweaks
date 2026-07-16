package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.scoreboard.PlayerScoreboardState;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDataManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardGUI;
import me.lovelace.loveTweaks.scoreboard.ScoreboardGUIHolder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class ScoreboardListener implements Listener, CommandExecutor {

    private final LoveTweaks plugin;
    private final ScoreboardDataManager dataManager;
    private final ScoreboardDisplayManager displayManager;

    public ScoreboardListener(LoveTweaks plugin, ScoreboardDataManager dataManager,
                              ScoreboardDisplayManager displayManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.displayManager = displayManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам.");
            return true;
        }
        PlayerScoreboardState state = dataManager.getState(player.getUniqueId());
        ScoreboardGUI.open(player, state, plugin.getScoreboardConfig());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Small delay to ensure world is loaded
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> displayManager.updateScoreboard(player), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        displayManager.removeScoreboard(player);
        dataManager.unload(player.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        displayManager.updateScoreboard(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ScoreboardGUIHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) return;

        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        ScoreboardConfig cfg = plugin.getScoreboardConfig();
        PlayerScoreboardState state = dataManager.getState(player.getUniqueId());

        if (slot == ScoreboardGUI.SLOT_TOGGLE) {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                state.setScoreboardEnabled(!state.isScoreboardEnabled());
                displayManager.updateScoreboard(player);
                ScoreboardGUI.refresh(event.getInventory(), holder, player, state, cfg);
            }
            return;
        }

        if (slot == ScoreboardGUI.SLOT_BACK) {
            player.closeInventory();
            String cmd = cfg.getBackCommand();
            if (cmd != null && !cmd.isBlank()) {
                String cleaned = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                player.performCommand(cleaned);
            }
            return;
        }

        if (slot == ScoreboardGUI.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        String placeholderId = holder.getSectionAt(slot);
        if (placeholderId == null) return;

        if (click == ClickType.MIDDLE) {
            state.togglePlaceholder(placeholderId, cfg.getMaxPlaceholders());
        } else if (click == ClickType.LEFT) {
            if (state.hasPlaceholderActive(placeholderId)) state.movePlaceholderUp(placeholderId);
        } else if (click == ClickType.RIGHT) {
            if (state.hasPlaceholderActive(placeholderId)) state.movePlaceholderDown(placeholderId);
        } else {
            return;
        }

        displayManager.updateScoreboard(player);
        ScoreboardGUI.refresh(event.getInventory(), holder, player, state, cfg);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ScoreboardGUIHolder holder)) return;
        dataManager.savePlayer(holder.getPlayerUuid());
    }
}

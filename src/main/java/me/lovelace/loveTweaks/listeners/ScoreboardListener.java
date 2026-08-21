package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.scoreboard.PlayerScoreboardState;
import me.lovelace.loveTweaks.scoreboard.ScoreboardConfig;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDataManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardDisplayManager;
import me.lovelace.loveTweaks.scoreboard.ScoreboardGUI;
import me.lovelace.loveTweaks.scoreboard.ScoreboardGUIHolder;
import org.bukkit.Sound;
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
            sender.sendMessage(plugin.getLoveTweaksConfig().message("players-only").replace('&', '§'));
            return true;
        }
        PlayerScoreboardState state = dataManager.getState(player.getUniqueId());
        ScoreboardGUI.open(player, state, plugin.getScoreboardConfig());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isAuthenticated(player)) return; // dodges LoveAuth's limbo - onAuthenticated shows it instead
        dataManager.loadPlayerAsync(player.getUniqueId()).thenAccept(state -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    displayManager.updateScoreboard(player);
                }
            });
        });
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        Player player = event.player();
        dataManager.loadPlayerAsync(player.getUniqueId()).thenAccept(state -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    displayManager.updateScoreboard(player);
                }
            });
        });
    }

    /**
     * Не кэшируем Optional<AuthOracle> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore. Если LoveAuth не установлен, скорборд
     * показывается сразу на join, как и раньше.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
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
            if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
            if (state.getActivePlaceholders().isEmpty()) {
                playFeedback(player, false);
                return;
            }
            state.setScoreboardEnabled(!state.isScoreboardEnabled());
            playFeedback(player, true);
            displayManager.updateScoreboard(player);
            ScoreboardGUI.refresh(event.getInventory(), holder, player, state, cfg);
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
            boolean wasActive = state.hasPlaceholderActive(placeholderId);
            state.togglePlaceholder(placeholderId, cfg.getMaxPlaceholders());
            boolean changed = state.hasPlaceholderActive(placeholderId) != wasActive;
            playFeedback(player, changed);
            if (!changed) return; // was already at max-placeholders, nothing to refresh
        } else if (click == ClickType.LEFT) {
            if (!state.hasPlaceholderActive(placeholderId)) return;
            boolean moved = state.movePlaceholderUp(placeholderId);
            playFeedback(player, moved);
            if (!moved) return; // already first in the scoreboard order
        } else if (click == ClickType.RIGHT) {
            if (!state.hasPlaceholderActive(placeholderId)) return;
            boolean moved = state.movePlaceholderDown(placeholderId);
            playFeedback(player, moved);
            if (!moved) return; // already last in the scoreboard order
        } else {
            return;
        }

        displayManager.updateScoreboard(player);
        ScoreboardGUI.refresh(event.getInventory(), holder, player, state, cfg);
    }

    /** Every click needs an audible response — a silent no-op reads as a broken button. */
    private void playFeedback(Player player, boolean changed) {
        if (changed) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ScoreboardGUIHolder holder)) return;
        dataManager.savePlayer(holder.getPlayerUuid());
    }
}

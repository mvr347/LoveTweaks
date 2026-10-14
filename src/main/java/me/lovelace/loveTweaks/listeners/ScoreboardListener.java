package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.scoreboard.PlayerScoreboardState;
import me.lovelace.loveTweaks.scoreboard.PresetApplier;
import me.lovelace.loveTweaks.scoreboard.ScoreboardAvailability;
import me.lovelace.loveTweaks.scoreboard.ScoreboardEntry;
import me.lovelace.loveTweaks.scoreboard.ScoreboardLayout;
import me.lovelace.loveTweaks.scoreboard.ScoreboardPlaceholder;
import me.lovelace.loveTweaks.scoreboard.ScoreboardPreset;
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
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ScoreboardGUIHolder) {
            event.setCancelled(true);
        }
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
        Inventory inv = event.getInventory();
        boolean category = holder.getType() == ScoreboardGUIHolder.Type.CATEGORY;
        int size = inv.getSize();

        // Footer: close is always there; back leads to the main menu from a submenu, and to the
        // configured command from the main menu.
        if (slot == ScoreboardLayout.closeSlot(size)) {
            player.closeInventory();
            return;
        }
        if (slot == ScoreboardLayout.backSlot(size)) {
            if (category) {
                dataManager.savePlayer(player.getUniqueId());
                ScoreboardGUI.open(player, state, cfg);
            } else {
                player.closeInventory();
                String cmd = cfg.getBackCommand();
                if (cmd != null && !cmd.isBlank()) {
                    player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                }
            }
            return;
        }
        if (!category && slot == ScoreboardGUI.SLOT_TOGGLE) {
            if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
            if (state.getActivePlaceholders().isEmpty()) {
                playFeedback(player, false);
                return;
            }
            state.setScoreboardEnabled(!state.isScoreboardEnabled());
            playFeedback(player, true);
            displayManager.updateScoreboard(player);
            ScoreboardGUI.refresh(inv, holder, player, state, cfg);
            return;
        }

        ScoreboardGUIHolder.Action action = holder.getAction(slot);
        if (action == null) return;

        boolean changed;
        switch (action.kind()) {
            case VIEW_TOGGLE -> {
                if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
                state.setViewAll(!state.isViewAll());
                holder.setPage(0);
                playFeedback(player, true);
                ScoreboardGUI.refresh(inv, holder, player, state, cfg);
                return;
            }
            case PAGE_PREV, PAGE_NEXT -> {
                if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
                holder.setPage(holder.getPage() + (action.kind() == ScoreboardGUIHolder.Kind.PAGE_NEXT ? 1 : -1));
                playFeedback(player, true);
                ScoreboardGUI.refresh(inv, holder, player, state, cfg);
                return;
            }
            case CATEGORY -> {
                if (click != ClickType.LEFT) return;
                playFeedback(player, true);
                ScoreboardGUI.openCategory(player, action.ref(), state, cfg);
                return;
            }
            case PRESET -> {
                if (click != ClickType.LEFT) return;
                ScoreboardPreset preset = cfg.getPresets().get(action.ref());
                if (preset == null) return;
                List<ScoreboardEntry> resolved = ScoreboardGUI.resolvePreset(preset, player, cfg);
                changed = !resolved.isEmpty() && !PresetApplier.sameSet(state.entries(), resolved);
                if (changed) state.replaceWith(resolved);
            }
            case PLACEHOLDER -> {
                ScoreboardPlaceholder ph = cfg.getPlaceholder(action.ref());
                if (ph == null) return;
                changed = category ? handleSubmenuClick(state, ph, click, player, cfg)
                        : state.isViewAll() ? handleFlatClick(state, ph, click, player, cfg)
                        : handleCardClick(state, ph, click, cfg);
            }
            default -> {
                return;
            }
        }

        playFeedback(player, changed);
        if (!changed) return;
        displayManager.updateScoreboard(player);
        ScoreboardGUI.refresh(inv, holder, player, state, cfg);
    }

    /** Slot card (view "По категориям"): LMB up, RMB down, MMB / Shift+RMB remove, Shift+LMB format. */
    private boolean handleCardClick(PlayerScoreboardState state, ScoreboardPlaceholder ph, ClickType click,
                                    ScoreboardConfig cfg) {
        String id = ph.id();
        return switch (click) {
            case LEFT -> state.movePlaceholderUp(id);
            case RIGHT -> state.movePlaceholderDown(id);
            case MIDDLE, SHIFT_RIGHT -> state.disable(id);
            case SHIFT_LEFT -> cycleVariant(state, ph);
            default -> false;
        };
    }

    /** Flat list (view "Все"): MMB / Shift+RMB toggle, LMB up, RMB down, Shift+LMB format. */
    private boolean handleFlatClick(PlayerScoreboardState state, ScoreboardPlaceholder ph, ClickType click,
                                    Player player, ScoreboardConfig cfg) {
        String id = ph.id();
        return switch (click) {
            case MIDDLE, SHIFT_RIGHT -> state.hasPlaceholderActive(id)
                    ? state.disable(id)
                    : enableIfPossible(state, ph, player, cfg);
            case LEFT -> state.movePlaceholderUp(id);
            case RIGHT -> state.movePlaceholderDown(id);
            case SHIFT_LEFT -> cycleVariant(state, ph);
            default -> false;
        };
    }

    /** Category submenu: LMB enables / disables, Shift+LMB changes the format of an enabled line. */
    private boolean handleSubmenuClick(PlayerScoreboardState state, ScoreboardPlaceholder ph, ClickType click,
                                       Player player, ScoreboardConfig cfg) {
        String id = ph.id();
        return switch (click) {
            case LEFT -> state.hasPlaceholderActive(id)
                    ? state.disable(id)
                    : enableIfPossible(state, ph, player, cfg);
            case SHIFT_LEFT -> cycleVariant(state, ph);
            default -> false;
        };
    }

    private boolean enableIfPossible(PlayerScoreboardState state, ScoreboardPlaceholder ph, Player player,
                                     ScoreboardConfig cfg) {
        if (!ScoreboardAvailability.isAvailable(player, ph)) return false;
        return state.enable(ph.id(), cfg.getMaxPlaceholders());
    }

    private boolean cycleVariant(PlayerScoreboardState state, ScoreboardPlaceholder ph) {
        if (ph.variantCount() <= 1 || !state.hasPlaceholderActive(ph.id())) return false;
        state.cycleVariant(ph.id(), ph.variantCount());
        return true;
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

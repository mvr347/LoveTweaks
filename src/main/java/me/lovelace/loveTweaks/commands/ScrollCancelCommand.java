package me.lovelace.loveTweaks.commands;

import me.lovelace.loveTweaks.managers.CoordinateTeleportScrollManager;
import me.lovelace.loveTweaks.managers.TeleportScrollManager;
import me.lovelace.loveTweaks.utils.Notify;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /scrollcancel: target of the chat button shown during a scroll countdown. */
public final class ScrollCancelCommand implements CommandExecutor {

    private final TeleportScrollManager scroll;
    private final CoordinateTeleportScrollManager coords;

    public ScrollCancelCommand(TeleportScrollManager scroll, CoordinateTeleportScrollManager coords) {
        this.scroll = scroll;
        this.coords = coords;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        boolean done = scroll.cancelByPlayer(player) | coords.cancelByPlayer(player);
        if (!done) {
            Notify.event(player, Component.text("Нет активной телепортации.", NamedTextColor.GRAY));
        }
        return true;
    }
}

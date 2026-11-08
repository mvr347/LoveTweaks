package me.lovelace.loveTweaks.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Clickable "cancel teleport" line sent to chat while a scroll countdown is running. */
public final class ScrollCancelButton {

    public static final String COMMAND = "scrollcancel";

    private ScrollCancelButton() {}

    public static void send(Player player) {
        Component button = Component.text("[Отменить перемещение]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/" + COMMAND))
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы отменить телепортацию", NamedTextColor.GRAY)));
        player.sendMessage(button);
    }
}

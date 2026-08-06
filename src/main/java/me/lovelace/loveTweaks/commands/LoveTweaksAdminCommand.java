package me.lovelace.loveTweaks.commands;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.items.TeleportScroll;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Единая административная команда плагина: {@code /lovetweaksadmin <subcommand>}.
 * <p>
 * Раньше все эти же подкоманды (reload, givescroll, givecoordscroll, herald) жили прямо в
 * {@code onCommand} главного класса плагина под именем {@code /lovetweaks}. Само по себе имя
 * не соответствовало принятому в экосистеме Love* стилю {@code love<плагин>admin} — при том что
 * команда и так была полностью административной. {@code /lovetweaks} оставлен в plugin.yml как
 * алиас {@code /lovetweaksadmin}, поэтому старая привычка админов набирать именно его продолжает
 * работать без изменений.
 */
public class LoveTweaksAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "givescroll", "givecoordscroll", "herald", "help");
    private static final List<String> HERALD_SUBCOMMANDS = List.of("bind", "unbind", "clear");

    private final LoveTweaks plugin;

    public LoveTweaksAdminCommand(@NotNull LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Команда в plugin.yml уже объявлена с permission: lovetweaks.admin, так что Bukkit
        // отсеет посторонних раньше, чем сюда дойдёт вызов — проверка здесь только для
        // единообразия с остальными Love*-плагинами и на случай прямого вызова executor'а.
        if (!sender.hasPermission("lovetweaks.admin")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "givescroll" -> handleGiveScroll(sender, args);
            case "givecoordscroll" -> handleGiveCoordScroll(sender, args);
            case "herald" -> handleHerald(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(@NotNull CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(msg("reload-done"));
    }

    private void handleGiveScroll(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("usage-givescroll"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "<player>", args[1]));
            return;
        }
        target.getInventory().addItem(TeleportScroll.create());
        sender.sendMessage(msg("scroll-given-sender", "<player>", target.getName()));
        target.sendMessage(msg("scroll-given-target"));
    }

    private void handleGiveCoordScroll(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("usage-givecoordscroll"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "<player>", args[1]));
            return;
        }
        String scrollId = args[2];
        ItemStack item = CoordinateTeleportScroll.create(scrollId);
        if (item == null) {
            sender.sendMessage(msg("coord-scroll-not-found", "<id>", scrollId));
            return;
        }
        target.getInventory().addItem(item);
        sender.sendMessage(msg("scroll-given-sender", "<player>", target.getName()));
        target.sendMessage(msg("scroll-given-target"));
    }

    private void handleHerald(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("usage-herald"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "bind" -> handleHeraldBind(sender);
            case "unbind" -> {
                plugin.getLoveTweaksConfig().setHeraldNpc(-1, "");
                sender.sendMessage(msg("herald-npc-unbound"));
            }
            case "clear" -> {
                plugin.getHeraldManager().clear();
                sender.sendMessage(msg("herald-cleared"));
            }
            default -> sender.sendMessage(msg("usage-herald"));
        }
    }

    private void handleHeraldBind(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("players-only"));
            return;
        }
        CitizensIntegration citizens = plugin.getCitizensIntegration();
        if (!citizens.isAvailable()) {
            sender.sendMessage(msg("citizens-missing"));
            return;
        }
        CitizensIntegration.NpcRef npc = citizens.bindTarget(player, 6.0);
        if (npc == null) {
            sender.sendMessage(msg("npc-not-selected"));
            return;
        }
        plugin.getLoveTweaksConfig().setHeraldNpc(npc.id(), npc.name());
        sender.sendMessage(msg("herald-npc-bound", "<npc>", npc.name()));
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(msg("help-header"));
        sender.sendMessage(msg("help-reload"));
        sender.sendMessage(msg("help-givescroll"));
        sender.sendMessage(msg("help-givecoordscroll"));
        sender.sendMessage(msg("help-herald"));
        sender.sendMessage(msg("help-footer"));
    }

    private String msg(String key) {
        return plugin.getLoveTweaksConfig().message(key).replace('&', '§');
    }

    private String msg(String key, String placeholder, String value) {
        return plugin.getLoveTweaksConfig().message(key).replace(placeholder, value).replace('&', '§');
    }

    @Nullable
    @Override
    @SuppressWarnings("NullableProblems")
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lovetweaks.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("herald")) {
            return StringUtil.copyPartialMatches(args[1], HERALD_SUBCOMMANDS, new ArrayList<>());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("givescroll") || args[0].equalsIgnoreCase("givecoordscroll"))) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givecoordscroll")) {
            List<String> ids = new ArrayList<>(plugin.getLoveTweaksConfig().getCoordTeleportScrollConfig().getScrolls().keySet());
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}

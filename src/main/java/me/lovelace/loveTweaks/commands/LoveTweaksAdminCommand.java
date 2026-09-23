package me.lovelace.loveTweaks.commands;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.enchantments.CustomEnchantType;
import me.lovelace.loveTweaks.herald.HeraldGUI;
import me.lovelace.loveTweaks.integration.CitizensIntegration;
import me.lovelace.loveTweaks.items.CoordinateTeleportScroll;
import me.lovelace.loveTweaks.items.TeleportScroll;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
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

    private static final List<String> SUBCOMMANDS = List.of("reload", "givescroll", "givecoordscroll", "givepurification", "givebook", "herald", "help");
    private static final List<String> HERALD_SUBCOMMANDS = List.of("bind", "unbind", "clear", "open");

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
            case "givepurification", "givepurificationpotion" -> handleGivePurification(sender, args);
            case "givebook" -> handleGiveBook(sender, args);
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
        if (args.length < 2) {
            sender.sendMessage(msg("usage-givecoordscroll"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "<player>", args[1]));
            return;
        }
        target.getInventory().addItem(CoordinateTeleportScroll.create());
        sender.sendMessage(msg("scroll-given-sender", "<player>", target.getName()));
        target.sendMessage(msg("scroll-given-target"));
    }

    private void handleGivePurification(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("usage-givepurification"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "<player>", args[1]));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {}
        }
        ItemStack potion = me.lovelace.loveTweaks.items.PurificationPotion.create();
        potion.setAmount(amount);
        target.getInventory().addItem(potion);

        sender.sendMessage(msg("purification-given-sender", "<player>", target.getName()).replace("<amount>", String.valueOf(amount)));
        target.sendMessage(msg("purification-given-target"));
    }

    /**
     * Зачарование - либо кастомное (кинжалы/штаны, {@link CustomEnchantType}), либо ванильное
     * ({@link Enchantment} из {@link Registry#ENCHANTMENT}). {@code givebook} понимает только
     * кастомные ID - vanilla-ветка здесь нужна только чтобы отличить "это ванильное зачарование"
     * от "такого зачарования вообще не существует" и показать {@code givebook-custom-only}.
     */
    private record ResolvedEnchant(CustomEnchantType custom, Enchantment vanilla) {
        boolean isPresent() { return custom != null || vanilla != null; }

        int maxLevel() { return custom != null ? custom.getMaxLevel() : vanilla.getMaxLevel(); }

        String displayName() { return custom != null ? custom.getDisplayName() : vanillaDisplayName(vanilla); }
    }

    private ResolvedEnchant resolveEnchant(@NotNull String id) {
        CustomEnchantType custom = CustomEnchantType.fromId(id);
        if (custom != null) return new ResolvedEnchant(custom, null);
        Enchantment vanilla = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
        return new ResolvedEnchant(null, vanilla);
    }

    /** Только ID кастомных зачарований - для {@code givebook}, которая ванильные больше не выдаёт. */
    private List<String> customEnchantIds() {
        List<String> ids = new ArrayList<>();
        for (CustomEnchantType type : CustomEnchantType.values()) {
            ids.add(type.getId());
        }
        return ids;
    }

    private List<String> levelSuggestions(@NotNull String prefix, @NotNull ResolvedEnchant resolved) {
        if (!resolved.isPresent() || resolved.maxLevel() <= 1) {
            return List.of("1");
        }
        List<String> levels = new ArrayList<>();
        for (int i = 1; i <= resolved.maxLevel(); i++) {
            levels.add(String.valueOf(i));
        }
        return StringUtil.copyPartialMatches(prefix, levels, new ArrayList<>());
    }

    private static String vanillaDisplayName(@NotNull Enchantment enchantment) {
        String key = enchantment.getKey().getKey().replace('_', ' ');
        StringBuilder sb = new StringBuilder(key.length());
        boolean capitalizeNext = true;
        for (char c : key.toCharArray()) {
            sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
            capitalizeNext = c == ' ';
        }
        return sb.toString();
    }

    private void handleGiveBook(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("usage-givebook"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "<player>", args[1]));
            return;
        }
        ResolvedEnchant resolved = resolveEnchant(args[2]);
        if (!resolved.isPresent()) {
            sender.sendMessage(msg("enchant-not-found", "<enchant>", args[2]));
            return;
        }
        // givebook is custom-enchant-only: it exists to hand out books for this plugin's own
        // PDC-based enchants, which the client can't render on its own. A vanilla Enchantment
        // needs no help from us - vanilla /enchant already covers giving/applying it.
        if (resolved.custom() == null) {
            sender.sendMessage(msg("givebook-custom-only", "<enchant>", args[2]));
            return;
        }
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {}
        }
        level = Math.max(1, Math.min(level, resolved.maxLevel()));

        ItemStack book = plugin.getCustomEnchantManager().createEnchantedBook(resolved.custom(), level);
        target.getInventory().addItem(book);

        sender.sendMessage(msg("book-given-sender", "<player>", target.getName())
                .replace("<enchant>", resolved.displayName())
                .replace("<level>", CustomEnchantType.toRoman(level)));
        target.sendMessage(msg("book-given-target")
                .replace("<enchant>", resolved.displayName())
                .replace("<level>", CustomEnchantType.toRoman(level)));
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
            case "open" -> handleHeraldOpen(sender, args);
            default -> sender.sendMessage(msg("usage-herald"));
        }
    }

    private void handleHeraldOpen(@NotNull CommandSender sender, @NotNull String[] args) {
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(msg("player-not-found", "<player>", args[2]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(msg("players-only"));
            return;
        }
        HeraldGUI.open(target, plugin.getHeraldManager(), plugin);
        if (!target.equals(sender)) {
            sender.sendMessage(msg("herald-opened", "<player>", target.getName()));
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
        sender.sendMessage(msg("help-givepurification"));
        sender.sendMessage(msg("help-givebook"));
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

        if (args.length == 3 && args[0].equalsIgnoreCase("herald") && args[1].equalsIgnoreCase("open")) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("givescroll")
                || args[0].equalsIgnoreCase("givecoordscroll")
                || args[0].equalsIgnoreCase("givepurification")
                || args[0].equalsIgnoreCase("givepurificationpotion")
                || args[0].equalsIgnoreCase("givebook"))) {
            List<String> names = new ArrayList<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givebook")) {
            return StringUtil.copyPartialMatches(args[2].toLowerCase(Locale.ROOT), customEnchantIds(), new ArrayList<>());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("givebook")) {
            return levelSuggestions(args[3], resolveEnchant(args[2]));
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("givepurification") || args[0].equalsIgnoreCase("givepurificationpotion"))) {
            return StringUtil.copyPartialMatches(args[2], List.of("1", "4", "8", "16", "32", "64"), new ArrayList<>());
        }

        return Collections.emptyList();
    }
}

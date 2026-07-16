package me.lovelace.loveTweaks.scoreboard;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int SLOT_PROFILE = 0;
    public static final int SLOT_TOGGLE = 51;
    public static final int SLOT_BACK = 52;
    public static final int SLOT_CLOSE = 53;
    public static final int PLACEHOLDERS_START = 18;
    public static final int PLACEHOLDERS_END = 35;
    public static final int MAX_PLACEHOLDER_SLOTS = 18;

    private static final int[] FILLER_SLOTS = {
        1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        36, 37, 38, 39, 40, 41, 42, 43, 44,
        45, 46, 47, 48, 49, 50
    };

    private ScoreboardGUI() {}

    public static void open(Player player, PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardGUIHolder holder = new ScoreboardGUIHolder(player.getUniqueId());
        Component title = LEGACY.deserialize(colorize(config.getGuiTitle()));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        populate(inv, holder, player, state, config);
        player.openInventory(inv);
    }

    public static void refresh(Inventory inv, ScoreboardGUIHolder holder, Player player,
                               PlayerScoreboardState state, ScoreboardConfig config) {
        holder.clearSlotMap();
        populate(inv, holder, player, state, config);
    }

    private static void populate(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                 PlayerScoreboardState state, ScoreboardConfig config) {
        // Profile button (slot 0)
        inv.setItem(SLOT_PROFILE, buildProfileHead(player, config));

        // Filler
        ItemStack filler = buildItem(config.getFillerMaterial(), config.getFillerName(), List.of());
        for (int slot : FILLER_SLOTS) inv.setItem(slot, filler);

        // Organize placeholders by sort group for display
        Map<Integer, List<String>> groupedPlaceholders = groupPlaceholders(state, player, config);

        int currentSlot = PLACEHOLDERS_START;
        for (int groupKey : groupedPlaceholders.keySet()) {
            List<String> placeholdersInGroup = groupedPlaceholders.get(groupKey);

            for (String id : placeholdersInGroup) {
                if (currentSlot > PLACEHOLDERS_END) break;

                ScoreboardPlaceholder placeholder = config.getPlaceholder(id);
                if (placeholder == null) continue;

                boolean active = state.hasPlaceholderActive(id);
                boolean conditionMet = placeholder.conditionType().isMet(player);

                ItemStack item;
                if (conditionMet) {
                    item = buildPlaceholderItem(placeholder, active, state, config);
                } else {
                    item = buildLockedPlaceholderItem(placeholder, config);
                }

                inv.setItem(currentSlot, item);
                holder.mapSlot(currentSlot, id);
                currentSlot++;
            }

            if (currentSlot <= PLACEHOLDERS_END) {
                currentSlot++;
            }
        }

        // Fill remaining slots with filler
        for (int i = currentSlot; i <= PLACEHOLDERS_END; i++) {
            inv.setItem(i, filler);
        }

        // Buttons
        inv.setItem(SLOT_TOGGLE, buildToggle(state, config));
        inv.setItem(SLOT_BACK, buildItem(config.getBackMaterial(), config.getBackName(), config.getBackLore()));
        inv.setItem(SLOT_CLOSE, buildItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
    }

    private static Map<Integer, List<String>> groupPlaceholders(PlayerScoreboardState state, Player player,
                                                                  ScoreboardConfig config) {
        Map<Integer, List<String>> groups = new LinkedHashMap<>();
        List<String> active = state.getActivePlaceholders();
        List<String> order = config.getPlaceholderOrder();

        for (String id : active) {
            ScoreboardPlaceholder ph = config.getPlaceholder(id);
            if (ph != null) {
                groups.computeIfAbsent(ph.sortGroup(), k -> new ArrayList<>()).add(id);
            }
        }

        for (String id : order) {
            ScoreboardPlaceholder ph = config.getPlaceholder(id);
            if (ph != null && !active.contains(id)) {
                groups.computeIfAbsent(ph.sortGroup(), k -> new ArrayList<>()).add(id);
            }
        }

        return groups;
    }

    private static ItemStack buildProfileHead(Player player, ScoreboardConfig config) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(LEGACY.deserialize(colorize(config.getProfileName())));

            List<Component> lore = new ArrayList<>();
            for (String line : config.getProfileLore()) {
                lore.add(LEGACY.deserialize(colorize(line)));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack buildToggle(PlayerScoreboardState state, ScoreboardConfig config) {
        if (state.isScoreboardEnabled()) {
            return buildItem(config.getToggleOnMaterial(), config.getToggleOnName(), config.getToggleOnLore());
        } else {
            return buildItem(config.getToggleOffMaterial(), config.getToggleOffName(), config.getToggleOffLore());
        }
    }

    private static ItemStack buildPlaceholderItem(ScoreboardPlaceholder placeholder, boolean active,
                                                    PlayerScoreboardState state, ScoreboardConfig config) {
        String name;
        String iconMat;
        List<String> lore = new ArrayList<>();

        if (active) {
            name = "&a" + stripColor(placeholder.displayName()) + " &8[&a✔&8]";
            iconMat = placeholder.icon().equalsIgnoreCase("PAPER") ? "LIME_DYE" : placeholder.icon();
        } else {
            name = "&7" + stripColor(placeholder.displayName()) + " &8[&7✘&8]";
            iconMat = "GRAY_DYE";
        }

        if (!placeholder.lore().isEmpty()) {
            for (String l : placeholder.lore()) lore.add(colorize(l));
        }

        lore.add("");
        lore.add("§8Шаблон:");
        lore.add("  §7" + colorize(placeholder.template()));
        lore.add("");

        if (active) {
            List<String> activePlaceholders = state.getActivePlaceholders();
            int pos = activePlaceholders.indexOf(placeholder.id()) + 1;
            int total = activePlaceholders.size();
            lore.add("§7Позиция: §f#" + pos + " §7из §f" + total);
            lore.add("");
            if (pos > 1) lore.add("§8▸ §7ЛКМ §8— сдвинуть выше");
            if (pos < total) lore.add("§8▸ §7ПКМ §8— сдвинуть ниже");
            lore.add("§8▸ §7СКМ §8— отключить");
        } else {
            boolean maxed = state.getActivePlaceholders().size() >= config.getMaxPlaceholders();
            if (maxed) {
                lore.add("§c  Достигнут максимум! §7(§f" + config.getMaxPlaceholders() + "§7)");
            }
            lore.add("§8▸ §7СКМ §8— включить");
        }

        return buildItem(iconMat, name, lore);
    }

    private static ItemStack buildLockedPlaceholderItem(ScoreboardPlaceholder placeholder, ScoreboardConfig config) {
        List<String> lore = new ArrayList<>();

        if (!placeholder.lore().isEmpty()) {
            for (String l : placeholder.lore()) lore.add(colorize(l));
        }

        lore.add("");
        lore.add("§cЗаблокирован");
        String conditionMsg = getConditionMessage(placeholder.conditionType());
        if (!conditionMsg.isEmpty()) {
            lore.add("§7" + conditionMsg);
        }

        return buildItem(config.getLockedMaterial(), "&8🔒 " + stripColor(placeholder.displayName()), lore);
    }

    private static String getConditionMessage(ScoreboardPlaceholderCondition condition) {
        return switch (condition) {
            case ALWAYS -> "";
            case IF_HAS_CLAN -> "Требуется клан";
            case IF_HAS_GROUP -> "Требуется группа";
            case IF_HAS_BEHAVIOR -> "Требуется стиль игры";
        };
    }

    static ItemStack buildItem(String materialStr, String displayName, List<String> lore) {
        ItemStack item = resolveItem(materialStr);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(LEGACY.deserialize(colorize(displayName)));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LEGACY.deserialize(colorize(line)));
        }
        meta.lore(loreComponents);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack resolveItem(String materialStr) {
        if (materialStr != null && materialStr.startsWith("basehead-")) {
            ItemStack skull = createBase64Skull(materialStr.substring(9));
            if (skull != null) return skull;
        }
        Material mat = materialStr != null ? Material.matchMaterial(materialStr.toUpperCase()) : null;
        return new ItemStack(mat != null ? mat : Material.STONE);
    }

    private static ItemStack createBase64Skull(String base64) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) return skull;

            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes()));
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            return null;
        }
    }

    private static String colorize(String text) {
        return text.replace('&', '§');
    }

    private static String stripColor(String text) {
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }
}

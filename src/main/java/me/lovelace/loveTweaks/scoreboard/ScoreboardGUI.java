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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int SLOT_PROFILE = 0;
    public static final int SLOT_TOGGLE = 51;
    public static final int SLOT_BACK = 52;
    public static final int SLOT_CLOSE = 53;
    public static final int GUI_SIZE = 54;

    /** Side-wall slots of the 3 work-zone rows (gui-gen-5 RULE 6) — left empty, never filler. */
    private static final int[] WORK_ZONE_WALLS = {18, 26, 27, 35, 36, 44};

    private ScoreboardGUI() {}

    public static void open(Player player, PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardGUIHolder holder = new ScoreboardGUIHolder(player.getUniqueId());
        Component title = LEGACY.deserialize(colorize(config.getGuiTitle()));
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);
        populate(inv, holder, player, state, config);
        player.openInventory(inv);
    }

    public static void refresh(Inventory inv, ScoreboardGUIHolder holder, Player player,
                               PlayerScoreboardState state, ScoreboardConfig config) {
        holder.clearSlotMap();
        populate(inv, holder, player, state, config);
    }

    /**
     * Placeholder slots are fixed by {@link ScoreboardConfig#getPlaceholderSlots()} (grouped by
     * category, assigned once at config load). Clicking a placeholder never moves it to a
     * different GUI slot — LMB/RMB only reorder its position within the rendered scoreboard.
     */
    private static void populate(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                 PlayerScoreboardState state, ScoreboardConfig config) {
        inv.setItem(SLOT_PROFILE, buildProfileHead(player, config));

        Map<String, Integer> slots = config.getPlaceholderSlots();

        Set<Integer> reserved = new HashSet<>(slots.values());
        reserved.add(SLOT_PROFILE);
        reserved.add(SLOT_TOGGLE);
        reserved.add(SLOT_BACK);
        reserved.add(SLOT_CLOSE);

        Set<Integer> emptyWalls = new HashSet<>();
        for (int slot : WORK_ZONE_WALLS) emptyWalls.add(slot);

        ItemStack filler = buildItem(config.getFillerMaterial(), config.getFillerName(), List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (reserved.contains(slot)) continue;
            inv.setItem(slot, emptyWalls.contains(slot) ? null : filler);
        }

        boolean maxed = state.getActivePlaceholders().size() >= config.getMaxPlaceholders();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            String id = entry.getKey();
            ScoreboardPlaceholder placeholder = config.getPlaceholder(id);
            if (placeholder == null) continue;

            boolean active = state.hasPlaceholderActive(id);
            ItemStack item = placeholder.isUnlocked(player)
                    ? buildPlaceholderItem(placeholder, active, maxed, state, config)
                    : buildLockedPlaceholderItem(placeholder, config);

            inv.setItem(entry.getValue(), item);
            holder.mapSlot(entry.getValue(), id);
        }

        inv.setItem(SLOT_TOGGLE, buildToggle(state, config));
        inv.setItem(SLOT_BACK, buildItem(config.getBackMaterial(), config.getBackName(), config.getBackLore()));
        inv.setItem(SLOT_CLOSE, buildItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
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
        if (state.getActivePlaceholders().isEmpty()) {
            return buildItem(config.getToggleEmptyMaterial(), config.getToggleEmptyName(), config.getToggleEmptyLore());
        }
        if (state.isScoreboardEnabled()) {
            return buildItem(config.getToggleOnMaterial(), config.getToggleOnName(), config.getToggleOnLore());
        }
        return buildItem(config.getToggleOffMaterial(), config.getToggleOffName(), config.getToggleOffLore());
    }

    private static ItemStack buildPlaceholderItem(ScoreboardPlaceholder placeholder, boolean active, boolean maxed,
                                                    PlayerScoreboardState state, ScoreboardConfig config) {
        String icon;
        String nameColor;
        if (active) {
            icon = config.getPlaceholderOnMaterial();
            nameColor = "&a";
        } else if (maxed) {
            icon = config.getPlaceholderBlockedMaterial();
            nameColor = "&8";
        } else {
            icon = config.getPlaceholderOffMaterial();
            nameColor = "&7";
        }

        List<String> lore = new ArrayList<>();
        if (!placeholder.lore().isEmpty()) {
            for (String l : placeholder.lore()) lore.add(colorize(l));
            lore.add("");
        }

        if (active) {
            List<String> activePlaceholders = state.getActivePlaceholders();
            int pos = activePlaceholders.indexOf(placeholder.id()) + 1;
            int total = activePlaceholders.size();
            lore.add("&7Позиция в скорборде: &f#" + pos + " &7из &f" + total);
            lore.add("");
            if (pos > 1) lore.add("&aЛКМ &7— сдвинуть выше");
            if (pos < total) lore.add("&aПКМ &7— сдвинуть ниже");
            lore.add("&cСКМ &7— выключить");
        } else if (maxed) {
            lore.add("&cДостигнут максимум &7(&f" + config.getMaxPlaceholders() + "&7)");
        } else {
            lore.add("&aСКМ &7— включить");
        }

        return buildItem(icon, nameColor + stripColor(placeholder.displayName()), lore);
    }

    private static ItemStack buildLockedPlaceholderItem(ScoreboardPlaceholder placeholder, ScoreboardConfig config) {
        List<String> lore = new ArrayList<>();
        if (!placeholder.lore().isEmpty()) {
            for (String l : placeholder.lore()) lore.add(colorize(l));
            lore.add("");
        }
        lore.add("&cНедоступно");
        String reason = placeholder.requirement() != null ? placeholder.requirement().reason() : null;
        if (reason != null && !reason.isBlank()) lore.add(colorize(reason));

        return buildItem(config.getPlaceholderBlockedMaterial(), "&8" + stripColor(placeholder.displayName()), lore);
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

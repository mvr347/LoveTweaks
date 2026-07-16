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
import java.util.List;
import java.util.UUID;

public final class ScoreboardGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int SLOT_TOGGLE = 0;
    public static final int SLOT_BACK = 25;
    public static final int SLOT_CLOSE = 26;
    public static final int SECTIONS_START = 9;
    public static final int MAX_SECTION_SLOTS = 8; // slots 9-16

    private static final int[] FILLER_SLOTS = {
        1, 2, 3, 4, 5, 6, 7, 8,
        17,
        18, 19, 20, 21, 22, 23, 24
    };

    private ScoreboardGUI() {}

    public static void open(Player player, PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardGUIHolder holder = new ScoreboardGUIHolder(player.getUniqueId());
        Component title = LEGACY.deserialize(colorize(config.getGuiTitle()));
        Inventory inv = Bukkit.createInventory(holder, 27, title);
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
        // Toggle
        inv.setItem(SLOT_TOGGLE, buildToggle(state, config));

        // Filler
        ItemStack filler = buildItem(config.getFillerMaterial(), config.getFillerName(), List.of());
        for (int slot : FILLER_SLOTS) inv.setItem(slot, filler);

        // Sections: active first (in order), then inactive (in config order)
        List<String> ordered = new ArrayList<>(state.getActiveSections());
        for (String id : config.getSectionOrder()) {
            if (!ordered.contains(id)) ordered.add(id);
        }

        for (int i = 0; i < ordered.size() && i < MAX_SECTION_SLOTS; i++) {
            String id = ordered.get(i);
            ScoreboardSection section = config.getSection(id);
            if (section == null) continue;
            int slot = SECTIONS_START + i;
            boolean active = state.hasSectionActive(id);
            inv.setItem(slot, buildSectionItem(section, active, state, config));
            holder.mapSlot(slot, id);
        }

        // Filler for unused section slots
        for (int i = ordered.size(); i < MAX_SECTION_SLOTS; i++) {
            inv.setItem(SECTIONS_START + i, filler);
        }

        // Back / Close
        inv.setItem(SLOT_BACK, buildItem(config.getBackMaterial(), config.getBackName(), config.getBackLore()));
        inv.setItem(SLOT_CLOSE, buildItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
    }

    private static ItemStack buildToggle(PlayerScoreboardState state, ScoreboardConfig config) {
        if (state.isScoreboardEnabled()) {
            return buildItem(config.getToggleOnMaterial(), config.getToggleOnName(), config.getToggleOnLore());
        } else {
            return buildItem(config.getToggleOffMaterial(), config.getToggleOffName(), config.getToggleOffLore());
        }
    }

    private static ItemStack buildSectionItem(ScoreboardSection section, boolean active,
                                              PlayerScoreboardState state, ScoreboardConfig config) {
        String name;
        String iconMat;
        List<String> lore = new ArrayList<>();

        if (active) {
            name = "&a" + stripColor(section.displayName()) + " &8[&a✔&8]";
            // Use section's configured icon for active state; fall back to LIME_DYE
            iconMat = section.icon().equalsIgnoreCase("PAPER") ? "LIME_DYE" : section.icon();
        } else {
            name = "&7" + stripColor(section.displayName()) + " &8[&7✘&8]";
            iconMat = "GRAY_DYE";
        }

        // Base description from section config
        if (!section.lore().isEmpty()) {
            for (String l : section.lore()) lore.add(colorize(l));
        }

        // Preview of scoreboard lines
        lore.add("");
        lore.add("§8Предпросмотр строк:");
        for (String line : section.lines()) {
            lore.add("  §7" + colorize(line));
        }
        lore.add("");

        if (active) {
            List<String> activeSections = state.getActiveSections();
            int pos = activeSections.indexOf(section.id()) + 1;
            int total = activeSections.size();
            lore.add("§7Позиция: §f#" + pos + " §7из §f" + total);
            lore.add("");
            if (pos > 1) lore.add("§8▸ §7ЛКМ §8— сдвинуть выше");
            if (pos < total) lore.add("§8▸ §7ПКМ §8— сдвинуть ниже");
            lore.add("§8▸ §7СКМ §8— отключить");
        } else {
            boolean maxed = state.getActiveSections().size() >= config.getMaxSections();
            if (maxed) {
                lore.add("§c  Достигнут максимум разделов! §7(§f" + config.getMaxSections() + "§7)");
            }
            lore.add("§8▸ §7СКМ §8— включить");
        }

        return buildItem(iconMat, name, lore);
    }

    // Creates an ItemStack from a material string (supports basehead-<base64>) with display name and lore.
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

            // Paper API: create profile with deterministic UUID from base64 hash, set texture property
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

    // Strips existing §/& color codes so we can apply our own active/inactive color prefix.
    private static String stripColor(String text) {
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }
}

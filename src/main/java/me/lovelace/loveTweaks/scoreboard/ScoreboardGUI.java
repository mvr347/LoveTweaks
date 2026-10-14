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
import java.util.List;
import java.util.UUID;

public final class ScoreboardGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static final int SLOT_PROFILE = 0;
    public static final int SLOT_TOGGLE = 51;
    public static final int SLOT_BACK = 52;
    public static final int SLOT_CLOSE = 53;
    public static final int GUI_SIZE = 54;

    private ScoreboardGUI() {}

    // ── Opening / refreshing ──────────────────────────────────────────────

    public static void open(Player player, PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardGUIHolder holder = new ScoreboardGUIHolder(player.getUniqueId());
        Component title = LEGACY.deserialize(colorize(config.getGuiTitle()));
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);
        populate(inv, holder, player, state, config);
        player.openInventory(inv);
    }

    public static void openCategory(Player player, String categoryId, PlayerScoreboardState state,
                                    ScoreboardConfig config) {
        ScoreboardCategory category = config.getCategories().get(categoryId);
        if (category == null) return;
        List<ScoreboardPlaceholder> items = config.placeholdersOf(categoryId);
        int size = ScoreboardLayout.submenuSize(items.size());
        ScoreboardGUIHolder holder = new ScoreboardGUIHolder(player.getUniqueId(),
                ScoreboardGUIHolder.Type.CATEGORY, categoryId);
        Component title = LEGACY.deserialize(colorize(config.getGuiTitle() + "&8 · " + stripColor(category.displayName())));
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        populate(inv, holder, player, state, config);
        player.openInventory(inv);
    }

    public static void refresh(Inventory inv, ScoreboardGUIHolder holder, Player player,
                               PlayerScoreboardState state, ScoreboardConfig config) {
        holder.clearSlotMap();
        inv.clear();
        populate(inv, holder, player, state, config);
    }

    private static void populate(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                 PlayerScoreboardState state, ScoreboardConfig config) {
        if (holder.getType() == ScoreboardGUIHolder.Type.CATEGORY) {
            populateCategory(inv, holder, player, state, config);
        } else {
            populateMain(inv, holder, player, state, config);
        }
    }

    // ── Main menu (54) ────────────────────────────────────────────────────

    /**
     * Header: profile head + view toggle in the centre of the preset buttons. Work zone: either
     * the numbered slot cards + category row, or (view "Все") the flat paginated list. Footer:
     * on/off toggle (51), back (52), close (53). Glass exists only in Header/Footer.
     */
    private static void populateMain(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                     PlayerScoreboardState state, ScoreboardConfig config) {
        ItemStack filler = buildItem(config.getFillerMaterial(), config.getFillerName(), List.of());
        List<ScoreboardPreset> presets = new ArrayList<>(config.getPresets().values());
        boolean hasBack = config.getBackCommand() != null && !config.getBackCommand().isBlank();

        ScoreboardLayout.Role[] frame = ScoreboardLayout.frame(GUI_SIZE, presets.size() + 1, true, hasBack);
        for (int slot = 0; slot < frame.length; slot++) {
            switch (frame[slot]) {
                case PROFILE -> inv.setItem(slot, buildProfileHead(player, config));
                case GLASS -> inv.setItem(slot, filler);
                case EXTRA -> inv.setItem(slot, buildToggle(state, config));
                case BACK -> inv.setItem(slot, buildItem(config.getBackMaterial(), config.getBackName(), config.getBackLore()));
                case CLOSE -> inv.setItem(slot, buildItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
                default -> { /* CONTROL slots are filled below, EMPTY stays empty */ }
            }
        }

        // Header controls: view toggle in the centre, presets around it (RULE 4).
        int toggleSlot = ScoreboardLayout.viewToggleSlot(presets.size());
        inv.setItem(toggleSlot, buildViewToggle(state, config));
        holder.mapSlot(toggleSlot, ScoreboardGUIHolder.Kind.VIEW_TOGGLE, "");
        int[] presetSlots = ScoreboardLayout.presetSlots(presets.size());
        for (int i = 0; i < presets.size(); i++) {
            inv.setItem(presetSlots[i], buildPresetButton(presets.get(i), player, state, config));
            holder.mapSlot(presetSlots[i], ScoreboardGUIHolder.Kind.PRESET, presets.get(i).id());
        }

        if (state.isViewAll()) {
            populateFlat(inv, holder, player, state, config);
        } else {
            populateCards(inv, holder, player, state, config);
        }
    }

    /** "По категориям": numbered slot cards (filled in selection order) + category row. */
    private static void populateCards(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                      PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        List<String> active = state.getActivePlaceholders();
        int cards = ScoreboardLayout.cardCount(config.getMaxPlaceholders());
        for (int i = 0; i < cards; i++) {
            int slot = ScoreboardLayout.CARD_SLOTS[i];
            ScoreboardPlaceholder ph = i < active.size() ? config.getPlaceholder(active.get(i)) : null;
            if (ph == null) {
                inv.setItem(slot, buildItem(config.getPlaceholderOffMaterial(),
                        msg.get("slot-empty-name", "n", i + 1),
                        List.of("", msg.get("slot-empty-lore"))));
                continue;
            }
            inv.setItem(slot, buildCard(ph, i, state, player, config));
            holder.mapSlot(slot, ScoreboardGUIHolder.Kind.PLACEHOLDER, ph.id());
        }

        List<ScoreboardCategory> categories = new ArrayList<>(config.getCategories().values());
        int[] slots = ScoreboardLayout.categorySlots(categories.size());
        for (int i = 0; i < slots.length; i++) {
            ScoreboardCategory category = categories.get(i);
            inv.setItem(slots[i], buildCategoryButton(category, state, config));
            holder.mapSlot(slots[i], ScoreboardGUIHolder.Kind.CATEGORY, category.id());
        }
    }

    /** "Все": the whole work zone as one flat list, paginated 21 per page (arrows at 36 / 44). */
    private static void populateFlat(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                     PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        List<String> active = state.getActivePlaceholders();
        List<PlaceholderSorter.Row> rows = new ArrayList<>();
        for (ScoreboardPlaceholder ph : config.getPlaceholders().values()) {
            rows.add(new PlaceholderSorter.Row(ph.id(), stripColor(ph.displayName()), active.indexOf(ph.id()),
                    ScoreboardAvailability.isAvailable(player, ph)));
        }
        List<String> sorted = PlaceholderSorter.sort(rows);

        int page = ScoreboardLayout.clampPage(holder.getPage(), sorted.size());
        holder.setPage(page);
        int pages = ScoreboardLayout.pageCount(sorted.size());
        int[] range = ScoreboardLayout.pageRange(sorted.size(), page);
        for (int i = range[0]; i < range[1]; i++) {
            ScoreboardPlaceholder ph = config.getPlaceholder(sorted.get(i));
            if (ph == null) continue;
            int slot = ScoreboardLayout.FLAT_SLOTS[i - range[0]];
            inv.setItem(slot, buildFlatItem(ph, state, player, config));
            holder.mapSlot(slot, ScoreboardGUIHolder.Kind.PLACEHOLDER, ph.id());
        }

        if (page > 0) {
            inv.setItem(ScoreboardLayout.PAGE_PREV, buildItem(config.getPagePrevMaterial(), msg.get("page-prev"),
                    List.of("", msg.get("page-lore", "page", page + 1, "pages", pages))));
            holder.mapSlot(ScoreboardLayout.PAGE_PREV, ScoreboardGUIHolder.Kind.PAGE_PREV, "");
        }
        if (page < pages - 1) {
            inv.setItem(ScoreboardLayout.PAGE_NEXT, buildItem(config.getPageNextMaterial(), msg.get("page-next"),
                    List.of("", msg.get("page-lore", "page", page + 1, "pages", pages))));
            holder.mapSlot(ScoreboardLayout.PAGE_NEXT, ScoreboardGUIHolder.Kind.PAGE_NEXT, "");
        }
    }

    // ── Category submenu (27 / 36 / 45) ───────────────────────────────────

    private static void populateCategory(Inventory inv, ScoreboardGUIHolder holder, Player player,
                                         PlayerScoreboardState state, ScoreboardConfig config) {
        ItemStack filler = buildItem(config.getFillerMaterial(), config.getFillerName(), List.of());
        ScoreboardCategory category = config.getCategories().get(holder.getCategoryId());
        int size = inv.getSize();

        // No controls and no extra button here; Back always leads to the main menu.
        ScoreboardLayout.Role[] frame = ScoreboardLayout.frame(size, 0, false, true);
        for (int slot = 0; slot < frame.length; slot++) {
            switch (frame[slot]) {
                case PROFILE -> inv.setItem(slot, category != null ? buildCategoryIcon(category, config) : filler);
                case GLASS -> inv.setItem(slot, filler);
                case BACK -> inv.setItem(slot, buildItem(config.getBackMaterial(), config.getBackName(),
                        List.of("", config.messages().get("back-to-main"))));
                case CLOSE -> inv.setItem(slot, buildItem(config.getCloseMaterial(), config.getCloseName(), config.getCloseLore()));
                default -> { }
            }
        }

        List<ScoreboardPlaceholder> items = config.placeholdersOf(holder.getCategoryId());
        int[] slots = ScoreboardLayout.submenuSlots(size, items.size());
        for (int i = 0; i < slots.length; i++) {
            ScoreboardPlaceholder ph = items.get(i);
            inv.setItem(slots[i], buildSubmenuItem(ph, state, player, config));
            holder.mapSlot(slots[i], ScoreboardGUIHolder.Kind.PLACEHOLDER, ph.id());
        }
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

    private static ItemStack buildViewToggle(PlayerScoreboardState state, ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        String current = msg.get(state.isViewAll() ? "view-name-all" : "view-name-categories");
        String other = msg.get(state.isViewAll() ? "view-name-categories" : "view-name-all");
        return buildItem(config.getViewButtonMaterial(), msg.get("view-title"), List.of(
                "", msg.get("view-current", "current", current), msg.get("view-other", "other", other),
                "", msg.get("view-click")));
    }

    private static ItemStack buildPresetButton(ScoreboardPreset preset, Player player, PlayerScoreboardState state,
                                               ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        List<ScoreboardEntry> resolved = resolvePreset(preset, player, config);
        String icon = preset.icon() != null ? preset.icon() : config.getPresetButtonMaterial();

        List<String> lore = new ArrayList<>();
        for (String l : preset.lore()) lore.add(l);
        if (!preset.lore().isEmpty()) lore.add("");
        lore.add(msg.get("preset-header"));
        for (ScoreboardEntry e : preset.entries()) {
            ScoreboardPlaceholder ph = config.getPlaceholder(e.id());
            if (ph == null) continue;
            String name = stripColor(ph.displayName());
            boolean used = resolved.stream().anyMatch(r -> r.id().equals(e.id()));
            lore.add(msg.get(used ? "preset-line" : "preset-line-skipped", "name", name));
        }
        lore.add("");

        if (resolved.isEmpty()) {
            lore.add(msg.get("preset-unavailable"));
            lore.add(msg.get("preset-unavailable-reason"));
            return buildItem(config.getPlaceholderBlockedMaterial(), "&8" + stripColor(preset.displayName()), lore);
        }
        if (PresetApplier.sameSet(state.entries(), resolved)) {
            lore.add(msg.get("preset-active"));
            return buildItem(config.getToggleOnMaterial(), "&a" + stripColor(preset.displayName()), lore);
        }
        lore.add(msg.get("preset-click"));
        return buildItem(icon, "&e" + stripColor(preset.displayName()), lore);
    }

    /** The entries a preset would enable for this player (unavailable rows skipped, limit applied). */
    public static List<ScoreboardEntry> resolvePreset(ScoreboardPreset preset, Player player, ScoreboardConfig config) {
        return PresetApplier.resolve(preset.entries(),
                id -> {
                    ScoreboardPlaceholder ph = config.getPlaceholder(id);
                    return ph != null && ScoreboardAvailability.isAvailable(player, ph);
                },
                id -> {
                    ScoreboardPlaceholder ph = config.getPlaceholder(id);
                    return ph != null ? ph.variantCount() : 0;
                },
                config.getMaxPlaceholders());
    }

    private static ItemStack buildCategoryIcon(ScoreboardCategory category, ScoreboardConfig config) {
        String icon = category.icon() != null ? category.icon() : config.getCategoryButtonMaterial();
        return buildItem(icon, category.displayName(), new ArrayList<>(category.lore()));
    }

    private static ItemStack buildCategoryButton(ScoreboardCategory category, PlayerScoreboardState state,
                                                 ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        List<ScoreboardPlaceholder> items = config.placeholdersOf(category.id());
        int on = 0;
        for (ScoreboardPlaceholder ph : items) if (state.hasPlaceholderActive(ph.id())) on++;

        List<String> lore = new ArrayList<>(category.lore());
        if (!category.lore().isEmpty()) lore.add("");
        lore.add(msg.get("category-enabled", "on", on, "total", items.size()));
        lore.add("");
        lore.add(msg.get("category-open"));
        String icon = category.icon() != null ? category.icon() : config.getCategoryButtonMaterial();
        return buildItem(icon, category.displayName(), lore);
    }

    private static List<String> baseLore(ScoreboardPlaceholder ph) {
        List<String> lore = new ArrayList<>();
        if (!ph.lore().isEmpty()) {
            for (String l : ph.lore()) lore.add(colorize(l));
            lore.add("");
        }
        return lore;
    }

    private static ItemStack buildLockedItem(ScoreboardPlaceholder ph, ScoreboardAvailability.Status status,
                                             List<String> trailing, ScoreboardConfig config) {
        List<String> lore = baseLore(ph);
        lore.add(config.messages().get("unavailable"));
        String reason = ScoreboardAvailability.reason(status, ph, config);
        if (reason != null && !reason.isBlank()) lore.add(colorize(reason));
        lore.addAll(trailing);
        return buildItem(config.getPlaceholderBlockedMaterial(), "&8" + stripColor(ph.displayName()), lore);
    }

    /** Numbered slot card of the "По категориям" view. */
    private static ItemStack buildCard(ScoreboardPlaceholder ph, int index, PlayerScoreboardState state, Player player,
                                       ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        int total = state.getActivePlaceholders().size();
        List<String> actions = new ArrayList<>();
        actions.add("");
        if (index > 0) actions.add(msg.get("slot-up"));
        if (index < total - 1) actions.add(msg.get("slot-down"));
        if (ph.variantCount() > 1) {
            actions.add(msg.get("slot-variant", "variant", ph.variantName(state.getVariant(ph.id()))));
        }
        actions.add(msg.get("slot-remove"));

        ScoreboardAvailability.Status status = ScoreboardAvailability.check(player, ph);
        if (status != ScoreboardAvailability.Status.AVAILABLE) {
            return buildLockedItem(ph, status, actions, config);
        }
        List<String> lore = baseLore(ph);
        lore.add(msg.get("slot-label", "n", index + 1));
        lore.addAll(actions);
        return buildItem(config.getPlaceholderOnMaterial(), "&a" + stripColor(ph.displayName()), lore);
    }

    /** Row of the flat "Все" view. */
    private static ItemStack buildFlatItem(ScoreboardPlaceholder ph, PlayerScoreboardState state, Player player,
                                           ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        boolean active = state.hasPlaceholderActive(ph.id());
        boolean full = state.getActivePlaceholders().size() >= config.getMaxPlaceholders();
        ScoreboardAvailability.Status status = ScoreboardAvailability.check(player, ph);

        List<String> actions = new ArrayList<>();
        actions.add("");
        if (active) {
            List<String> list = state.getActivePlaceholders();
            int pos = list.indexOf(ph.id()) + 1;
            actions.add(msg.get("flat-position", "pos", pos, "total", list.size()));
            if (pos > 1) actions.add(msg.get("flat-up"));
            if (pos < list.size()) actions.add(msg.get("flat-down"));
            if (ph.variantCount() > 1) {
                actions.add(msg.get("slot-variant", "variant", ph.variantName(state.getVariant(ph.id()))));
            }
            actions.add(msg.get("flat-disable"));
        } else if (status == ScoreboardAvailability.Status.AVAILABLE) {
            if (full) actions.add(msg.get("reason-full", "max", config.getMaxPlaceholders()));
            else actions.add(msg.get("flat-enable"));
        }

        if (status != ScoreboardAvailability.Status.AVAILABLE) {
            return buildLockedItem(ph, status, actions, config);
        }
        String icon = active ? config.getPlaceholderOnMaterial()
                : full ? config.getPlaceholderBlockedMaterial() : config.getPlaceholderOffMaterial();
        String color = active ? "&a" : full ? "&8" : "&7";
        List<String> lore = baseLore(ph);
        lore.addAll(actions);
        return buildItem(icon, color + stripColor(ph.displayName()), lore);
    }

    /** Row of a category submenu. */
    private static ItemStack buildSubmenuItem(ScoreboardPlaceholder ph, PlayerScoreboardState state, Player player,
                                              ScoreboardConfig config) {
        ScoreboardMessages msg = config.messages();
        boolean active = state.hasPlaceholderActive(ph.id());
        boolean full = state.getActivePlaceholders().size() >= config.getMaxPlaceholders();
        ScoreboardAvailability.Status status = ScoreboardAvailability.check(player, ph);

        List<String> actions = new ArrayList<>();
        actions.add("");
        if (active) {
            actions.add(msg.get("sub-slot", "n", state.getActivePlaceholders().indexOf(ph.id()) + 1));
            if (ph.variantCount() > 1) {
                actions.add(msg.get("sub-variant", "variant", ph.variantName(state.getVariant(ph.id()))));
            }
            actions.add(msg.get("sub-disable"));
        }

        if (status != ScoreboardAvailability.Status.AVAILABLE) {
            return buildLockedItem(ph, status, actions, config);
        }
        if (!active && full) {
            // All slots taken: the reason replaces the enable hint, like any other locked item.
            List<String> lore = baseLore(ph);
            lore.add(msg.get("unavailable"));
            lore.add(msg.get("reason-full", "max", config.getMaxPlaceholders()));
            return buildItem(config.getPlaceholderBlockedMaterial(), "&8" + stripColor(ph.displayName()), lore);
        }
        List<String> lore = baseLore(ph);
        if (active) {
            lore.addAll(actions);
        } else {
            lore.add("");
            lore.add(msg.get("sub-enable"));
        }
        return buildItem(active ? config.getPlaceholderOnMaterial() : config.getPlaceholderOffMaterial(),
                (active ? "&a" : "&7") + stripColor(ph.displayName()), lore);
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

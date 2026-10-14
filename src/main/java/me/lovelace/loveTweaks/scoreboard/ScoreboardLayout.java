package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Slot arithmetic of the /scoreboard menus (gui_gen v2.1). Bukkit-free so it can be unit tested.
 *
 * <p>Header = row 0 (+ Row1 from 45 slots), Footer = always the last row, work zone = everything
 * in between. Glass lives only in Header/Footer; the side walls of the work zone stay empty.
 */
public final class ScoreboardLayout {

    /** What a slot of the menu frame (Header + Footer + work zone) holds. */
    public enum Role { PROFILE, GLASS, CONTROL, EXTRA, BACK, CLOSE, EMPTY }

    /** Hard cap of scoreboard lines: 8 cards fit a 2x4 grid and the 15-line scoreboard body. */
    public static final int MAX_LINES = 8;

    public static final int MAIN_SIZE = 54;

    /** Numbered line cards of the "По категориям" view: two pairs per row, like pairs on the board. */
    public static final int[] CARD_SLOTS = {20, 21, 23, 24, 29, 30, 32, 33};
    /** Category buttons of the main menu. */
    public static final int[] CATEGORY_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    /** Work zone of the 54-slot menu without the side walls: the flat "Все" view. */
    public static final int[] FLAT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    public static final int PAGE_SIZE = FLAT_SLOTS.length;
    public static final int PAGE_PREV = 36;
    public static final int PAGE_NEXT = 44;

    /** Category submenus hold at most three work rows (a 54-slot menu). */
    public static final int MAX_SUBMENU_ITEMS = 21;

    private ScoreboardLayout() {}

    /** Header slots 2-7 for {@code count} control buttons, centred (RULE 4). */
    public static int[] controlSlots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 6, 7};
            case 6 -> new int[]{2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("Header fits 0-6 control buttons, got " + count);
        };
    }

    /** The control slot holding the middle button (the view toggle), e.g. slot 4 of {2,3,4,6,7}. */
    public static int middleControl(int count) {
        int[] slots = controlSlots(count);
        if (slots.length == 0) throw new IllegalArgumentException("no controls");
        return slots[slots.length / 2 - (slots.length % 2 == 0 ? 1 : 0)];
    }

    /** Slot of the "По категориям / Все" toggle: the centre of the header controls (presets + toggle). */
    public static int viewToggleSlot(int presetCount) {
        return middleControl(presetCount + 1);
    }

    /** Slots of the preset buttons: all controls of (presets + toggle) except the toggle's centre slot. */
    public static int[] presetSlots(int presetCount) {
        int toggle = viewToggleSlot(presetCount);
        int[] out = new int[presetCount];
        int i = 0;
        for (int s : controlSlots(presetCount + 1)) {
            if (s != toggle) out[i++] = s;
        }
        return out;
    }

    /** Category buttons are centred in the 37-43 row. */
    public static int[] categorySlots(int categoryCount) {
        int n = Math.min(categoryCount, CATEGORY_SLOTS.length);
        int[] cols = centeredColumns(n);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = 36 + cols[i];
        return out;
    }

    /** Number of line cards shown: {@code max-placeholders} clamped to [1, 8]. */
    public static int cardCount(int maxPlaceholders) {
        return Math.max(1, Math.min(MAX_LINES, maxPlaceholders));
    }

    /** Clamps the configured line limit (also protects against 0 / negative / absurd values). */
    public static int clampMaxLines(int configured) {
        return cardCount(configured);
    }

    public static int workStart(int size) {
        return size >= 45 ? 18 : 9;
    }

    public static int workRows(int size) {
        return (size - 9 - workStart(size)) / 9;
    }

    public static int footerStart(int size) {
        return size - 9;
    }

    public static int extraSlot(int size) { return size - 3; }
    public static int backSlot(int size) { return size - 2; }
    public static int closeSlot(int size) { return size - 1; }

    /** Side-wall slots of the work zone (columns 0 and 8): always empty, except pagination in 54. */
    public static List<Integer> wallSlots(int size) {
        List<Integer> out = new ArrayList<>();
        for (int row = 0; row < workRows(size); row++) {
            int base = workStart(size) + row * 9;
            out.add(base);
            out.add(base + 8);
        }
        return out;
    }

    /** Roles of every slot of Header + Footer; the work zone is {@link Role#EMPTY}. */
    public static Role[] frame(int size, int controlCount, boolean hasExtra, boolean hasBack) {
        if (size != 27 && size != 36 && size != 45 && size != 54) {
            throw new IllegalArgumentException("Non-standard menu size " + size);
        }
        Role[] roles = new Role[size];
        java.util.Arrays.fill(roles, Role.EMPTY);
        roles[0] = Role.PROFILE;
        for (int i = 1; i <= 8; i++) roles[i] = Role.GLASS;
        for (int s : controlSlots(controlCount)) roles[s] = Role.CONTROL;
        if (size >= 45) {
            for (int i = 9; i <= 17; i++) roles[i] = Role.GLASS;
        }
        for (int i = footerStart(size); i < size; i++) roles[i] = Role.GLASS;
        if (hasExtra) roles[extraSlot(size)] = Role.EXTRA;
        if (hasBack) roles[backSlot(size)] = Role.BACK;
        roles[closeSlot(size)] = Role.CLOSE;
        return roles;
    }

    // ── "Все" view pagination ─────────────────────────────────────────────

    public static int pageCount(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static int clampPage(int page, int total) {
        return Math.max(0, Math.min(page, pageCount(total) - 1));
    }

    /** {from, to} (exclusive) indices of the given page. */
    public static int[] pageRange(int total, int page) {
        int p = clampPage(page, total);
        int from = Math.min(total, p * PAGE_SIZE);
        return new int[]{from, Math.min(total, from + PAGE_SIZE)};
    }

    // ── Category submenus ─────────────────────────────────────────────────

    /** Rows needed for {@code count} items (7 per row), clamped to the 3 rows of the biggest menu. */
    public static int submenuRows(int count) {
        return Math.max(1, Math.min(3, (Math.min(count, MAX_SUBMENU_ITEMS) + 6) / 7));
    }

    /**
     * 27 (one work row) / 36 (two) / 54 (three). 45 is deliberately skipped: it has the same two work
     * rows as 36 (Row1 eats the third), so it would only add glass without room for content. Pagination
     * is not needed: 21 items always fit the three rows of a 54-slot menu.
     */
    public static int submenuSize(int count) {
        return switch (submenuRows(count)) {
            case 1 -> 27;
            case 2 -> 36;
            default -> 54;
        };
    }

    /** Columns (1-7) used by a row of {@code n} buttons, symmetric around column 4. */
    public static int[] centeredColumns(int n) {
        return switch (n) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{3, 4, 5};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            case 7 -> new int[]{1, 2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("A row holds 0-7 buttons, got " + n);
        };
    }

    /**
     * Content slots of a category submenu: items are spread evenly over the needed rows
     * (9 -> 5 + 4, not 7 + 2) and every row is centred.
     */
    public static int[] submenuSlots(int size, int count) {
        int shown = Math.min(count, MAX_SUBMENU_ITEMS);
        if (shown == 0) return new int[0];
        int rows = Math.min(workRows(size), (shown + 6) / 7);
        int base = shown / rows;
        int extra = shown % rows;
        int[] slots = new int[shown];
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            int inRow = base + (r < extra ? 1 : 0);
            int rowStart = workStart(size) + r * 9;
            for (int col : centeredColumns(inRow)) slots[idx++] = rowStart + col;
        }
        return slots;
    }
}

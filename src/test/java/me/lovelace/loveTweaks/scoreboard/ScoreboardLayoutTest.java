package me.lovelace.loveTweaks.scoreboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardLayoutTest {

    private static final ScoreboardLayout.Role EMPTY = ScoreboardLayout.Role.EMPTY;

    @Test
    @DisplayName("main menu: slot cards, category row and header controls are where the plan puts them")
    void mainMenuSlots() {
        assertArrayEquals(new int[]{20, 21, 23, 24, 29, 30, 32, 33}, ScoreboardLayout.CARD_SLOTS);
        assertArrayEquals(new int[]{37, 38, 39, 40, 41, 42, 43}, ScoreboardLayout.CATEGORY_SLOTS);
        assertArrayEquals(new int[]{37, 38, 39, 40, 41, 42, 43}, ScoreboardLayout.categorySlots(7));
        // 4 presets + the view toggle = 5 controls at 2,3,4,6,7 with the toggle in the centre
        assertArrayEquals(new int[]{2, 3, 4, 6, 7}, ScoreboardLayout.controlSlots(5));
        assertEquals(4, ScoreboardLayout.viewToggleSlot(4));
        assertArrayEquals(new int[]{2, 3, 6, 7}, ScoreboardLayout.presetSlots(4));
    }

    @Test
    @DisplayName("toggle sits in the centre of the controls for every preset count; presets never collide with it")
    void toggleCentred() {
        for (int presets = 0; presets <= ScoreboardConfig.MAX_PRESETS; presets++) {
            int toggle = ScoreboardLayout.viewToggleSlot(presets);
            int[] slots = ScoreboardLayout.presetSlots(presets);
            assertEquals(presets, slots.length);
            Set<Integer> all = new TreeSet<>();
            for (int s : slots) all.add(s);
            all.add(toggle);
            assertEquals(presets + 1, all.size(), "presets=" + presets);
            assertEquals(new TreeSet<>(all), new TreeSet<>(toInts(ScoreboardLayout.controlSlots(presets + 1))));
            // as central as the symmetric layout allows: as many controls left of it as right (+-1)
            long left = all.stream().filter(s -> s < toggle).count();
            long right = all.stream().filter(s -> s > toggle).count();
            assertTrue(Math.abs(left - right) <= 1, "presets=" + presets);
        }
    }

    private static List<Integer> toInts(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int i : a) l.add(i);
        return l;
    }

    @Test
    @DisplayName("header controls fit 2-7 for 1..6 buttons (1-4 mirror around slot 4); 7 is rejected")
    void controlSlotsSymmetric() {
        for (int n = 1; n <= 6; n++) {
            int[] s = ScoreboardLayout.controlSlots(n);
            assertEquals(n, s.length);
            for (int slot : s) assertTrue(slot >= 2 && slot <= 7);
            if (n <= 4) { // 1-4 buttons mirror around the centre column 4; 5 and 6 follow the standard's own table
                Set<Integer> set = new HashSet<>(toInts(s));
                for (int slot : s) assertTrue(set.contains(8 - slot), "n=" + n + " slot=" + slot);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ScoreboardLayout.controlSlots(7));
    }

    @Test
    @DisplayName("54-slot frame: glass only in Header and Footer, work zone empty, footer 51/52/53")
    void mainFrame() {
        ScoreboardLayout.Role[] f = ScoreboardLayout.frame(54, 5, true, true);
        assertEquals(ScoreboardLayout.Role.PROFILE, f[0]);
        for (int s : new int[]{2, 3, 4, 6, 7}) assertEquals(ScoreboardLayout.Role.CONTROL, f[s]);
        assertEquals(ScoreboardLayout.Role.GLASS, f[1]);
        assertEquals(ScoreboardLayout.Role.GLASS, f[5]); // the gap of 5 controls is glass, never empty
        assertEquals(ScoreboardLayout.Role.GLASS, f[8]);
        for (int s = 9; s <= 17; s++) assertEquals(ScoreboardLayout.Role.GLASS, f[s], "Row1 slot " + s);
        for (int s = 18; s <= 44; s++) assertEquals(EMPTY, f[s], "work zone slot " + s);
        for (int s = 45; s <= 50; s++) assertEquals(ScoreboardLayout.Role.GLASS, f[s]);
        assertEquals(ScoreboardLayout.Role.EXTRA, f[51]);
        assertEquals(ScoreboardLayout.Role.BACK, f[52]);
        assertEquals(ScoreboardLayout.Role.CLOSE, f[53]);
    }

    @Test
    @DisplayName("a missing Back / extra button brings the glass back; header/footer never have empty slots")
    void noEmptyInHeaderFooter() {
        for (int size : new int[]{27, 36, 45, 54}) {
            for (boolean extra : new boolean[]{true, false}) {
                for (boolean back : new boolean[]{true, false}) {
                    ScoreboardLayout.Role[] f = ScoreboardLayout.frame(size, 3, extra, back);
                    int headerEnd = size >= 45 ? 18 : 9;
                    for (int s = 0; s < headerEnd; s++) assertNotEquals(EMPTY, f[s], size + " header " + s);
                    for (int s = size - 9; s < size; s++) assertNotEquals(EMPTY, f[s], size + " footer " + s);
                    for (int s = headerEnd; s < size - 9; s++) assertEquals(EMPTY, f[s], size + " work " + s);
                    assertEquals(extra ? ScoreboardLayout.Role.EXTRA : ScoreboardLayout.Role.GLASS, f[size - 3]);
                    assertEquals(back ? ScoreboardLayout.Role.BACK : ScoreboardLayout.Role.GLASS, f[size - 2]);
                    assertEquals(ScoreboardLayout.Role.CLOSE, f[size - 1]);
                    if (size >= 45) assertEquals(9, countGlass(f, 9, 18), "Row1 is all glass");
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ScoreboardLayout.frame(18, 0, true, true));
    }

    private static int countGlass(ScoreboardLayout.Role[] f, int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (f[i] == ScoreboardLayout.Role.GLASS) n++;
        return n;
    }

    @Test
    @DisplayName("cards, categories and flat slots never touch the side walls, header or footer")
    void contentAvoidsWalls() {
        Set<Integer> walls = new HashSet<>(ScoreboardLayout.wallSlots(54));
        assertEquals(Set.of(18, 26, 27, 35, 36, 44), walls);
        for (int s : ScoreboardLayout.CARD_SLOTS) assertFalse(walls.contains(s));
        for (int s : ScoreboardLayout.CATEGORY_SLOTS) assertFalse(walls.contains(s));
        for (int s : ScoreboardLayout.FLAT_SLOTS) {
            assertFalse(walls.contains(s));
            assertTrue(s >= 18 && s < 45);
        }
        assertEquals(21, ScoreboardLayout.FLAT_SLOTS.length);
        assertEquals(36, ScoreboardLayout.PAGE_PREV);
        assertEquals(44, ScoreboardLayout.PAGE_NEXT);
        assertTrue(walls.contains(ScoreboardLayout.PAGE_PREV) && walls.contains(ScoreboardLayout.PAGE_NEXT));
    }

    @Test
    @DisplayName("line limit is clamped to 1..8")
    void clampMax() {
        assertEquals(8, ScoreboardLayout.clampMaxLines(50));
        assertEquals(8, ScoreboardLayout.clampMaxLines(8));
        assertEquals(5, ScoreboardLayout.clampMaxLines(5));
        assertEquals(1, ScoreboardLayout.clampMaxLines(0));
        assertEquals(1, ScoreboardLayout.clampMaxLines(-3));
        assertEquals(8, ScoreboardLayout.CARD_SLOTS.length);
    }

    @Test
    @DisplayName("flat view paginates by 21 with clamped pages")
    void pagination() {
        assertEquals(1, ScoreboardLayout.pageCount(0));
        assertEquals(1, ScoreboardLayout.pageCount(21));
        assertEquals(2, ScoreboardLayout.pageCount(22));
        assertEquals(2, ScoreboardLayout.pageCount(39));
        assertEquals(3, ScoreboardLayout.pageCount(43));
        assertArrayEquals(new int[]{0, 21}, ScoreboardLayout.pageRange(39, 0));
        assertArrayEquals(new int[]{21, 39}, ScoreboardLayout.pageRange(39, 1));
        assertArrayEquals(new int[]{21, 39}, ScoreboardLayout.pageRange(39, 9)); // clamped to the last page
        assertArrayEquals(new int[]{0, 21}, ScoreboardLayout.pageRange(39, -4));
        assertArrayEquals(new int[]{0, 0}, ScoreboardLayout.pageRange(0, 0));
        assertEquals(1, ScoreboardLayout.clampPage(5, 39));
    }

    @Test
    @DisplayName("category submenu size follows the row count: 27 / 36 / 54 (45 has no room for a third row)")
    void submenuSizes() {
        for (int n = 1; n <= 7; n++) assertEquals(27, ScoreboardLayout.submenuSize(n), "n=" + n);
        for (int n = 8; n <= 14; n++) assertEquals(36, ScoreboardLayout.submenuSize(n), "n=" + n);
        for (int n = 15; n <= 21; n++) assertEquals(54, ScoreboardLayout.submenuSize(n), "n=" + n);
        assertEquals(54, ScoreboardLayout.submenuSize(40)); // overflow is cut, 21 is the most a submenu shows
        assertEquals(27, ScoreboardLayout.submenuSize(0));
    }

    @Test
    @DisplayName("submenu rows are centred (symmetric around column 4) and stay inside the work zone for 1..21 lines")
    void submenuSlotsCentred() {
        for (int n = 1; n <= 21; n++) {
            int size = ScoreboardLayout.submenuSize(n);
            int[] slots = ScoreboardLayout.submenuSlots(size, n);
            assertEquals(n, slots.length, "n=" + n);
            assertEquals(n, Arrays.stream(slots).distinct().count(), "unique n=" + n);

            Map<Integer, Set<Integer>> perRow = new TreeMap<>();
            for (int s : slots) {
                assertTrue(s >= ScoreboardLayout.workStart(size) && s < ScoreboardLayout.footerStart(size),
                        "n=" + n + " slot " + s + " outside the work zone of " + size);
                int col = s % 9;
                assertTrue(col >= 1 && col <= 7, "n=" + n + " slot " + s + " is on a side wall");
                perRow.computeIfAbsent(s / 9, k -> new TreeSet<>()).add(col);
            }
            assertEquals(ScoreboardLayout.workRows(size) >= perRow.size() ? perRow.size() : -1, perRow.size());
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (Set<Integer> cols : perRow.values()) {
                min = Math.min(min, cols.size());
                max = Math.max(max, cols.size());
                for (int c : cols) assertTrue(cols.contains(8 - c), "n=" + n + " row not symmetric: " + cols);
            }
            assertTrue(max - min <= 1, "n=" + n + " rows are unbalanced");
        }
        assertArrayEquals(new int[]{13}, ScoreboardLayout.submenuSlots(27, 1));
        assertArrayEquals(new int[]{10, 11, 12, 14, 15, 16}, ScoreboardLayout.submenuSlots(27, 6));
        assertEquals(0, ScoreboardLayout.submenuSlots(27, 0).length);
    }

    @Test
    @DisplayName("submenu frame: category icon in slot 0, no controls, Back + Close, glass restored where there is no extra button")
    void submenuFrame() {
        ScoreboardLayout.Role[] f = ScoreboardLayout.frame(36, 0, false, true);
        assertEquals(ScoreboardLayout.Role.PROFILE, f[0]);
        for (int s = 1; s <= 8; s++) assertEquals(ScoreboardLayout.Role.GLASS, f[s]);
        assertEquals(ScoreboardLayout.Role.GLASS, f[33]);
        assertEquals(ScoreboardLayout.Role.BACK, f[34]);
        assertEquals(ScoreboardLayout.Role.CLOSE, f[35]);
        assertEquals(34, ScoreboardLayout.backSlot(36));
        assertEquals(35, ScoreboardLayout.closeSlot(36));
    }

    @Test
    @DisplayName("category row is centred when there are fewer than 7 categories")
    void fewCategories() {
        assertArrayEquals(new int[]{40}, ScoreboardLayout.categorySlots(1));
        assertArrayEquals(new int[]{39, 41}, ScoreboardLayout.categorySlots(2));
        assertArrayEquals(new int[]{37, 38, 39, 40, 41, 42, 43}, ScoreboardLayout.categorySlots(9)); // extra categories are not shown
    }

    @Test
    @DisplayName("KdRatio never divides by zero and uses a dot decimal separator")
    void kdRatio() {
        assertEquals("2.50", me.lovelace.loveTweaks.placeholder.KdRatio.format(5, 2));
        assertEquals("7.00", me.lovelace.loveTweaks.placeholder.KdRatio.format(7, 0));
        assertEquals("0.00", me.lovelace.loveTweaks.placeholder.KdRatio.format(0, 0));
        assertEquals("0.33", me.lovelace.loveTweaks.placeholder.KdRatio.format(1, 3));
    }
}

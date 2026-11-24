package me.lovelace.loveTweaks.repair;

import me.lovelace.loveTweaks.utils.GuiItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The craftsman's counter: one NPC for repairs, the anchor master (LoveClaims) and the clan banner (LoveClans).
 * A root menu (gui_gen: 27 slots, slot 0 the NPC, the sections centred in the work row, Close at the end of the
 * footer). The other plugins are reached through their own commands, which accept the call for a few seconds after
 * this hub sets the {@code craftsman_hub_until} metadata (no shared classes, no jitpack coupling).
 */
final class CraftsmanHub implements Listener {

    static final String HUB_KEY = "craftsman_hub_until";
    private static final int CLOSE = 26;

    private final JavaPlugin plugin;
    private final Blacksmith smith;

    /** One entry of the work row. */
    private record Section(String material, String nameKey, String loreKey, Consumer<Player> action) {
    }

    static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final List<Section> sections;
        private final int[] slots;

        Menu(List<Section> sections, int[] slots) {
            this.sections = sections;
            this.slots = slots;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    CraftsmanHub(JavaPlugin plugin, Blacksmith smith) {
        this.plugin = plugin;
        this.smith = smith;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("blacksmith.hub.enabled", true);
    }

    private boolean pluginOn(String key) {
        String name = plugin.getConfig().getString("blacksmith.hub." + key + ".plugin", "");
        return !name.isBlank() && Bukkit.getPluginManager().isPluginEnabled(name)
                && !plugin.getConfig().getString("blacksmith.hub." + key + ".command", "").isBlank();
    }

    /** Sections in order; repair always, the others only when their plugin is on the server. */
    List<Section> sections() {
        List<Section> out = new ArrayList<>();
        out.add(new Section("ANVIL", "hub-repair", "hub-repair-lore", smith::open));
        if (pluginOn("anchors")) out.add(new Section("LODESTONE", "hub-anchors", "hub-anchors-lore", p -> run(p, "anchors")));
        if (pluginOn("banner")) out.add(new Section("WHITE_BANNER", "hub-banner", "hub-banner-lore", p -> run(p, "banner")));
        return out;
    }

    /** True when the NPC should show the hub: it is on and there is something besides repair to choose. */
    boolean shouldOpen() {
        return enabled() && sections().size() > 1;
    }

    /** Slots of {@code count} buttons centred in the work row of a 27-slot menu (gui_gen RULE 6). */
    static int[] centered(int count) {
        return switch (count) {
            case 1 -> new int[]{13};
            case 2 -> new int[]{12, 14};
            case 3 -> new int[]{11, 13, 15};
            case 4 -> new int[]{10, 12, 14, 16};
            default -> new int[]{10, 11, 12, 13, 14, 15, 16};
        };
    }

    void open(Player player) {
        List<Section> sections = sections();
        int[] slots = centered(Math.min(sections.size(), 7));
        Menu menu = new Menu(sections, slots);
        player.closeInventory();
        menu.inventory = Bukkit.createInventory(menu, 27, GuiItemUtil.colorize(player, text("hub-title")));
        ItemStack glass = GuiItemUtil.buildItem(player, "GRAY_STAINED_GLASS_PANE", " ", List.of());
        for (int i = 0; i < 9; i++) menu.inventory.setItem(i, glass);
        for (int i = 18; i < 27; i++) menu.inventory.setItem(i, glass);
        menu.inventory.setItem(0, GuiItemUtil.buildItem(player, "ANVIL", text("hub-npc"), List.of(text("hub-npc-lore"))));
        for (int i = 0; i < slots.length && i < sections.size(); i++) {
            Section s = sections.get(i);
            menu.inventory.setItem(slots[i], GuiItemUtil.buildItem(player, s.material(), text(s.nameKey()),
                    List.of(text(s.loreKey()), "", text("hub-click"))));
        }
        menu.inventory.setItem(CLOSE, GuiItemUtil.buildItem(player, "BARRIER", text("close"), List.of()));
        player.openInventory(menu.inventory);
    }

    private String text(String key) {
        return plugin.getConfig().getString("blacksmith.text." + key, key);
    }

    /** Runs the other plugin's command for the player, with the hub's short permission window set. */
    private void run(Player player, String key) {
        String command = plugin.getConfig().getString("blacksmith.hub." + key + ".command", "");
        if (command.isBlank()) return;
        long until = System.currentTimeMillis() + 5_000L;
        player.setMetadata(HUB_KEY, new FixedMetadataValue(plugin, until));
        player.closeInventory();
        try {
            player.performCommand(command);
        } finally {
            // the command runs synchronously; the window only has to cover this call
            player.removeMetadata(HUB_KEY, plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        for (int i = 0; i < menu.slots.length && i < menu.sections.size(); i++) {
            if (menu.slots[i] == slot && event.getClick() == org.bukkit.event.inventory.ClickType.LEFT) {
                menu.sections.get(i).action().accept(player);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}

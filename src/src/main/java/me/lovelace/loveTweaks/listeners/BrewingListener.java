package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Iterator;

/**
 * Обработчик отключения ванильных варочных стоек и зельеварения:
 * 1. Отключение крафта всех зелий в варочной стойке (запрет варки, воронок, кликов и открытия ванильного интерфейса).
 * 2. Отключение ванильного крафта варочной стойки на верстаке.
 * 3. Отключение появления ванильных варочных стоек в мире (генерация чанков/структур, сундуки с лутом, торговля жителей, установка блоков).
 * 4. Отключение естественного появления и выпадения зелий в мире (лут сундуков, дроп с мобов/ведьм, бартер пиглинов, рыбалка, жители).
 *
 * Полностью интегрирован с плагином LoveBrew: кастомные варочные аппараты LoveBrew (PDC lovebrew, кастомные имена)
 * и напитки LoveBrew НЕ блокируются, не удаляются и работают в штатном режиме.
 */
public class BrewingListener implements Listener {

    private final LoveTweaks plugin;

    public BrewingListener(LoveTweaks plugin) {
        this.plugin = plugin;
        removeBrewingStandRecipes();
    }

    /**
     * Удаляет ванильные рецепты крафта варочной стойки из реестра сервера.
     */
    public void removeBrewingStandRecipes() {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandCraft()) {
            return;
        }

        try {
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe recipe = it.next();
                if (recipe != null && recipe.getResult().getType() == Material.BREWING_STAND && !isLoveBrewItem(recipe.getResult())) {
                    if (recipe instanceof Keyed keyed) {
                        Bukkit.removeRecipe(keyed.getKey());
                    } else {
                        it.remove();
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось удалить рецепты варочной стойки: " + t.getMessage());
        }
    }

    // ─── Проверка интеграции с LoveBrew и LoveTweaks ──────────────────────────

    /**
     * Проверяет, является ли блок кастомным варочным аппаратом LoveBrew.
     */
    public static boolean isLoveBrewBlock(Block block) {
        if (block == null) return false;
        return isLoveBrewState(block.getState());
    }

    /**
     * Проверяет состояние тайл-энтити на принадлежность к LoveBrew.
     */
    public static boolean isLoveBrewState(BlockState state) {
        if (state instanceof TileState tile) {
            var pdc = tile.getPersistentDataContainer();
            for (NamespacedKey key : pdc.getKeys()) {
                String ns = key.getNamespace().toLowerCase(java.util.Locale.ROOT);
                String k = key.getKey().toLowerCase(java.util.Locale.ROOT);
                if (ns.contains("lovebrew") || ns.contains("lovebrewing") || k.contains("lovebrew") || k.contains("brewing_stand") || k.contains("brewing_tier") || k.contains("barrel")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Проверяет, является ли предмет кастомным аппаратом/напитком/предметом LoveBrew.
     */
    public static boolean isLoveBrewItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        var pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            String ns = key.getNamespace().toLowerCase(java.util.Locale.ROOT);
            String k = key.getKey().toLowerCase(java.util.Locale.ROOT);
            if (ns.contains("lovebrew") || ns.contains("lovebrewing")
                || k.contains("lovebrew") || k.contains("brewing_stand") || k.contains("brewing_tier")
                || k.contains("beverage") || k.contains("barrel") || k.contains("glassware")
                || k.contains("recipe_id") || k.contains("coin_value")) {
                return true;
            }
        }

        // ItemsAdder custom stack check
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
                if (customStack != null) {
                    String namespacedId = (String) customStackClass.getMethod("getNamespacedID").invoke(customStack);
                    if (namespacedId != null) {
                        String lowerId = namespacedId.toLowerCase(java.util.Locale.ROOT);
                        if (lowerId.contains("lovebrew") || lowerId.startsWith("voidcore:") || lowerId.contains("brewing_stand") || lowerId.contains("barrel") || lowerId.contains("drink") || lowerId.contains("beer") || lowerId.contains("wine") || lowerId.contains("mead") || lowerId.contains("cider")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Display name / lore check
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("lovebrew") || name.contains("варочн") || name.contains("варилк")
                || name.contains("бочка") || name.contains("бокал") || name.contains("кружка")
                || name.contains("кубок") || name.contains("колба") || name.contains("чарка")
                || name.contains("свиток") || name.contains("монета")) {
                return true;
            }
        }

        if (meta.hasLore()) {
            java.util.List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    String lower = line.toLowerCase(java.util.Locale.ROOT);
                    if (lower.contains("варочн") || lower.contains("варк") || lower.contains("скорость варки")
                        || lower.contains("выдержк") || lower.contains("пивовар") || lower.contains("настойк")
                        || lower.contains("напиток") || lower.contains("lovebrew")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Проверяет, является ли предмет ванильным зельем или стрелой с эффектом
     * (исключая кастомные зелья очищения LoveTweaks и напитки LoveBrew).
     */
    public static boolean isVanillaPotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material mat = item.getType();
        if (mat != Material.POTION && mat != Material.SPLASH_POTION && mat != Material.LINGERING_POTION && mat != Material.TIPPED_ARROW) {
            return false;
        }

        // Защита кастомного зелья очищения LoveTweaks
        if (me.lovelace.loveTweaks.items.PurificationPotion.isPurificationPotion(item)) {
            return false;
        }

        // Защита кастомных напитков и предметов LoveBrew
        if (isLoveBrewItem(item)) {
            return false;
        }

        if (mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION || mat == Material.TIPPED_ARROW) {
            return true;
        }

        if (mat == Material.POTION) {
            if (item.getItemMeta() instanceof PotionMeta potionMeta) {
                if (potionMeta.hasCustomEffects()) {
                    return true;
                }
                PotionType baseType = potionMeta.getBasePotionType();
                if (baseType != null && baseType != PotionType.WATER) {
                    return true;
                }
            } else {
                return true;
            }
        }

        return false;
    }

    // ─── 1. Отключение варки зелий в варочной стойке ───────────────────────────

    /**
     * Блокирует завершение ванильной варки зелий в любой варочной стойке.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableBrewing()) {
            event.setCancelled(true);
        }
    }

    public static boolean isLoveBrewPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("LoveBrew") || Bukkit.getPluginManager().isPluginEnabled("LoveBrewing");
    }

    /**
     * Блокирует открытие ванильного интерфейса варочной стойки.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING && !(event.getInventory() instanceof BrewerInventory)) {
            return;
        }

        if (plugin.getLoveTweaksConfig().isDisableBrewing() || plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player && !isLoveBrewPresent()) {
                player.sendMessage(GuiItemUtil.colorize(player, plugin.getLoveTweaksConfig().getDisabledBrewingMessage()));
            }
        }
    }

    /**
     * Блокирует клики и манипуляции с предметами в инвентаре варочной стойки.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.BREWING || event.getInventory() instanceof BrewerInventory) {
            if (plugin.getLoveTweaksConfig().isDisableBrewing() || plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Блокирует перетаскивание предметов в инвентарь варочной стойки.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() == InventoryType.BREWING || event.getInventory() instanceof BrewerInventory) {
            if (plugin.getLoveTweaksConfig().isDisableBrewing() || plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Блокирует перемещение предметов воронками/выбрасывателями в варочную стойку.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() == InventoryType.BREWING || event.getDestination() instanceof BrewerInventory) {
            if (plugin.getLoveTweaksConfig().isDisableBrewing() || plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 2. Отключение крафта варочной стойки ─────────────────────────────────

    /**
     * Скрывает результат крафта ванильной варочной стойки на верстаке.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandCraft()) {
            return;
        }

        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.BREWING_STAND && !isLoveBrewItem(result)) {
            event.getInventory().setResult(null);
        } else if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.BREWING_STAND && !isLoveBrewItem(event.getRecipe().getResult())) {
            event.getInventory().setResult(null);
        }
    }

    /**
     * Блокирует факт крафта ванильной варочной стойки и отправляет сообщение игроку.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandCraft()) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() == Material.BREWING_STAND && !isLoveBrewItem(result)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(GuiItemUtil.colorize(player, plugin.getLoveTweaksConfig().getDisabledBrewingMessage()));
            }
        }
    }

    // ─── 3. Отключение появления варочной стойки в мире ───────────────────────

    /**
     * Блокирует установку ванильной варочной стойки игроком (аппараты LoveBrew разрешены).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        if (event.getBlockPlaced().getType() == Material.BREWING_STAND) {
            if (isLoveBrewPresent() || isLoveBrewItem(event.getItemInHand())) {
                return; // Разрешаем установку варочного аппарата LoveBrew
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(GuiItemUtil.colorize(event.getPlayer(), plugin.getLoveTweaksConfig().getDisabledBrewingMessage()));
        }
    }

    /**
     * Блокирует раздачу/выбрасывание ванильной варочной стойки раздатчиками.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        if (event.getItem().getType() == Material.BREWING_STAND && !isLoveBrewItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует открытие ванильной варочной стойки в мире по ПКМ (аппараты LoveBrew обрабатываются LoveBrew).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld() && !plugin.getLoveTweaksConfig().isDisableBrewing()) {
            return;
        }

        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BREWING_STAND) {
            return;
        }

        // Если это варочный аппарат LoveBrew или LoveBrew активен на сервере — даём LoveBrew полностью обработать взаимодействие
        if (isLoveBrewPresent() || isLoveBrewBlock(event.getClickedBlock())) {
            return;
        }

        // Блокируем взаимодействие с ванильной стойкой
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(GuiItemUtil.colorize(event.getPlayer(), plugin.getLoveTweaksConfig().getDisabledBrewingMessage()));
        }
    }

    /**
     * Очищает ванильные варочные стойки при генерации и загрузке чанков (деревни, иглу, корабли Края).
     * Аппараты LoveBrew не затрагиваются.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        for (BlockState state : event.getChunk().getTileEntities()) {
            if ((state.getType() == Material.BREWING_STAND || state instanceof BrewingStand) && !isLoveBrewState(state)) {
                state.getBlock().setType(Material.AIR, false);
            }
        }
    }

    /**
     * Удаляет варочные стойки и ванильные зелья из сгенерированного лута в сундуках мира.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLootGenerate(LootGenerateEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            event.getLoot().removeIf(item -> item != null && item.getType() == Material.BREWING_STAND && !isLoveBrewItem(item));
        }
        if (plugin.getLoveTweaksConfig().isDisablePotionNaturalDrops()) {
            event.getLoot().removeIf(BrewingListener::isVanillaPotion);
        }
    }

    /**
     * Предотвращает выпадение ванильной варочной стойки при разрушении блока.
     * Разрушение аппаратов LoveBrew обрабатывается самим LoveBrew.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        if (event.getBlock().getType() == Material.BREWING_STAND) {
            if (isLoveBrewPresent() || isLoveBrewBlock(event.getBlock())) {
                return; // LoveBrew сам обработает дроп своего кастомного предмета
            }
            event.setDropItems(false);
        }
    }

    /**
     * Блокирует выпадение ванильной варочной стойки как обычного предмета.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        event.getItems().removeIf(item -> item.getItemStack().getType() == Material.BREWING_STAND && !isLoveBrewItem(item.getItemStack()));
    }

    /**
     * Предотвращает появление ванильных предметов варочной стойки.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            return;
        }

        if (event.getEntity().getItemStack().getType() == Material.BREWING_STAND && !isLoveBrewItem(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * Блокирует торговлю варочными стойками и ванильными зельями у жителей деревни.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (plugin.getLoveTweaksConfig().isDisableBrewingStandWorld()) {
            if (event.getRecipe().getResult().getType() == Material.BREWING_STAND && !isLoveBrewItem(event.getRecipe().getResult())) {
                event.setCancelled(true);
                return;
            }
        }
        if (plugin.getLoveTweaksConfig().isDisablePotionNaturalDrops()) {
            if (isVanillaPotion(event.getRecipe().getResult())) {
                event.setCancelled(true);
            }
        }
    }

    // ─── 4. Отключение естественного появления и выпадения зелий ──────────────

    /**
     * Предотвращает выпадение зелий при смерти существ (ведьмы и др.).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisablePotionNaturalDrops()) {
            return;
        }

        event.getDrops().removeIf(BrewingListener::isVanillaPotion);
    }

    /**
     * Удаляет зелья из результатов бартера с пиглинами.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPiglinBarter(PiglinBarterEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisablePotionNaturalDrops()) {
            return;
        }

        event.getOutcome().removeIf(BrewingListener::isVanillaPotion);
    }

    /**
     * Блокирует вылавливание зелий при рыбалке.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!plugin.getLoveTweaksConfig().isDisablePotionNaturalDrops()) {
            return;
        }

        if (event.getCaught() instanceof Item caughtItem) {
            if (isVanillaPotion(caughtItem.getItemStack())) {
                caughtItem.remove();
                event.setCancelled(true);
            }
        }
    }
}

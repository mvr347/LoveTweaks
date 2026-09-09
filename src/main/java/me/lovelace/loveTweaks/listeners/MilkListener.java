package me.lovelace.loveTweaks.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.items.PurificationPotion;
import me.lovelace.loveTweaks.managers.PurificationPotionConfig;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.potion.PotionEffect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Обработчик употребления молока и кастомного "Зелья очищения".
 * Поддерживает настраиваемый кулдаун (30 сек по умолчанию), очищение всех эффектов,
 * текстуры ItemsAdder и защищенную от утечек память на базе Caffeine Cache.
 */
public class MilkListener implements Listener {

    private final LoveTweaks plugin;
    private final NamespacedKey recipeKey;

    // Время окончания кулдауна для игрока (UUID -> System.currentTimeMillis() + cooldownMs)
    private final Cache<UUID, Long> cooldowns = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public MilkListener(LoveTweaks plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "purification_potion");

        registerPurificationRecipe();
    }

    public void reload() {
        registerPurificationRecipe();
        cooldowns.invalidateAll();
    }

    public void clearAll() {
        cooldowns.invalidateAll();
    }

    /**
     * Регистрирует бесформенный рецепт крафта зелья очищения.
     */
    public void registerPurificationRecipe() {
        try {
            plugin.getServer().removeRecipe(recipeKey);
        } catch (Throwable ignored) {}

        PurificationPotionConfig cfg = plugin.getLoveTweaksConfig().getPurificationPotionConfig();
        if (!cfg.isRecipeEnabled()) {
            return;
        }

        try {
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, PurificationPotion.create());
            List<String> ingredients = cfg.recipeIngredients();
            if (ingredients == null || ingredients.isEmpty()) {
                recipe.addIngredient(Material.GHAST_TEAR);
                recipe.addIngredient(Material.FERMENTED_SPIDER_EYE);
                recipe.addIngredient(Material.NETHER_WART);
            } else {
                for (String ingName : ingredients) {
                    Material mat = Material.matchMaterial(ingName.toUpperCase());
                    if (mat != null) {
                        recipe.addIngredient(mat);
                    }
                }
            }
            plugin.getServer().addRecipe(recipe);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось зарегистрировать рецепт зелья очищения: " + t.getMessage());
        }
    }

    /**
     * Создаёт зелье очищения через фабрику {@link PurificationPotion}.
     */
    public ItemStack createPurificationPotion() {
        return PurificationPotion.create();
    }

    public boolean isPurificationPotion(ItemStack item) {
        return PurificationPotion.isPurificationPotion(item);
    }

    /**
     * Возвращает оставшееся время кулдауна в секундах (0.0 если кулдауна нет).
     */
    public double getRemainingCooldown(Player player) {
        Long expireTime = cooldowns.getIfPresent(player.getUniqueId());
        if (expireTime == null) return 0.0;
        long now = System.currentTimeMillis();
        if (now >= expireTime) {
            cooldowns.invalidate(player.getUniqueId());
            return 0.0;
        }
        return (expireTime - now) / 1000.0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (!isPurificationPotion(item)) {
            return;
        }

        Player player = event.getPlayer();
        double remaining = getRemainingCooldown(player);

        // Если это зелье или съедобный предмет, но кулдаун активен — блокируем анимацию питья
        if (item.getType() == Material.POTION || item.getType() == Material.MILK_BUCKET || item.getType().isEdible()) {
            if (remaining > 0.0) {
                event.setCancelled(true);
                sendCooldownMessage(player, remaining);
            }
            return;
        }

        // Если кастомный предмет ItemsAdder не является ванильным съедобным материалом (Instant use)
        event.setCancelled(true);
        if (remaining > 0.0) {
            sendCooldownMessage(player, remaining);
            return;
        }

        applyPurification(player, item, event.getHand());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if (item.getType() == Material.MILK_BUCKET) {
            if (plugin.getLoveTweaksConfig().isDisableMilk()) {
                event.setCancelled(true);
            }
            return;
        }

        if (isPurificationPotion(item)) {
            event.setCancelled(true);

            double remaining = getRemainingCooldown(player);
            if (remaining > 0.0) {
                sendCooldownMessage(player, remaining);
                return;
            }

            EquipmentSlot hand = isPurificationPotion(player.getInventory().getItemInMainHand()) ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
            applyPurification(player, item, hand);
        }
    }

    private void applyPurification(Player player, ItemStack item, EquipmentSlot hand) {
        PurificationPotionConfig cfg = plugin.getLoveTweaksConfig().getPurificationPotionConfig();

        // 1. Устанавливаем кулдаун
        int cooldownSec = cfg.cooldownSeconds();
        if (cooldownSec > 0) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSec * 1000L));
        }

        // 2. Снимаем предмет из руки и выдаем пустую бутылочку при необходимости
        consumeFromHand(player, hand);
        if (item.getType() == Material.POTION) {
            giveEmptyBottle(player);
        }

        // 3. Очищаем ВСЕ эффекты (и положительные, и отрицательные)
        clearAllEffects(player);

        // 4. Отправляем сообщение об очищении
        String msg = cfg.purificationMessage();
        if (msg != null && !msg.isBlank()) {
            player.sendMessage(GuiItemUtil.colorize(player, msg));
        }

        // 5. Визуальные эффекты и звук
        try {
            player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0.03);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.0, 0), 15, 0.4, 0.5, 0.4, 0.5);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "Could not play purification effects: " + t.getMessage());
        }
    }

    private void sendCooldownMessage(Player player, double remainingSeconds) {
        PurificationPotionConfig cfg = plugin.getLoveTweaksConfig().getPurificationPotionConfig();
        String formatted = String.format(Locale.ROOT, "%.1f", remainingSeconds);
        String msg = cfg.cooldownMessage()
                .replace("<seconds>", formatted)
                .replace("<time>", formatted);
        player.sendMessage(GuiItemUtil.colorize(player, msg));

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "Could not play error sound: " + t.getMessage());
        }
    }

    private void consumeFromHand(Player player, EquipmentSlot slot) {
        PlayerInventory inv = player.getInventory();
        if (slot == EquipmentSlot.OFF_HAND && isPurificationPotion(inv.getItemInOffHand())) {
            decreaseOrClear(inv, inv.getItemInOffHand(), EquipmentSlot.OFF_HAND);
        } else if (isPurificationPotion(inv.getItemInMainHand())) {
            decreaseOrClear(inv, inv.getItemInMainHand(), EquipmentSlot.HAND);
        } else if (isPurificationPotion(inv.getItemInOffHand())) {
            decreaseOrClear(inv, inv.getItemInOffHand(), EquipmentSlot.OFF_HAND);
        }
    }

    private void decreaseOrClear(PlayerInventory inventory, ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.getType() == Material.AIR) return;
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            if (slot == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(stack);
            } else {
                inventory.setItemInMainHand(stack);
            }
        } else {
            if (slot == EquipmentSlot.OFF_HAND) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItemInMainHand(null);
            }
        }
    }

    private void giveEmptyBottle(Player player) {
        ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
        PlayerInventory inventory = player.getInventory();

        if (inventory.firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), bottle);
        } else {
            inventory.addItem(bottle);
        }
    }

    private void clearAllEffects(Player player) {
        PurificationPotionConfig cfg = plugin != null && plugin.getLoveTweaksConfig() != null
                ? plugin.getLoveTweaksConfig().getPurificationPotionConfig()
                : null;
        List<PotionEffect> activeEffects = new ArrayList<>(player.getActivePotionEffects());
        for (PotionEffect effect : activeEffects) {
            if (cfg != null && cfg.isEffectIgnored(effect.getType())) {
                continue;
            }
            player.removePotionEffect(effect.getType());
        }
    }
}


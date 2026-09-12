package me.lovelace.loveTweaks.enchantments;

import me.lovelace.loveTweaks.LoveTweaks;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomEnchantListener implements Listener {

    private final LoveTweaks plugin;
    private final CustomEnchantManager manager;

    // Track combo for Zhazhda Krovi
    private final Map<UUID, Integer> hitCombo = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();

    // Track last backstab for Ten (Shadow)
    private final Map<UUID, Long> lastBackstabTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastBackstabTarget = new ConcurrentHashMap<>();

    public CustomEnchantListener(@NotNull LoveTweaks plugin, @NotNull CustomEnchantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) {
            return;
        }

        // 1. Изнурение (Mining Fatigue on hit)
        int iznurenie = manager.getLevel(weapon, CustomEnchantType.IZNURENIE);
        if (iznurenie > 0) {
            int amplifier = iznurenie - 1;
            int duration = iznurenie == 1 ? 60 : 70; // 3.0s / 3.5s
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, amplifier));
        }

        // 2. Сокрушение (Armor ignoring bonus damage)
        int socrushenie = manager.getLevel(weapon, CustomEnchantType.SOCRUSHENIE);
        if (socrushenie > 0) {
            double ignore = socrushenie == 1 ? 2.0 : 3.0;
            event.setDamage(event.getDamage() + (ignore * 0.4));
        }

        // 3. Раскол (Shield strike weakness / slowness / freeze)
        int raskol = manager.getLevel(weapon, CustomEnchantType.RASKOL);
        if (raskol > 0 && victim instanceof Player target && target.isBlocking()) {
            if (raskol == 1) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            } else {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1));
                target.setFreezeTicks(Math.max(target.getFreezeTicks(), 40) + 40);
            }
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f);
        }

        // 4. Удар исподтишка (Backstab bonus damage)
        int backstab = manager.getLevel(weapon, CustomEnchantType.UDAR_ISPODTISHKA);
        boolean backstabHit = isBackstab(attacker, victim);
        if (backstabHit) {
            lastBackstabTime.put(attacker.getUniqueId(), System.currentTimeMillis());
            lastBackstabTarget.put(attacker.getUniqueId(), victim.getUniqueId());

            if (backstab > 0) {
                double multiplier = backstab == 1 ? 1.4 : 1.7;
                event.setDamage(event.getDamage() * multiplier);

                if (backstab == 2) {
                    event.setDamage(event.getDamage() + 0.8);
                }

                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.2, 0.2, 0.2, 0.1);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            }
        }

        // 5. Кровопийца (Lifesteal on critical strike, mutually exclusive with Zhazhda Krovi)
        int krovopiyca = manager.getLevel(weapon, CustomEnchantType.KROVOPIYCA);
        int zhazhda = manager.getLevel(weapon, CustomEnchantType.ZHAZHDA_KROVI);

        if (krovopiyca > 0 && zhazhda <= 0 && event.isCritical()) {
            AttributeInstance maxHpAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
            attacker.setHealth(Math.min(maxHp, attacker.getHealth() + 1.0)); // 0.5 hearts = 1.0 HP
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0.05);
        }

        // 6. Жажда крови (Hit combo grants Regeneration, mutually exclusive with Krovopiyca)
        if (zhazhda > 0 && krovopiyca <= 0) {
            UUID uuid = attacker.getUniqueId();
            long now = System.currentTimeMillis();

            if (now - lastHit.getOrDefault(uuid, 0L) > 2500) {
                hitCombo.put(uuid, 0);
            }

            int combo = hitCombo.getOrDefault(uuid, 0) + 1;
            hitCombo.put(uuid, combo);
            lastHit.put(uuid, now);

            if (combo >= 4) {
                int amplifier = zhazhda - 1;
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, amplifier));
                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
                hitCombo.put(uuid, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        handleDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            handleDeath(event.getEntity());
        }
    }

    private void handleDeath(LivingEntity victim) {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        int ten = manager.getLevel(weapon, CustomEnchantType.TEN);
        if (ten <= 0) {
            return;
        }

        // Check if killed via backstab (either directly or within last 2 seconds)
        Long backstabTime = lastBackstabTime.get(killer.getUniqueId());
        UUID targetId = lastBackstabTarget.get(killer.getUniqueId());

        boolean wasBackstab = (targetId != null && targetId.equals(victim.getUniqueId())
                && backstabTime != null && (System.currentTimeMillis() - backstabTime) < 2000)
                || isBackstab(killer, victim);

        if (wasBackstab) {
            int duration = ten == 1 ? 60 : 100; // 3.0s / 5.0s
            killer.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
            killer.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, killer.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
            killer.getWorld().playSound(killer.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack leggings = player.getInventory().getLeggings();
        if (leggings == null || leggings.getType().isAir()) {
            return;
        }

        int prityazhenie = manager.getLevel(leggings, CustomEnchantType.PRITYAZHENIE);
        if (prityazhenie <= 0) {
            return;
        }

        double radius = prityazhenie == 1 ? 3.0 : 5.0;
        Location playerLoc = player.getLocation();

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Item item && item.isValid() && !item.isDead()) {
                if (item.getPickupDelay() <= 20) {
                    Vector diff = playerLoc.toVector().subtract(item.getLocation().toVector());
                    if (diff.lengthSquared() > 0.04) {
                        Vector direction = diff.normalize().multiply(0.35);
                        item.setVelocity(direction);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    @SuppressWarnings("deprecation")
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return;
        }

        Map<CustomEnchantType, Integer> rightEnchants = manager.getEnchantments(right);
        if (rightEnchants.isEmpty()) {
            // Check if left has custom enchantments and right is repairing it
            if (manager.hasAnyCustomEnchant(left)) {
                ItemStack result = event.getResult();
                if (result != null) {
                    // Reapply custom enchantments from left to result so they aren't lost
                    for (Map.Entry<CustomEnchantType, Integer> entry : manager.getEnchantments(left).entrySet()) {
                        manager.applyEnchantment(result, entry.getKey(), entry.getValue());
                    }
                    event.setResult(result);
                }
            }
            return;
        }

        // Determine if left is an applicable target
        boolean isBookToBook = manager.isBook(left) && manager.isBook(right);
        boolean isTargetValid = isBookToBook
                || (manager.isDagger(left) && rightEnchants.keySet().stream().allMatch(t -> t.getTarget() == CustomEnchantType.Target.DAGGER))
                || (manager.isLeggings(left) && rightEnchants.keySet().stream().allMatch(t -> t.getTarget() == CustomEnchantType.Target.LEGGINGS));

        if (!isTargetValid) {
            return;
        }

        ItemStack base = event.getResult() != null ? event.getResult().clone() : left.clone();
        Map<CustomEnchantType, Integer> leftEnchants = manager.getEnchantments(left);
        Map<CustomEnchantType, Integer> combined = new HashMap<>(leftEnchants);

        int cost = 1;
        try {
            if (event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView) {
                cost = Math.max(1, anvilView.getRepairCost());
            } else {
                cost = Math.max(1, inv.getRepairCost());
            }
        } catch (Throwable ignored) {
            cost = Math.max(1, inv.getRepairCost());
        }
        boolean modified = false;

        for (Map.Entry<CustomEnchantType, Integer> entry : rightEnchants.entrySet()) {
            CustomEnchantType type = entry.getKey();
            int rightLevel = entry.getValue();

            // Check if applicable
            if (!manager.isApplicable(type, base)) {
                continue;
            }

            // Check mutual exclusion with already existing enchants on left item
            boolean conflict = false;
            for (CustomEnchantType existing : combined.keySet()) {
                if (manager.areMutuallyExclusive(type, existing)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) {
                continue;
            }

            int leftLevel = combined.getOrDefault(type, 0);
            int resultLevel;
            if (leftLevel == 0) {
                resultLevel = rightLevel;
            } else if (leftLevel == rightLevel) {
                resultLevel = Math.min(type.getMaxLevel(), leftLevel + 1);
            } else {
                resultLevel = Math.max(leftLevel, rightLevel);
            }

            combined.put(type, resultLevel);
            cost += resultLevel * 2;
            modified = true;
        }

        if (modified) {
            for (Map.Entry<CustomEnchantType, Integer> entry : combined.entrySet()) {
                manager.applyEnchantment(base, entry.getKey(), entry.getValue());
            }
            event.setResult(base);
            final int finalCost = cost;
            try {
                if (event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView) {
                    anvilView.setRepairCost(finalCost);
                } else {
                    inv.setRepairCost(finalCost);
                }
            } catch (Throwable ignored) {
                inv.setRepairCost(finalCost);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (event.getView() instanceof org.bukkit.inventory.view.AnvilView anvilView) {
                        anvilView.setRepairCost(finalCost);
                    } else {
                        inv.setRepairCost(finalCost);
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!plugin.getLoveTweaksConfig().isCustomEnchantmentsEnabled()) {
            return;
        }
        ItemStack result = event.getResult();
        if (result != null && manager.hasAnyCustomEnchant(result)) {
            manager.removeAllCustomEnchantments(result);
            event.setResult(result);
        }
    }

    public static boolean isBackstab(@NotNull Player attacker, @NotNull LivingEntity victim) {
        Vector diff = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (diff.lengthSquared() < 1.0E-4) {
            return false;
        }
        Vector toVictim = diff.normalize();
        Vector victimDirection = victim.getLocation().getDirection().normalize();
        return toVictim.dot(victimDirection) > 0.5;
    }
}

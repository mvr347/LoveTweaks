package me.lovelace.loveTweaks.listeners;

import me.lovelace.loveTweaks.LoveTweaks;
import me.lovelace.loveTweaks.utils.GuiItemUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Выброшенные предметы могут потеряться (исчезнуть) или, если у них есть
 * прочность, сломаться при падении.
 */
public class ItemDropLossListener implements Listener {

    private final LoveTweaks plugin;

    public ItemDropLossListener(LoveTweaks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getLoveTweaksConfig().isItemDropLossEnabled()) {
            return;
        }

        Item itemEntity = event.getItemDrop();
        ItemStack stack = itemEntity.getItemStack();
        Player player = event.getPlayer();
        double multiplier = terriblePolitenessMultiplier(player);

        if (hasDurability(stack)) {
            if (roll(plugin.getLoveTweaksConfig().getItemDropBreakChance() * multiplier)) {
                damageOrBreakItem(player, itemEntity, stack);
            }
        } else {
            if (roll(plugin.getLoveTweaksConfig().getItemDropLossChance() * multiplier)) {
                handleItemOrStackLoss(player, itemEntity, stack, multiplier);
            }
        }
    }

    private void handleItemOrStackLoss(Player player, Item itemEntity, ItemStack stack, double multiplier) {
        int amount = stack.getAmount();
        if (amount <= 1) {
            loseItem(player, itemEntity);
            return;
        }

        // Выброшен стак / несколько предметов:
        // Очень маленький шанс (базово 1%) полного уничтожения всего стака
        double completeLossChance = plugin.getLoveTweaksConfig().getItemDropCompleteStackLossChance() * multiplier;
        if (roll(completeLossChance)) {
            loseItem(player, itemEntity);
            return;
        }

        // В остальных случаях теряется лишь небольшая часть предметов (от 1 до 4)
        int maxLost = Math.min(4, Math.max(1, amount / 4));
        int lostCount = ThreadLocalRandom.current().nextInt(1, maxLost + 1);
        int remaining = amount - lostCount;

        stack.setAmount(remaining);
        itemEntity.setItemStack(stack);

        Location location = itemEntity.getLocation();
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 0.9f, 0.6f);
        location.getWorld().spawnParticle(Particle.SMOKE, location, 8, 0.2, 0.2, 0.2, 0.02);

        String msg = plugin.getLoveTweaksConfig().getItemDropStackPartialLoseMessage()
                .replace("<count>", String.valueOf(lostCount));
        player.sendMessage(GuiItemUtil.colorize(msg));
    }

    private boolean hasDurability(ItemStack stack) {
        return stack.getType().getMaxDurability() > 0 && stack.getItemMeta() instanceof Damageable;
    }

    private boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * Множитель шанса потери/поломки для ступени вежливости "Ужасно" (0 из 0-6 у LoveBehavior).
     * 1.0 (без изменений), если LoveBehavior не установлен или игрок не на этой ступени.
     */
    private double terriblePolitenessMultiplier(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.social.BehaviorLevels.class)
                .filter(levels -> levels.politenessLevel(player.getUniqueId()) == 0)
                .map(levels -> plugin.getLoveTweaksConfig().getItemDropTerriblePolitenessMultiplier())
                .orElse(1.0);
    }

    private void damageOrBreakItem(Player player, Item itemEntity, ItemStack stack) {
        if (!(stack.getItemMeta() instanceof Damageable meta)) {
            breakItem(player, itemEntity);
            return;
        }

        int maxDurability = stack.getType().getMaxDurability();
        int currentDamage = meta.getDamage();
        int remainingDurability = maxDurability - currentDamage;

        // Случайный урон по прочности (от 5% до 25% макс. прочности, минимум 1-5 единиц)
        int minDmg = Math.max(1, (int) Math.round(maxDurability * 0.05));
        int maxDmg = Math.max(minDmg + 1, (int) Math.round(maxDurability * 0.25));
        int damageLoss = ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1);

        // Если прочности не хватает, чтобы пережить урон, или предмет уже критически сломан (<= 5% прочности)
        if (remainingDurability <= damageLoss || remainingDurability <= Math.max(2, (int) Math.round(maxDurability * 0.05))) {
            breakItem(player, itemEntity);
            return;
        }

        meta.setDamage(currentDamage + damageLoss);
        stack.setItemMeta(meta);
        itemEntity.setItemStack(stack);

        Location location = itemEntity.getLocation();
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, 0.6f, 1.6f);
        location.getWorld().spawnParticle(Particle.CRIT, location, 8, 0.15, 0.15, 0.15, 0.05);

        player.sendMessage(GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getItemDropDamageMessage()));
    }

    private void breakItem(Player player, Item itemEntity) {
        Location location = itemEntity.getLocation();
        itemEntity.remove();

        location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        location.getWorld().spawnParticle(Particle.CLOUD, location, 12, 0.2, 0.2, 0.2, 0);

        player.sendMessage(GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getItemDropBreakMessage()));
    }

    private void loseItem(Player player, Item itemEntity) {
        Location location = itemEntity.getLocation();
        itemEntity.remove();

        location.getWorld().playSound(location, Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
        location.getWorld().spawnParticle(Particle.SMOKE, location, 10, 0.2, 0.2, 0.2, 0.02);

        player.sendMessage(GuiItemUtil.colorize(plugin.getLoveTweaksConfig().getItemDropLoseMessage()));
    }
}

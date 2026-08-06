package me.lovelace.loveTweaks.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

import java.util.List;
import java.util.Optional;

/**
 * Общая проверка "игрок сейчас в бою", используемая всеми свитками телепортации (обычным
 * и координатным), чтобы нельзя было телепортироваться, убегая с PvP.
 *
 * Вынесена из {@code TeleportScrollManager}, чтобы координатный свиток не дублировал ту же
 * логику с нуля — оба менеджера используют один и тот же контракт "что значит быть в бою".
 */
public final class CombatUtil {

    private CombatUtil() {}

    /**
     * Если LoveCore установлен, спрашиваем его {@code CombatState} — он знает и про метку
     * стороннего боевого плагина, и про войну/осаду, чего одна метка не знает. Ядра нет или
     * служба ещё не поднялась — падаем на прежнюю проверку метки {@code in_combat}, которой
     * DeluxeCombat и большинство combat-плагинов помечают игроков.
     */
    public static boolean isInCombat(Player player) {
        if (Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                Optional<Boolean> fromCore = dev.lovelace.lovecore.api.LoveCore
                        .service(dev.lovelace.lovecore.api.combat.CombatState.class)
                        .map(state -> state.inCombat(player.getUniqueId()));
                if (fromCore.isPresent()) {
                    return fromCore.get();
                }
            } catch (Throwable ignored) {
                // Ядро есть, но служба ещё не поднялась или контракт изменился — падаем на метку.
            }
        }
        return hasCombatMetadata(player);
    }

    private static boolean hasCombatMetadata(Player player) {
        List<MetadataValue> values = player.getMetadata("in_combat");
        for (MetadataValue value : values) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}

package me.lovelace.loveTweaks.diagnostics;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;

/** Main-thread, read-only inspection of the native gates and loaded enchantment definition. */
final class NativeLungeState {
  static List<String> inspect(Player player) {
    var lines = new ArrayList<String>();
    try {
      Object handle = player.getClass().getMethod("getHandle").invoke(player);
      Object listener = handle.getClass().getField("connection").get(handle);
      Object item = call(handle, "getMainHandItem");
      Object weapon = call(handle, "getWeaponItem");
      Method cannot =
          handle.getClass().getMethod("cannotAttackWithItem", item.getClass(), int.class);
      lines.add(
          "NMS допуск сейчас: клиент загружен="
              + call(listener, "hasClientLoaded")
              + ", неподвижен="
              + call(handle, "isImmobile")
              + ", перезарядка запрещает STAB="
              + cannot.invoke(handle, item, 5)
              + ", оружие совпадает с рукой="
              + (weapon == item)
              + ", готовность Bukkit="
              + player.getAttackCooldown());
      Object component =
          Class.forName("net.minecraft.core.component.DataComponents")
              .getField("ENCHANTMENTS")
              .get(null);
      Object enchants =
          item.getClass()
              .getMethod("get", Class.forName("net.minecraft.core.component.DataComponentType"))
              .invoke(weapon, component);
      lines.add("NMS чары фактического оружия: " + bounded(String.valueOf(enchants)));
    } catch (ReflectiveOperationException | RuntimeException failure) {
      lines.add("NMS допуск недоступен: " + failure.getClass().getSimpleName());
    }
    try {
      var enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
      if (enchantment == null) {
        lines.add("В реестре отсутствует minecraft:lunge");
      } else {
        Object holder = call(enchantment, "getHolder");
        Object nativeEnchant = call(holder, "value");
        Object effectType =
            Class.forName("net.minecraft.world.item.enchantment.EnchantmentEffectComponents")
                .getField("POST_PIERCING_ATTACK")
                .get(null);
        var effects =
            (List<?>)
                nativeEnchant
                    .getClass()
                    .getMethod(
                        "getEffects",
                        Class.forName("net.minecraft.core.component.DataComponentType"))
                    .invoke(nativeEnchant, effectType);
        Object mainHand =
            Class.forName("net.minecraft.world.entity.EquipmentSlot")
                .getField("MAINHAND")
                .get(null);
        lines.add(
            "Реестр Lunge: post_piercing_attack="
                + effects.size()
                + ", активные слоты="
                + enchantment.getActiveSlotGroups()
                + ", основная рука разрешена="
                + nativeEnchant
                    .getClass()
                    .getMethod("matchingSlot", mainHand.getClass())
                    .invoke(nativeEnchant, mainHand));
        for (Object effect : effects.stream().limit(2).toList()) {
          lines.add("Загруженный эффект Lunge: " + bounded(String.valueOf(effect)));
        }
      }
    } catch (ReflectiveOperationException | RuntimeException failure) {
      lines.add("Чтение эффектов Lunge недоступно: " + failure.getClass().getSimpleName());
    }
    return List.copyOf(lines);
  }

  private static Object call(Object target, String name) throws ReflectiveOperationException {
    return target.getClass().getMethod(name).invoke(target);
  }

  private static String bounded(String value) {
    return value.length() > 1600 ? value.substring(0, 1600) + "…" : value;
  }
}

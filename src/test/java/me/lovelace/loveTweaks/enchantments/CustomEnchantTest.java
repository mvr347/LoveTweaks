package me.lovelace.loveTweaks.enchantments;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CustomEnchantTest {

    @Test
    @DisplayName("All 8 custom enchantments have correct IDs, targets, and max levels")
    void testEnchantmentMetadata() {
        assertEquals(8, CustomEnchantType.values().length);

        // 1. Изнурение
        CustomEnchantType iznurenie = CustomEnchantType.IZNURENIE;
        assertEquals("iznurenie", iznurenie.getId());
        assertEquals("Изнурение", iznurenie.getDisplayName());
        assertEquals(2, iznurenie.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, iznurenie.getTarget());
        assertNull(iznurenie.getMutuallyExclusiveWith());
        assertEquals("Накладывает Усталость I на 3 секунды", iznurenie.getDescription(1));
        assertEquals("Накладывает Усталость II на 3.5 секунды", iznurenie.getDescription(2));

        // 2. Сокрушение
        CustomEnchantType socrushenie = CustomEnchantType.SOCRUSHENIE;
        assertEquals("socrushenie", socrushenie.getId());
        assertEquals("Сокрушение", socrushenie.getDisplayName());
        assertEquals(2, socrushenie.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, socrushenie.getTarget());
        assertNull(socrushenie.getMutuallyExclusiveWith());
        assertEquals("Игнорирует 2 единицы брони", socrushenie.getDescription(1));
        assertEquals("Игнорирует 3 единицы брони", socrushenie.getDescription(2));

        // 3. Раскол
        CustomEnchantType raskol = CustomEnchantType.RASKOL;
        assertEquals("raskol", raskol.getId());
        assertEquals("Раскол", raskol.getDisplayName());
        assertEquals(2, raskol.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, raskol.getTarget());
        assertNull(raskol.getMutuallyExclusiveWith());
        assertEquals("При ударе по щиту: Слабость I + Замедление I на 2 сек", raskol.getDescription(1));
        assertEquals("При ударе по щиту: Слабость I + Замедление II + лёгкое замораживание на 2.5 сек", raskol.getDescription(2));

        // 4. Удар исподтишка
        CustomEnchantType udar = CustomEnchantType.UDAR_ISPODTISHKA;
        assertEquals("udar_ispodtishka", udar.getId());
        assertEquals("Удар исподтишка", udar.getDisplayName());
        assertEquals(2, udar.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, udar.getTarget());
        assertNull(udar.getMutuallyExclusiveWith());
        assertEquals("Удар в спину: критический урон ×1.4", udar.getDescription(1));
        assertEquals("Удар в спину: критический урон ×1.7 + игнорирует 2 брони", udar.getDescription(2));

        // 5. Тень
        CustomEnchantType ten = CustomEnchantType.TEN;
        assertEquals("ten", ten.getId());
        assertEquals("Тень", ten.getDisplayName());
        assertEquals(2, ten.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, ten.getTarget());
        assertNull(ten.getMutuallyExclusiveWith());
        assertEquals("После убийства в спину: Невидимость на 3 секунды", ten.getDescription(1));
        assertEquals("После убийства в спину: Невидимость на 5 секунд", ten.getDescription(2));

        // 6. Кровопийца
        CustomEnchantType krovopiyca = CustomEnchantType.KROVOPIYCA;
        assertEquals("krovopiyca", krovopiyca.getId());
        assertEquals("Кровопийца", krovopiyca.getDisplayName());
        assertEquals(1, krovopiyca.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, krovopiyca.getTarget());
        assertEquals("zhazhda_krovi", krovopiyca.getMutuallyExclusiveWith());
        assertEquals("При критическом ударе восстанавливает 0.5 сердца", krovopiyca.getDescription(1));

        // 7. Жажда крови
        CustomEnchantType zhazhda = CustomEnchantType.ZHAZHDA_KROVI;
        assertEquals("zhazhda_krovi", zhazhda.getId());
        assertEquals("Жажда крови", zhazhda.getDisplayName());
        assertEquals(2, zhazhda.getMaxLevel());
        assertEquals(CustomEnchantType.Target.DAGGER, zhazhda.getTarget());
        assertEquals("krovopiyca", zhazhda.getMutuallyExclusiveWith());
        assertEquals("При серии ударов даёт Регенерацию I", zhazhda.getDescription(1));
        assertEquals("При серии ударов даёт Регенерацию II", zhazhda.getDescription(2));

        // 8. Притяжение
        CustomEnchantType prityazhenie = CustomEnchantType.PRITYAZHENIE;
        assertEquals("prityazhenie", prityazhenie.getId());
        assertEquals("Притяжение", prityazhenie.getDisplayName());
        assertEquals(2, prityazhenie.getMaxLevel());
        assertEquals(CustomEnchantType.Target.LEGGINGS, prityazhenie.getTarget());
        assertNull(prityazhenie.getMutuallyExclusiveWith());
        assertEquals("Притягивает предметы в радиусе 3 блоков", prityazhenie.getDescription(1));
        assertEquals("Притягивает предметы в радиусе 5 блоков", prityazhenie.getDescription(2));
    }

    @Test
    @DisplayName("Formatted enchantment names correctly display roman numerals or omit them for single-level enchants")
    void testFormattedNames() {
        assertEquals("§7Изнурение I", CustomEnchantType.IZNURENIE.getFormattedName(1));
        assertEquals("§7Изнурение II", CustomEnchantType.IZNURENIE.getFormattedName(2));
        assertEquals("§7Кровопийца", CustomEnchantType.KROVOPIYCA.getFormattedName(1));
        assertEquals("§7Жажда крови I", CustomEnchantType.ZHAZHDA_KROVI.getFormattedName(1));
        assertEquals("§7Жажда крови II", CustomEnchantType.ZHAZHDA_KROVI.getFormattedName(2));
    }

    @Test
    @DisplayName("fromId correctly resolves enchantment types by exact ID, alias, and with prefix")
    void testFromId() {
        assertEquals(CustomEnchantType.IZNURENIE, CustomEnchantType.fromId("iznurenie"));
        assertEquals(CustomEnchantType.IZNURENIE, CustomEnchantType.fromId("enchant_iznurenie"));
        assertEquals(CustomEnchantType.IZNURENIE, CustomEnchantType.fromId("IZNURENIE"));

        assertEquals(CustomEnchantType.UDAR_ISPODTISHKA, CustomEnchantType.fromId("udar_ispodtishka"));
        assertEquals(CustomEnchantType.UDAR_ISPODTISHKA, CustomEnchantType.fromId("udar ispodtishka"));

        assertEquals(CustomEnchantType.KROVOPIYCA, CustomEnchantType.fromId("krovopiyca"));
        assertEquals(CustomEnchantType.ZHAZHDA_KROVI, CustomEnchantType.fromId("zhazhda_krovi"));

        assertNull(CustomEnchantType.fromId("unknown_enchant"));
    }
}

package me.lovelace.loveTweaks.enchantments;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CustomEnchantType {

    IZNURENIE(
            "iznurenie",
            "Изнурение",
            2,
            Target.DAGGER,
            null,
            new String[]{
                    "Накладывает Усталость I на 3 секунды",
                    "Накладывает Усталость II на 3.5 секунды"
            }
    ),
    SOCRUSHENIE(
            "socrushenie",
            "Сокрушение",
            2,
            Target.DAGGER,
            null,
            new String[]{
                    "Игнорирует 2 единицы брони",
                    "Игнорирует 3 единицы брони"
            }
    ),
    RASKOL(
            "raskol",
            "Раскол",
            2,
            Target.DAGGER,
            null,
            new String[]{
                    "При ударе по щиту: Слабость I + Замедление I на 2 сек",
                    "При ударе по щиту: Слабость I + Замедление II + лёгкое замораживание на 2.5 сек"
            }
    ),
    UDAR_ISPODTISHKA(
            "udar_ispodtishka",
            "Удар исподтишка",
            2,
            Target.DAGGER,
            null,
            new String[]{
                    "Удар в спину: критический урон ×1.4",
                    "Удар в спину: критический урон ×1.7 + игнорирует 2 брони"
            }
    ),
    TEN(
            "ten",
            "Тень",
            2,
            Target.DAGGER,
            null,
            new String[]{
                    "После убийства в спину: Невидимость на 3 секунды",
                    "После убийства в спину: Невидимость на 5 секунд"
            }
    ),
    KROVOPIYCA(
            "krovopiyca",
            "Кровопийца",
            1,
            Target.DAGGER,
            "zhazhda_krovi",
            new String[]{
                    "При критическом ударе восстанавливает 0.5 сердца"
            }
    ),
    ZHAZHDA_KROVI(
            "zhazhda_krovi",
            "Жажда крови",
            2,
            Target.DAGGER,
            "krovopiyca",
            new String[]{
                    "При серии ударов даёт Регенерацию I",
                    "При серии ударов даёт Регенерацию II"
            }
    ),
    PRITYAZHENIE(
            "prityazhenie",
            "Притяжение",
            2,
            Target.LEGGINGS,
            null,
            new String[]{
                    "Притягивает предметы в радиусе 3 блоков",
                    "Притягивает предметы в радиусе 5 блоков"
            }
    );

    public enum Target {
        DAGGER("Кинжал"),
        LEGGINGS("Штаны");

        private final String displayName;

        Target(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final String id;
    private final String displayName;
    private final int maxLevel;
    private final Target target;
    private final String mutuallyExclusiveWith;
    private final String[] descriptions;

    CustomEnchantType(String id, String displayName, int maxLevel, Target target, String mutuallyExclusiveWith, String[] descriptions) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.target = target;
        this.mutuallyExclusiveWith = mutuallyExclusiveWith;
        this.descriptions = descriptions;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Target getTarget() {
        return target;
    }

    @Nullable
    public String getMutuallyExclusiveWith() {
        return mutuallyExclusiveWith;
    }

    public String getDescription(int level) {
        int index = Math.max(0, Math.min(level - 1, descriptions.length - 1));
        return descriptions[index];
    }

    @NotNull
    public NamespacedKey getKey(@NotNull Plugin plugin) {
        return new NamespacedKey(plugin, id);
    }

    public String getFormattedName(int level) {
        if (maxLevel <= 1) {
            return "§7" + displayName;
        }
        return "§7" + displayName + " " + toRoman(level);
    }

    public static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    @Nullable
    public static CustomEnchantType fromId(@NotNull String id) {
        String normalized = id.toLowerCase(Locale.ROOT).replace("enchant_", "").replace(" ", "_");
        for (CustomEnchantType type : values()) {
            if (type.id.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}

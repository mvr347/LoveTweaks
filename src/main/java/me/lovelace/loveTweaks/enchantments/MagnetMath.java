package me.lovelace.loveTweaks.enchantments;

/** Pure maths of the "Притяжение" enchant: how an item is pulled toward its wearer. */
public final class MagnetMath {

    /** Items closer than this (blocks) are left for vanilla pickup. */
    public static final double MIN_DISTANCE = 0.5;
    /** Maximum pull speed in blocks per tick. */
    public static final double MAX_SPEED = 0.45;

    private MagnetMath() {
    }

    public static double radius(int level) {
        return level <= 1 ? 3.0 : 5.0;
    }

    /**
     * Velocity {dx,dy,dz} for an item at offset (dx,dy,dz) from the player, or {@code null} when it is out of range
     * or already close enough to be picked up normally. Speed grows as the item gets closer.
     */
    public static double[] pull(double dx, double dy, double dz, double radius) {
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > radius || dist < MIN_DISTANCE) {
            return null;
        }
        double speed = Math.min(MAX_SPEED, 0.15 + 0.3 * (1.0 - dist / radius));
        return new double[]{dx / dist * speed, dy / dist * speed, dz / dist * speed};
    }
}

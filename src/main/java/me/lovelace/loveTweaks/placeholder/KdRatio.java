package me.lovelace.loveTweaks.placeholder;

import java.util.Locale;

/** Kill/death ratio formatting, kept Bukkit-free so it can be unit tested. */
public final class KdRatio {

    private KdRatio() {}

    /** kills / deaths with two decimals; a player without deaths shows kills as the ratio (no division by zero). */
    public static String format(int kills, int deaths) {
        double ratio = deaths <= 0 ? kills : (double) kills / deaths;
        return String.format(Locale.ROOT, "%.2f", ratio);
    }
}

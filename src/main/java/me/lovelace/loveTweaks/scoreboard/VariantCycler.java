package me.lovelace.loveTweaks.scoreboard;

/** Pure helpers for the "format variant" mechanism (Shift+LMB cycles through them). */
public final class VariantCycler {

    private VariantCycler() {}

    /** Next variant index, wrapping around. A placeholder with 0 or 1 variants always stays on 0. */
    public static int next(int current, int count) {
        if (count <= 1) return 0;
        return (normalize(current, count) + 1) % count;
    }

    /** Clamps a stored index into {@code [0, count)}; anything out of range falls back to the first variant. */
    public static int normalize(int current, int count) {
        if (count <= 0) return 0;
        return current >= 0 && current < count ? current : 0;
    }
}

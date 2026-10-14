package me.lovelace.loveTweaks.scoreboard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One scoreboard line definition. A line has one or more format {@link Variant}s (Shift+LMB cycles
 * them); a plain {@code template} in config is just a single unnamed variant.
 */
public record ScoreboardPlaceholder(
    String id,
    String displayName,
    List<Variant> variants,
    List<String> lore,
    ScoreboardRequirement requirement,
    String category,
    Set<String> papiIds
) {
    public record Variant(String name, String template) {}

    public static ScoreboardPlaceholder create(String id, String displayName, List<Variant> variants,
                                               List<String> lore, ScoreboardRequirement requirement,
                                               String category) {
        // Every expansion the line needs: from all templates and from the unlock requirement.
        Set<String> ids = new LinkedHashSet<>();
        for (Variant v : variants) ids.addAll(PapiIds.extract(v.template()));
        if (requirement != null) ids.addAll(PapiIds.extract(requirement.placeholder()));
        return new ScoreboardPlaceholder(id, displayName, List.copyOf(variants), lore, requirement, category,
                Set.copyOf(ids));
    }

    public int variantCount() {
        return variants.size();
    }

    /** Template of the given variant; an out-of-range index falls back to the first one. */
    public String template(int variant) {
        if (variants.isEmpty()) return "";
        return variants.get(VariantCycler.normalize(variant, variants.size())).template();
    }

    public String variantName(int variant) {
        if (variants.isEmpty()) return "";
        return variants.get(VariantCycler.normalize(variant, variants.size())).name();
    }

    public boolean isUnlocked(org.bukkit.entity.Player player) {
        return requirement == null || requirement.isMet(player);
    }
}

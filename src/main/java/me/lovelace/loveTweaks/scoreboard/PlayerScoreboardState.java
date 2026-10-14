package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player scoreboard settings. The order of {@link #getActivePlaceholders()} is both the order
 * of the lines on the board and the order in which the numbered slot cards are filled.
 * Bukkit-free on purpose (unit tested).
 */
public class PlayerScoreboardState {

    private boolean scoreboardEnabled;
    private boolean autoDisabled;
    private boolean viewAll;
    private final List<String> activePlaceholders;
    private final Map<String, Integer> variants = new HashMap<>();

    public PlayerScoreboardState(boolean scoreboardEnabled, List<String> activePlaceholders) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.activePlaceholders = new ArrayList<>(activePlaceholders);
        this.autoDisabled = false;
    }

    public PlayerScoreboardState(boolean scoreboardEnabled, boolean viewAll, List<ScoreboardEntry> entries) {
        this(scoreboardEnabled, List.of());
        this.viewAll = viewAll;
        replaceWith(entries);
    }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean v) { this.scoreboardEnabled = v; }
    public boolean isAutoDisabled() { return autoDisabled; }
    public void setAutoDisabled(boolean v) { this.autoDisabled = v; }
    public boolean isViewAll() { return viewAll; }
    public void setViewAll(boolean v) { this.viewAll = v; }
    public List<String> getActivePlaceholders() { return activePlaceholders; }

    public boolean hasPlaceholderActive(String id) { return activePlaceholders.contains(id); }

    /** Index of the chosen format variant (0 when none was chosen). */
    public int getVariant(String id) { return variants.getOrDefault(id, 0); }

    public void setVariant(String id, int variant) {
        if (variant <= 0) variants.remove(id);
        else variants.put(id, variant);
    }

    /** Moves the placeholder to its next variant; returns the new index. */
    public int cycleVariant(String id, int variantCount) {
        int next = VariantCycler.next(getVariant(id), variantCount);
        setVariant(id, next);
        return next;
    }

    /** Snapshot for persistence. */
    public List<ScoreboardEntry> entries() {
        List<ScoreboardEntry> out = new ArrayList<>(activePlaceholders.size());
        for (String id : activePlaceholders) out.add(new ScoreboardEntry(id, getVariant(id)));
        return out;
    }

    public void replaceWith(List<ScoreboardEntry> entries) {
        activePlaceholders.clear();
        variants.clear();
        for (ScoreboardEntry e : entries) {
            if (activePlaceholders.contains(e.id())) continue;
            activePlaceholders.add(e.id());
            setVariant(e.id(), e.variant());
        }
    }

    /** Appends to the first free slot. @return false when already enabled or all slots are taken. */
    public boolean enable(String id, int maxPlaceholders) {
        if (activePlaceholders.contains(id) || activePlaceholders.size() >= maxPlaceholders) return false;
        activePlaceholders.add(id);
        return true;
    }

    /** Removes the line; the lines after it shift up, so slot cards stay gap-free. */
    public boolean disable(String id) {
        variants.remove(id);
        return activePlaceholders.remove(id);
    }

    public void togglePlaceholder(String id, int maxPlaceholders) {
        if (activePlaceholders.contains(id)) {
            disable(id);
        } else {
            enable(id, maxPlaceholders);
        }
    }

    /** @return true if the placeholder actually moved (false when it was already first). */
    public boolean movePlaceholderUp(String id) {
        int idx = activePlaceholders.indexOf(id);
        if (idx > 0) {
            activePlaceholders.remove(idx);
            activePlaceholders.add(idx - 1, id);
            return true;
        }
        return false;
    }

    /** @return true if the placeholder actually moved (false when it was already last). */
    public boolean movePlaceholderDown(String id) {
        int idx = activePlaceholders.indexOf(id);
        if (idx >= 0 && idx < activePlaceholders.size() - 1) {
            activePlaceholders.remove(idx);
            activePlaceholders.add(idx + 1, id);
            return true;
        }
        return false;
    }

    /** Drops lines beyond {@code max} (limit lowered in config, or an old DB row set). */
    public void clampTo(int max) {
        while (activePlaceholders.size() > max) {
            disable(activePlaceholders.get(activePlaceholders.size() - 1));
        }
    }

    public void ensureAutoDisable() {
        if (activePlaceholders.isEmpty() && scoreboardEnabled) {
            autoDisabled = true;
            scoreboardEnabled = false;
        }
    }
}

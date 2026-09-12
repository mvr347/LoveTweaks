package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class PlayerScoreboardState {

    private boolean scoreboardEnabled;
    private boolean autoDisabled;
    private final List<String> activePlaceholders;

    public PlayerScoreboardState(boolean scoreboardEnabled, List<String> activePlaceholders) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.activePlaceholders = new ArrayList<>(activePlaceholders);
        this.autoDisabled = false;
    }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean v) { this.scoreboardEnabled = v; }
    public boolean isAutoDisabled() { return autoDisabled; }
    public void setAutoDisabled(boolean v) { this.autoDisabled = v; }
    public List<String> getActivePlaceholders() { return activePlaceholders; }

    public boolean hasPlaceholderActive(String id) { return activePlaceholders.contains(id); }

    public void togglePlaceholder(String id, int maxPlaceholders) {
        if (activePlaceholders.contains(id)) {
            activePlaceholders.remove(id);
        } else if (activePlaceholders.size() < maxPlaceholders) {
            activePlaceholders.add(id);
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

    public void ensureAutoDisable() {
        if (activePlaceholders.isEmpty() && scoreboardEnabled) {
            autoDisabled = true;
            scoreboardEnabled = false;
        }
    }
}

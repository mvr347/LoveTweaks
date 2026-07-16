package me.lovelace.loveTweaks.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class PlayerScoreboardState {

    private boolean scoreboardEnabled;
    private final List<String> activeSections;

    public PlayerScoreboardState(boolean scoreboardEnabled, List<String> activeSections) {
        this.scoreboardEnabled = scoreboardEnabled;
        this.activeSections = new ArrayList<>(activeSections);
    }

    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public void setScoreboardEnabled(boolean v) { this.scoreboardEnabled = v; }
    public List<String> getActiveSections() { return activeSections; }

    public boolean hasSectionActive(String id) { return activeSections.contains(id); }

    public void toggleSection(String id, int maxSections) {
        if (activeSections.contains(id)) {
            activeSections.remove(id);
        } else if (activeSections.size() < maxSections) {
            activeSections.add(id);
        }
    }

    public void moveSectionUp(String id) {
        int idx = activeSections.indexOf(id);
        if (idx > 0) {
            activeSections.remove(idx);
            activeSections.add(idx - 1, id);
        }
    }

    public void moveSectionDown(String id) {
        int idx = activeSections.indexOf(id);
        if (idx >= 0 && idx < activeSections.size() - 1) {
            activeSections.remove(idx);
            activeSections.add(idx + 1, id);
        }
    }
}

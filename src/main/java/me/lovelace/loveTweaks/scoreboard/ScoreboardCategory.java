package me.lovelace.loveTweaks.scoreboard;

import java.util.List;

/**
 * A category of scoreboard lines. {@code icon} may be null: the GUI then falls back to the
 * shared category-button texture from {@code scoreboard.gui}.
 */
public record ScoreboardCategory(String id, String displayName, String icon, List<String> lore) {}

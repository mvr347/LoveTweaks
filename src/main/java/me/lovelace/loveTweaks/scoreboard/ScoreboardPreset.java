package me.lovelace.loveTweaks.scoreboard;

import java.util.List;

/** A quick layout from {@code scoreboard.presets}. {@code icon} may be null (shared texture is used). */
public record ScoreboardPreset(String id, String displayName, String icon, List<String> lore,
                               List<ScoreboardEntry> entries) {}

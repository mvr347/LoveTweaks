package me.lovelace.loveTweaks.scoreboard;

import java.util.List;

public record ScoreboardSection(
        String id,
        String displayName,
        String icon,
        List<String> lines,
        List<String> lore
) {}

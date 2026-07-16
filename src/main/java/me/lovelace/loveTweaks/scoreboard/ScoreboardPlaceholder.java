package me.lovelace.loveTweaks.scoreboard;

import java.util.List;

public record ScoreboardPlaceholder(
    String id,
    String displayName,
    String template,
    String icon,
    List<String> lore,
    ScoreboardPlaceholderCondition conditionType,
    int sortGroup
) {}

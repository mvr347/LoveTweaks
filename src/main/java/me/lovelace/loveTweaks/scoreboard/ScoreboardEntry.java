package me.lovelace.loveTweaks.scoreboard;

/** One enabled scoreboard line: placeholder id plus the index of the chosen format variant. */
public record ScoreboardEntry(String id, int variant) {
    public ScoreboardEntry(String id) {
        this(id, 0);
    }
}

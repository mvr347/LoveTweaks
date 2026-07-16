package me.lovelace.loveTweaks.scoreboard;

import org.bukkit.entity.Player;

public enum ScoreboardPlaceholderCondition {
    ALWAYS {
        @Override
        public boolean isMet(Player player) {
            return true;
        }
    },
    IF_HAS_CLAN {
        @Override
        public boolean isMet(Player player) {
            return PlaceholderIntegration.has(player, "clans_tag");
        }
    },
    IF_HAS_GROUP {
        @Override
        public boolean isMet(Player player) {
            return PlaceholderIntegration.has(player, "lovesubnames_current");
        }
    },
    IF_HAS_BEHAVIOR {
        @Override
        public boolean isMet(Player player) {
            return PlaceholderIntegration.has(player, "lovebehavior_playstyle_name");
        }
    };

    public abstract boolean isMet(Player player);
}

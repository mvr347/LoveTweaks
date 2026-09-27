package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Safe, version-resilient bridge to ReviveMe's API methods.
 * <p>
 * Across different releases of ReviveMe (e.g. standalone ReviveMe-API 1.0.0 vs ReviveMe 4.4.8),
 * {@code ReviveMeAPI} can be either an interface (returning boxed {@link Boolean}) or a concrete class
 * (returning primitive {@code boolean}). Calling {@code ReviveMeAPI.hasDowned(player)} directly in
 * compiled code risks {@link IncompatibleClassChangeError} or {@link NoSuchMethodError} at runtime.
 * This bridge resolves methods reflectively at startup while caching {@link Method} instances for peak
 * performance and providing automatic fallbacks to ReviveMe's internal manager.
 */
public final class ReviveMeApiBridge {

    private static final Logger LOGGER = Bukkit.getLogger();

    private static volatile boolean initialized = false;
    private static Class<?> apiClass;
    private static Method hasDownedMethod;
    private static Method isReliverMethod;
    private static Method getDownedPlayerMethod;
    private static Method getDownedPlayerByReliverMethod;
    private static Method revivePlayerMethod;
    private static Method revivePlayerWithReviverMethod;
    private static Method downPlayerMethod;
    private static Method downPlayerCauseMethod;
    private static Method downPlayerEnemyMethod;
    private static Method downPlayerCauseEnemyMethod;

    private ReviveMeApiBridge() {
    }

    public static synchronized void init(Plugin reviveMePlugin) {
        if (reviveMePlugin == null || !reviveMePlugin.isEnabled()) {
            initialized = false;
            return;
        }

        try {
            ClassLoader loader = reviveMePlugin.getClass().getClassLoader();
            try {
                apiClass = Class.forName("net.kokoricraft.reviveme.api.ReviveMeAPI", false, loader);
            } catch (ClassNotFoundException e) {
                apiClass = Class.forName("net.kokoricraft.reviveme.api.ReviveMeAPI");
            }

            if (apiClass != null) {
                for (Method m : apiClass.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())) {
                        continue;
                    }
                    String name = m.getName();
                    int count = m.getParameterCount();

                    if ("hasDowned".equals(name) && count == 1) {
                        hasDownedMethod = m;
                    } else if ("isReliver".equals(name) && count == 1) {
                        isReliverMethod = m;
                    } else if ("getDownedPlayer".equals(name) && count == 1) {
                        getDownedPlayerMethod = m;
                    } else if ("getDownedPlayerByReliver".equals(name) && count == 1) {
                        getDownedPlayerByReliverMethod = m;
                    } else if ("revivePlayer".equals(name) && count == 1) {
                        revivePlayerMethod = m;
                    } else if ("revivePlayer".equals(name) && count == 2) {
                        revivePlayerWithReviverMethod = m;
                    } else if ("downPlayer".equals(name) && count == 1 && m.getParameterTypes()[0] == Player.class) {
                        downPlayerMethod = m;
                    } else if ("downPlayer".equals(name) && count == 2) {
                        Class<?> p1 = m.getParameterTypes()[0];
                        Class<?> p2 = m.getParameterTypes()[1];
                        if (p1 == Player.class && p2 == DamageCause.class) {
                            downPlayerCauseMethod = m;
                        } else if (p1 == Player.class && p2 == Player.class) {
                            downPlayerEnemyMethod = m;
                        }
                    } else if ("downPlayer".equals(name) && count == 3) {
                        downPlayerCauseEnemyMethod = m;
                    }
                }
            }
            initialized = true;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[ReviveMeApiBridge] Could not resolve ReviveMeAPI methods: " + t.getMessage());
            initialized = false;
        }
    }

    public static boolean isAvailable() {
        return initialized && apiClass != null;
    }

    /**
     * Checks if a player is currently injured (downed).
     * Corresponds to: {@code Boolean isDowned = ReviveMeAPI.hasDowned(player);}
     */
    public static boolean hasDowned(Player player) {
        if (player == null) {
            return false;
        }
        if (hasDownedMethod != null) {
            try {
                Object res = hasDownedMethod.invoke(null, player);
                if (res instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
            }
        }
        // Fallback: check ReviveMe's internal manager directly via reflection
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ReviveMe");
            if (plugin != null && plugin.isEnabled()) {
                Method getManagerMethod = plugin.getClass().getMethod("getManager");
                Object manager = getManagerMethod.invoke(plugin);
                if (manager != null) {
                    Method hasDowned = manager.getClass().getMethod("hasDowned", Entity.class);
                    Object res = hasDowned.invoke(manager, player);
                    if (res instanceof Boolean b) {
                        return b;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Checks if the player is currently reviving someone.
     * Corresponds to: {@code Boolean isReliver = ReviveMeAPI.isReliver(player);}
     */
    public static boolean isReliver(Player player) {
        if (player == null || isReliverMethod == null) {
            return false;
        }
        try {
            Object res = isReliverMethod.invoke(null, player);
            if (res instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Gets the DownedPlayer object for an injured player.
     * Corresponds to: {@code DownedPlayer downed = ReviveMeAPI.getDownedPlayer(player);}
     */
    public static Object getDownedPlayer(Player player) {
        if (player == null || getDownedPlayerMethod == null) {
            return null;
        }
        try {
            return getDownedPlayerMethod.invoke(null, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Gets the injured player being revived by a reliver.
     * Corresponds to: {@code DownedPlayer downed = ReviveMeAPI.getDownedPlayerByReliver(player);}
     */
    public static Object getDownedPlayerByReliver(Player player) {
        if (player == null || getDownedPlayerByReliverMethod == null) {
            return null;
        }
        try {
            return getDownedPlayerByReliverMethod.invoke(null, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Revives an injured player.
     * Corresponds to: {@code ReviveMeAPI.revivePlayer(player);}
     */
    public static void revivePlayer(Player player) {
        if (player == null || revivePlayerMethod == null) {
            return;
        }
        try {
            revivePlayerMethod.invoke(null, player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Revives an injured player with a specified reviver.
     * Corresponds to: {@code ReviveMeAPI.revivePlayer(player, reviver);}
     */
    public static void revivePlayer(Player player, Player reviver) {
        if (player == null || revivePlayerWithReviverMethod == null) {
            return;
        }
        try {
            revivePlayerWithReviverMethod.invoke(null, player, reviver);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets a player to downed state.
     * Corresponds to: {@code ReviveMeAPI.downPlayer(player);}
     */
    public static void downPlayer(Player player) {
        if (player == null || downPlayerMethod == null) {
            return;
        }
        try {
            downPlayerMethod.invoke(null, player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets a player to downed state with damage cause.
     * Corresponds to: {@code ReviveMeAPI.downPlayer(player, DamageCause.FALL);}
     */
    public static void downPlayer(Player player, DamageCause cause) {
        if (player == null || downPlayerCauseMethod == null) {
            return;
        }
        try {
            downPlayerCauseMethod.invoke(null, player, cause);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets a player to downed state caused by enemy player.
     * Corresponds to: {@code ReviveMeAPI.downPlayer(player, enemy);}
     */
    public static void downPlayer(Player player, Player enemy) {
        if (player == null || downPlayerEnemyMethod == null) {
            return;
        }
        try {
            downPlayerEnemyMethod.invoke(null, player, enemy);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Sets a player to downed state with damage cause and enemy player.
     * Corresponds to: {@code ReviveMeAPI.downPlayer(player, DamageCause.ENTITY_ATTACK, enemy);}
     */
    public static void downPlayer(Player player, DamageCause cause, Player enemy) {
        if (player == null || downPlayerCauseEnemyMethod == null) {
            return;
        }
        try {
            downPlayerCauseEnemyMethod.invoke(null, player, cause, enemy);
        } catch (Throwable ignored) {
        }
    }
}

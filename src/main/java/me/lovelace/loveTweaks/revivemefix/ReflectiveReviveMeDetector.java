package me.lovelace.loveTweaks.revivemefix;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Finds ReviveMe's {@code PlayerDownedEvent}/{@code PlayerReviveEvent} classes by scanning the
 * installed ReviveMe jar for those simple class names, rather than hardcoding a package path.
 * The exact package differs across ReviveMe releases, and the whole point of this module is to
 * not depend on internals of a specific build (see module spec section 46) - resolving the real
 * classes from whatever jar is actually installed means it keeps working across ReviveMe updates
 * as long as the class/method names themselves don't change.
 * <p>
 * If either event class, or a no-arg {@code Player}-returning getter on it, can't be found, the
 * detector reports {@link #isAvailable()} as {@code false} and wires up nothing - per the module
 * spec, "if not sure the player is Downed, don't save their inventory" beats a guess.
 */
final class ReflectiveReviveMeDetector implements ReviveMeDetector {

    private static final Set<String> WANTED_SIMPLE_NAMES = Set.of("PlayerDownedEvent", "PlayerReviveEvent", "ReviveMeAPI");

    private final JavaPlugin ownerPlugin;
    private final boolean debug;

    private boolean available;
    private Listener registeredListener;
    private Method hasDownedMethod;

    ReflectiveReviveMeDetector(JavaPlugin ownerPlugin, boolean debug, Consumer<Player> onDowned, Consumer<Player> onRevived) {
        this.ownerPlugin = ownerPlugin;
        this.debug = debug;
        try {
            resolveAndHook(onDowned, onRevived);
        } catch (Throwable t) {
            log(Level.WARNING, "Failed to hook into ReviveMe, module will stay inactive: " + t.getMessage());
            available = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean isDowned(Player player) {
        if (!available || hasDownedMethod == null) {
            return false;
        }
        try {
            Object result = hasDownedMethod.invoke(null, player);
            return result instanceof Boolean bool && bool;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Unregisters the dynamically-registered listener (called from module disable()). */
    void shutdown() {
        if (registeredListener != null) {
            HandlerList.unregisterAll(registeredListener);
            registeredListener = null;
        }
        available = false;
    }

    private void resolveAndHook(Consumer<Player> onDowned, Consumer<Player> onRevived) {
        Plugin reviveMe = Bukkit.getPluginManager().getPlugin("ReviveMe");
        if (reviveMe == null || !reviveMe.isEnabled()) {
            log(Level.INFO, "ReviveMe not found or disabled - inventory-fix module stays inactive.");
            return;
        }

        Map<String, Class<?>> found = scanJarForClasses(reviveMe, WANTED_SIMPLE_NAMES);
        Class<?> downedEventClass = found.get("PlayerDownedEvent");
        Class<?> reviveEventClass = found.get("PlayerReviveEvent");
        Class<?> apiClass = found.get("ReviveMeAPI");

        if (downedEventClass == null || reviveEventClass == null
                || !Event.class.isAssignableFrom(downedEventClass) || !Event.class.isAssignableFrom(reviveEventClass)) {
            log(Level.WARNING, "Could not locate ReviveMe's PlayerDownedEvent/PlayerReviveEvent in the installed jar "
                    + "(ReviveMe version may have renamed them) - inventory-fix module stays inactive.");
            return;
        }

        Method downedGetPlayer = findPlayerGetter(downedEventClass);
        Method reviveGetPlayer = findPlayerGetter(reviveEventClass);
        if (downedGetPlayer == null || reviveGetPlayer == null) {
            log(Level.WARNING, "ReviveMe's Downed/Revive events don't expose a Player getter as expected - "
                    + "inventory-fix module stays inactive.");
            return;
        }

        if (apiClass != null) {
            for (Method method : apiClass.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && "hasDowned".equals(method.getName())
                        && method.getParameterCount() == 1 && method.getReturnType() == boolean.class) {
                    hasDownedMethod = method;
                    break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> downedEventType = (Class<? extends Event>) downedEventClass;
        @SuppressWarnings("unchecked")
        Class<? extends Event> reviveEventType = (Class<? extends Event>) reviveEventClass;

        registeredListener = new Listener() {
        };

        Bukkit.getPluginManager().registerEvent(downedEventType, registeredListener, EventPriority.MONITOR,
                playerExtractingExecutor(downedGetPlayer, onDowned), ownerPlugin, false);
        Bukkit.getPluginManager().registerEvent(reviveEventType, registeredListener, EventPriority.MONITOR,
                playerExtractingExecutor(reviveGetPlayer, onRevived), ownerPlugin, false);

        available = true;
        log(Level.INFO, "Hooked into ReviveMe (" + downedEventClass.getName() + " / " + reviveEventClass.getName() + ").");
    }

    private EventExecutor playerExtractingExecutor(Method playerGetter, Consumer<Player> callback) {
        return (listener, event) -> {
            try {
                Object playerObj = playerGetter.invoke(event);
                if (playerObj instanceof Player player) {
                    callback.accept(player);
                }
            } catch (Throwable t) {
                log(Level.WARNING, "Failed to read Player from ReviveMe event: " + t.getMessage());
            }
        };
    }

    /** First public no-arg method returning a {@link Player}, preferring one literally named {@code getPlayer}. */
    private static Method findPlayerGetter(Class<?> eventClass) {
        Method fallback = null;
        for (Method method : eventClass.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }
            if (!Player.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if ("getPlayer".equals(method.getName())) {
                return method;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        return fallback;
    }

    /** Scans the plugin jar's entries for classes matching any of {@code simpleNames}, stopping once all are found. */
    private static Map<String, Class<?>> scanJarForClasses(Plugin plugin, Set<String> simpleNames) {
        Map<String, Class<?>> found = new HashMap<>();
        Set<String> remaining = new HashSet<>(simpleNames);
        try {
            java.io.File jarFile = new java.io.File(
                    plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            ClassLoader loader = plugin.getClass().getClassLoader();

            try (JarFile jar = new JarFile(jarFile)) {
                var entries = jar.entries();
                while (entries.hasMoreElements() && !remaining.isEmpty()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.contains("$")) {
                        continue;
                    }
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    int lastDot = className.lastIndexOf('.');
                    String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
                    if (!remaining.contains(simpleName)) {
                        continue;
                    }
                    try {
                        found.put(simpleName, Class.forName(className, false, loader));
                        remaining.remove(simpleName);
                    } catch (Throwable ignored) {
                        // Class failed to load (missing transitive dependency, etc.) - keep scanning,
                        // maybe another entry with the same simple name (shouldn't normally happen) works.
                    }
                }
            }
        } catch (Exception ignored) {
            // No readable jar (exploded classes dir, sealed jar, etc.) - found stays whatever it has so far.
        }
        return found;
    }

    private void log(Level level, String message) {
        if (level == Level.INFO && !debug) {
            return;
        }
        ownerPlugin.getLogger().log(level, "[ReviveMeInventoryFix] " + message);
    }
}

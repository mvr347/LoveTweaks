package me.lovelace.loveTweaks.vanilla;

import java.lang.reflect.Field;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Re-enables vanilla "attribute swapping" that Paper blocks by default.
 *
 * <p>Paper keeps the switch in {@code GlobalConfiguration.get().unsupportedSettings
 * .updateEquipmentOnPlayerActions} (paper-global.yml: {@code unsupported-settings.update-equipment-on-player-actions},
 * default {@code true} = swapping blocked). The field is public and read on every packet, so writing it takes effect
 * at once. The plugin only compiles against paper-api, hence reflection. Paper rebuilds the configuration object on
 * {@code /paper reload}, which restores the value from the file, so the caller re-applies it periodically.</p>
 *
 * <p>All failures (Paper internals renamed, running on a non-Paper server) degrade to a single warning and a no-op.</p>
 */
public final class AttributeSwapToggle {

    static final String FIELD = "updateEquipmentOnPlayerActions";

    private final Logger log;
    private final Supplier<Object> settingsSource;
    private Boolean original;
    private boolean warned;

    AttributeSwapToggle(Logger log, Supplier<Object> settingsSource) {
        this.log = log;
        this.settingsSource = settingsSource;
    }

    /** Toggle bound to the real Paper configuration. */
    public static AttributeSwapToggle forPaper(Logger log) {
        return new AttributeSwapToggle(log, AttributeSwapToggle::paperSettings);
    }

    private static Object paperSettings() {
        try {
            Class<?> global = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object config = global.getMethod("get").invoke(null);
            return global.getField("unsupportedSettings").get(config);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    /** Makes swapping allowed ({@code true}) or blocked again ({@code false}). Safe to call repeatedly. */
    public void apply(boolean allowSwap) {
        write(!allowSwap);
    }

    /** Puts back the value Paper had when this toggle first touched it. */
    public void restore() {
        if (original != null) write(original);
    }

    /** Value of the Paper switch right now, or {@code null} when it cannot be read. */
    public Boolean current() {
        Object settings = settingsSource.get();
        if (settings == null) return null;
        try {
            return settings.getClass().getField(FIELD).getBoolean(settings);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private void write(boolean value) {
        Object settings = settingsSource.get();
        if (settings == null) { warnOnce("не найден GlobalConfiguration Paper"); return; }
        try {
            Field field = settings.getClass().getField(FIELD);
            boolean now = field.getBoolean(settings);
            // First touch: remember what Paper had (the value from paper-global.yml) so restore() can undo us.
            if (original == null) original = now;
            if (now != value) field.setBoolean(settings, value);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce("поле " + FIELD + " недоступно (" + error + ")");
        }
    }

    private void warnOnce(String reason) {
        if (warned) return;
        warned = true;
        log.warning("Attribute swap не переключён: " + reason + ". Задайте unsupported-settings."
                + "update-equipment-on-player-actions: false в config/paper-global.yml вручную.");
    }
}

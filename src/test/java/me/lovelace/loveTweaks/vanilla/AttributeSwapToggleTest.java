package me.lovelace.loveTweaks.vanilla;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AttributeSwapToggleTest {

    /** Stand-in with the same public field Paper's UnsupportedSettings exposes. */
    public static class FakeSettings {
        public boolean updateEquipmentOnPlayerActions = true;
    }

    private static AttributeSwapToggle toggle(FakeSettings[] holder) {
        return new AttributeSwapToggle(Logger.getAnonymousLogger(), () -> holder[0]);
    }

    @Test
    void allowingSwapTurnsPaperBlockOff() {
        FakeSettings[] holder = {new FakeSettings()};
        toggle(holder).apply(true);
        assertFalse(holder[0].updateEquipmentOnPlayerActions);
    }

    @Test
    void restorePutsBackTheOriginalValue() {
        FakeSettings[] holder = {new FakeSettings()};
        AttributeSwapToggle toggle = toggle(holder);
        toggle.apply(true);
        toggle.restore();
        assertTrue(holder[0].updateEquipmentOnPlayerActions);
    }

    @Test
    void reapplyingSurvivesPaperRebuildingItsConfig() {
        FakeSettings[] holder = {new FakeSettings()};
        AttributeSwapToggle toggle = toggle(holder);
        toggle.apply(true);
        holder[0] = new FakeSettings(); // /paper reload: fresh object, value from file (true)
        toggle.apply(true);
        assertFalse(holder[0].updateEquipmentOnPlayerActions);
    }

    @Test
    void restoreKeepsFileValueWhenServerAlreadyAllowedSwap() {
        FakeSettings[] holder = {new FakeSettings()};
        holder[0].updateEquipmentOnPlayerActions = false; // owner already edited paper-global.yml
        AttributeSwapToggle toggle = toggle(holder);
        toggle.apply(true);
        toggle.restore();
        assertFalse(holder[0].updateEquipmentOnPlayerActions);
    }

    @Test
    void disablingTheTweakBlocksSwapAgainOnlyWhenWeChangedIt() {
        FakeSettings[] holder = {new FakeSettings()};
        AttributeSwapToggle toggle = toggle(holder);
        toggle.apply(true);
        toggle.restore();
        assertTrue(holder[0].updateEquipmentOnPlayerActions);
    }

    @Test
    void missingPaperInternalsIsANoOp() {
        AttributeSwapToggle toggle = new AttributeSwapToggle(Logger.getAnonymousLogger(), () -> null);
        assertDoesNotThrow(() -> toggle.apply(true));
        assertDoesNotThrow(toggle::restore);
        assertNull(toggle.current());
    }

    @Test
    void settingsObjectWithoutTheFieldIsANoOp() {
        AttributeSwapToggle toggle = new AttributeSwapToggle(Logger.getAnonymousLogger(), Object::new);
        assertDoesNotThrow(() -> toggle.apply(true));
        assertNull(toggle.current());
    }
}

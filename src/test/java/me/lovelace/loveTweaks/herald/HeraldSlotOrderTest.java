package me.lovelace.loveTweaks.herald;

import me.lovelace.loveTweaks.herald.HeraldManager.Unavailability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HeraldSlotOrderTest {

    private static HeraldSlot free() {
        return new HeraldSlot();
    }

    private static HeraldSlot taken() {
        HeraldSlot slot = new HeraldSlot();
        slot.activate(UUID.randomUUID(), "Buyer", "hello", 60_000L);
        return slot;
    }

    @Test
    @DisplayName("Only the first slot is open when everything is free")
    void allFree() {
        List<HeraldSlot> slots = List.of(free(), free(), free());
        assertTrue(HeraldManager.isSlotUnlocked(slots, 0));
        assertFalse(HeraldManager.isSlotUnlocked(slots, 1));
        assertFalse(HeraldManager.isSlotUnlocked(slots, 2));
    }

    @Test
    @DisplayName("Slot N+1 opens once slot N is occupied")
    void firstTaken() {
        List<HeraldSlot> slots = List.of(taken(), free(), free());
        assertFalse(HeraldManager.isSlotUnlocked(slots, 0), "occupied slot is not buyable");
        assertTrue(HeraldManager.isSlotUnlocked(slots, 1));
        assertFalse(HeraldManager.isSlotUnlocked(slots, 2));
    }

    @Test
    @DisplayName("A gap (slot 0 expired, slot 1 occupied) reopens slot 0 and keeps slot 2 reachable")
    void gapAfterExpiry() {
        List<HeraldSlot> slots = List.of(free(), taken(), free());
        assertTrue(HeraldManager.isSlotUnlocked(slots, 0));
        assertFalse(HeraldManager.isSlotUnlocked(slots, 1));
        assertTrue(HeraldManager.isSlotUnlocked(slots, 2));
    }

    @Test
    @DisplayName("All slots occupied: nothing is unlocked")
    void allTaken() {
        List<HeraldSlot> slots = List.of(taken(), taken(), taken());
        for (int i = 0; i < slots.size(); i++) {
            assertFalse(HeraldManager.isSlotUnlocked(slots, i));
        }
    }

    @Test
    @DisplayName("Out-of-range indexes are never unlocked")
    void outOfRange() {
        List<HeraldSlot> slots = List.of(free());
        assertFalse(HeraldManager.isSlotUnlocked(slots, -1));
        assertFalse(HeraldManager.isSlotUnlocked(slots, 1));
        assertFalse(HeraldManager.isSlotUnlocked(List.of(), 0));
    }

    @Test
    @DisplayName("Unavailability priority: locked > owns slot > no economy > no funds")
    void unavailabilityPriority() {
        assertEquals(Unavailability.SLOT_LOCKED, HeraldManager.resolveUnavailability(false, true, false, false));
        assertEquals(Unavailability.OWNS_SLOT, HeraldManager.resolveUnavailability(true, true, false, false));
        assertEquals(Unavailability.NO_ECONOMY, HeraldManager.resolveUnavailability(true, false, false, false));
        assertEquals(Unavailability.NO_FUNDS, HeraldManager.resolveUnavailability(true, false, true, false));
        assertEquals(Unavailability.NONE, HeraldManager.resolveUnavailability(true, false, true, true));
    }
}

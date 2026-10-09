package dev.podium.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsTest {

    @Test
    void rangesAndNumbersAreRead() {
        assertEquals(List.of(0, 1, 2, 9, 10, 25), Settings.parseSlots("[0-2, 9-10, 25]"));
        assertEquals(List.of(4), Settings.parseSlots("4"));
    }

    @Test
    void aTypoOrAnImpossibleSlotIsLeftOut() {
        assertEquals(List.of(1, 3), Settings.parseSlots("1, banana, 3, 7-x"));
        assertEquals(List.of(52, 53), Settings.parseSlots("52-9999"));
        assertTrue(Settings.parseSlots("-3").isEmpty());
        assertTrue(Settings.parseSlots("").isEmpty());
        assertTrue(Settings.parseSlots(null).isEmpty());
    }

    @Test
    void aHugeRangeCannotEatMemory() {
        assertEquals(54, Settings.parseSlots("0-2000000000").size());
    }
}

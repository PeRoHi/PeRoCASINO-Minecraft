package me.bokan.perocasino.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarryCornerSetTest {

    @Test
    void normalizesMinMaxWithoutDroppingEitherCorner() {
        QuarryCornerSet.Corner a = new QuarryCornerSet.Corner("world", 10, 80, 5);
        QuarryCornerSet.Corner b = new QuarryCornerSet.Corner("world", 1, 70, 20);
        QuarryCornerSet.Range range = QuarryCornerSet.complete(a, b).orElseThrow();
        assertEquals("world", range.world());
        assertEquals(1, range.minX());
        assertEquals(70, range.minY());
        assertEquals(5, range.minZ());
        assertEquals(10, range.maxX());
        assertEquals(80, range.maxY());
        assertEquals(20, range.maxZ());
    }

    @Test
    void differentWorldsDoNotComplete() {
        QuarryCornerSet.Corner a = new QuarryCornerSet.Corner("a", 0, 0, 0);
        QuarryCornerSet.Corner b = new QuarryCornerSet.Corner("b", 1, 1, 1);
        assertTrue(QuarryCornerSet.complete(a, b).isEmpty());
    }
}

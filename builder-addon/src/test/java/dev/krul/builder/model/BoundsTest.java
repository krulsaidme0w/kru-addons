package dev.krul.builder.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundsTest {
    @Test
    void volumeIncludesBothEndpoints() {
        assertEquals(8L, new Bounds(new Cell(0, 0, 0), new Cell(1, 1, 1)).volume());
        assertEquals(1L, new Bounds(new Cell(-2, 3, -4), new Cell(-2, 3, -4)).volume());
        assertEquals(24L, new Bounds(new Cell(-1, -2, -3), new Cell(0, 0, 0)).volume());
    }

    @Test
    void rejectsReversedEndpointsOnEveryAxis() {
        assertThrows(IllegalArgumentException.class,
            () -> new Bounds(new Cell(2, 0, 0), new Cell(1, 1, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new Bounds(new Cell(0, 2, 0), new Cell(1, 1, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> new Bounds(new Cell(0, 0, 2), new Cell(1, 1, 1)));
    }

    @Test
    void containmentIsInclusiveOnEveryAxis() {
        var bounds = new Bounds(new Cell(-1, -2, -3), new Cell(1, 2, 3));
        assertTrue(bounds.contains(new Cell(-1, -2, -3)));
        assertTrue(bounds.contains(new Cell(1, 2, 3)));
        assertTrue(bounds.contains(new Cell(0, 0, 0)));
        for (var outside : new Cell[] {new Cell(-2, 0, 0), new Cell(2, 0, 0),
            new Cell(0, -3, 0), new Cell(0, 3, 0), new Cell(0, 0, -4), new Cell(0, 0, 4)}) {
            assertFalse(bounds.contains(outside), outside.toString());
        }
    }

    @Test
    void widensCoordinatesBeforeSubtractingOrAdding() {
        assertEquals(4_294_967_296L,
            new Bounds(new Cell(Integer.MIN_VALUE, 0, 0), new Cell(Integer.MAX_VALUE, 0, 0)).volume());
        assertEquals(4_294_967_296L,
            new Bounds(new Cell(0, Integer.MIN_VALUE, 0), new Cell(0, Integer.MAX_VALUE, 0)).volume());
        assertEquals(4_294_967_296L,
            new Bounds(new Cell(0, 0, Integer.MIN_VALUE), new Cell(0, 0, Integer.MAX_VALUE)).volume());
    }

    @Test
    void volumeRejectsLongOverflowInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> new Bounds(
            new Cell(Integer.MIN_VALUE, Integer.MIN_VALUE, 0),
            new Cell(Integer.MAX_VALUE, Integer.MAX_VALUE, 0)).volume());
        assertThrows(ArithmeticException.class, () -> new Bounds(
            new Cell(0, Integer.MIN_VALUE, Integer.MIN_VALUE),
            new Cell(0, Integer.MAX_VALUE, Integer.MAX_VALUE)).volume());
    }

    @Test
    void rejectsMissingCoordinates() {
        var origin = new Cell(0, 0, 0);
        assertThrows(NullPointerException.class, () -> new Bounds(null, origin));
        assertThrows(NullPointerException.class, () -> new Bounds(origin, null));
        assertThrows(NullPointerException.class, () -> new Bounds(origin, origin).contains(null));
    }
}

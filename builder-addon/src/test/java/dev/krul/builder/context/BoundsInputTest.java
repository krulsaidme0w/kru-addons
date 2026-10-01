package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundsInputTest {
    @Test void parsesAllSixCoordinatesAndOrdersEndpoints() {
        assertEquals(new Bounds(new Cell(-2, 4, 6), new Cell(3, 8, 9)),
            BoundsInput.parse("3", "8", "9", "-2", "4", "6").orElseThrow());
    }

    @Test void rejectsMissingMalformedAndOversizedCoordinates() {
        assertTrue(BoundsInput.parse("1", "2", "3", "", "5", "6").isEmpty());
        assertTrue(BoundsInput.parse("1", "two", "3", "4", "5", "6").isEmpty());
        assertTrue(BoundsInput.parse("0", "0", "0", "32768", "0", "0").isEmpty());
        assertTrue(BoundsInput.parse("-2147483648", "0", "0", "2147483647", "1", "1").isEmpty());
    }
}

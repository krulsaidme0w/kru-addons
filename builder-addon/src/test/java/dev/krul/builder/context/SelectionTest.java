package dev.krul.builder.context;

import dev.krul.builder.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionTest {
    @Test void requiresBothCornersAndNormalizesEachAxis() {
        var selection = new Selection();
        assertTrue(selection.bounds().isEmpty());
        selection.setSecond(new Cell(-5,70,8));
        assertTrue(selection.bounds().isEmpty());
        selection.setFirst(new Cell(3,60,12));
        assertEquals(new Bounds(new Cell(-5,60,8), new Cell(3,70,12)), selection.bounds().orElseThrow());
        selection.setFirst(new Cell(-6,80,7));
        assertEquals(new Bounds(new Cell(-6,70,7), new Cell(-5,80,8)), selection.bounds().orElseThrow());
        selection.clear();
        selection.setFirst(new Cell(0,0,0));
        assertTrue(selection.bounds().isEmpty(), "Clear must forget both corners");
    }
}

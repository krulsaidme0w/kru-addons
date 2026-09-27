package dev.krul.cobblemon.observation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class LoadedObservationCursorTest {
    @Test void initializationAndEmptySlotsCannotConsumeTheWholeSourceOutsideBudget() {
        AtomicInteger consumed = new AtomicInteger(), slots = new AtomicInteger(), reads = new AtomicInteger();
        Iterable<Integer> entities = () -> new Iterator<>() {
            int n;
            public boolean hasNext() { return n < 2; }
            public Integer next() { consumed.incrementAndGet(); return n++; }
        };
        var cursor = new LoadedObservationCursor<Integer, Integer>(entities, 1000,
            slot -> { slots.incrementAndGet(); return Collections.emptyIterator(); },
            (value, now) -> { reads.incrementAndGet(); return null; }, (value, now) -> null);
        assertEquals(0, consumed.get()); assertEquals(0, slots.get());
        for (int n = 0; n < 5; n++) cursor.next(1);
        assertEquals(2, consumed.get()); assertEquals(2, reads.get());
        assertTrue(slots.get() <= 3); assertFalse(cursor.complete());
    }
}

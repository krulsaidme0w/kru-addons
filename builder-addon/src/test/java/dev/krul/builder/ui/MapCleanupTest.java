package dev.krul.builder.ui;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MapCleanupTest {
    @Test void removesStaleEntriesAfterIterationAndDisposesEachValueOnce() {
        Map<String, String> textures = new HashMap<>(Map.of("keep", "live", "drop", "stale"));
        AtomicInteger disposals = new AtomicInteger();

        MapCleanup.removeWhere(textures, id -> id.equals("drop"), ignored -> disposals.incrementAndGet());

        assertEquals(Map.of("keep", "live"), textures);
        assertEquals(1, disposals.get());
    }
}

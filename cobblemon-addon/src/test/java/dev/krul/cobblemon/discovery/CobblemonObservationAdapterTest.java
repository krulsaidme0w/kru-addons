package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CobblemonObservationAdapterTest {
    @Test void discoveryInterpretsRadiantAspectAtItsBoundary() {
        assertEquals(Boolean.TRUE, CobblemonObservationAdapter.hasRadiantAspect(Set.of("day", "Radiant")));
        assertEquals(Boolean.FALSE, CobblemonObservationAdapter.hasRadiantAspect(Set.of("day")));
        assertNull(CobblemonObservationAdapter.hasRadiantAspect(Set.of()));
    }
}

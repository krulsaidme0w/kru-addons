package dev.krul.cobblemon.observation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.observation.FactValue.*;

class ObservationRuntimeTest {
    static class Source implements ObservationRuntime.Source {
        int consumed, opened, inspected; boolean loaded = true; double radius;
        public ObservationRuntime.Cursor open(ObservationSnapshot.Scope scope, double radius) {
            opened++; this.radius = radius;
            return new ObservationRuntime.Cursor() {
                int position;
                public boolean complete() { return position == 20; }
                public ObservationRuntime.Entry next(long now) {
                    consumed++; int id = ++position;
                    return new ObservationRuntime.Entry(new ObservationSnapshot(
                        ObservationSnapshot.Identity.entity(scope, new UUID(0, id)), now,
                        Map.of("kind", known(Type.STRING, FactValue.Source.LIVE, "POKEMON"),
                            "distance", known(Type.NUMBER, FactValue.Source.LIVE, id))), id);
                }
            };
        }
        public boolean loaded(ObservationRuntime.Entry entry) { return loaded; }
        public ObservationRuntime.Entry inspect(ObservationRuntime.Entry entry, long now) { inspected++; return entry; }
    }
    @Test void subscriptionsShareOneLazyBudgetAndFilterRadiusWithoutTruncatingAtEight() {
        var runtime = new ObservationRuntime(3, 64); var source = new Source();
        var near = runtime.subscribe("pokemon", 4); var far = runtime.subscribe("generic", 30);
        Object connection = new Object(), world = new Object();
        runtime.tick(connection, world, source, 1);
        assertEquals(3, source.consumed); assertEquals(1, source.opened);
        assertEquals(30, source.radius); assertEquals(3, far.snapshot().entries().size());
        for (int i = 2; i <= 7; i++) runtime.tick(connection, world, source, i);
        assertEquals(20, source.consumed); assertEquals(20, far.snapshot().entries().size());
        assertEquals(4, near.snapshot().entries().size());
        assertThrows(UnsupportedOperationException.class, () -> far.snapshot().entries().clear());
        near.close(); far.close(); runtime.tick(connection, world, source, 8);
        assertEquals(20, source.consumed);
    }
    @Test void unloadAndScopeReplacementInvalidateInspectionAndOldSnapshots() {
        var runtime = new ObservationRuntime(3, 64); var source = new Source();
        var sub = runtime.subscribe("pokemon", 30);
        Object connection = new Object(), world = new Object();
        runtime.tick(connection, world, source, 1);
        var old = sub.snapshot(); var identity = old.entries().getFirst().observation().identity();
        assertTrue(sub.inspect(identity, 2).isPresent()); assertEquals(1, source.inspected);
        source.loaded = false; runtime.tick(connection, world, source, 3);
        assertTrue(sub.snapshot().entries().isEmpty()); assertTrue(sub.inspect(identity, 3).isEmpty());
        source.loaded = true; runtime.tick(connection, new Object(), source, 4);
        assertNotEquals(old.scope(), sub.snapshot().scope()); assertTrue(sub.inspect(identity, 4).isEmpty());
        var worldScope = sub.snapshot().scope(); runtime.tick(new Object(), world, source, 5);
        assertNotEquals(worldScope.connectionId(), sub.snapshot().scope().connectionId());
        runtime.tick(null, null, source, 6);
        assertEquals(ObservationRuntime.State.NO_WORLD, sub.snapshot().state());
        assertTrue(sub.snapshot().entries().isEmpty());
    }
}

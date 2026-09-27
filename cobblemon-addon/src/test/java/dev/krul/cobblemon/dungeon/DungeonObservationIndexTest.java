package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonObservationIndexTest {
    @Test
    void exposesCurrentImmutableObservationSnapshotForRendering() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        DungeonObservation first = observation("overworld", "one", Map.of(), 1);
        DungeonObservation second = observation("overworld", "two", Map.of(), 2);
        index.observe(first);
        index.observe(second);

        List<DungeonObservation> snapshot = index.observations();

        assertEquals(2, snapshot.size());
        assertTrue(snapshot.containsAll(List.of(first, second)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(first));
    }

    private static final BlockPos CHEST = new BlockPos(12, 64, -3);

    @Test
    void suppressesExactRepeatsButRefreshesSeenAt() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        DungeonObservation first = observation("overworld", "chest", Map.of("state", "closed"), 10);
        DungeonObservation repeat = observation("overworld", "chest", Map.of("state", "closed"), 20);

        assertTrue(index.observe(first));
        assertEquals(java.util.List.of(first), index.drainChanges(10));
        assertFalse(index.observe(repeat));
        assertEquals(1, index.summary().retainedObservations());
        assertEquals(0, index.summary().pendingChanges());
        assertEquals(20, index.summary().latestSeenAt());
        assertEquals(0, index.drainChanges(10).size());

        DungeonObservationIndex fresh = new DungeonObservationIndex(8);
        fresh.observe(repeat);
        assertEquals(repeat.seenAt(), fresh.summary().latestSeenAt());
    }

    @Test
    void emitsOneReplacementWhenPublicFactsChange() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        DungeonObservation closed = observation("overworld", "chest", Map.of("state", "closed"), 10);
        DungeonObservation open = observation("overworld", "chest", Map.of("state", "open"), 20);

        index.observe(closed);
        assertTrue(index.observe(open));

        assertEquals(java.util.List.of(open), index.drainChanges(10));
        assertEquals(0, index.drainChanges(10).size());
    }

    @Test
    void dimensionChangeStartsFreshSection() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        index.observe(observation("overworld", "chest", Map.of(), 10));

        index.beginDimension("the_nether");

        assertEquals("the_nether", index.summary().dimension());
        assertEquals(0, index.summary().retainedObservations());
        assertEquals(0, index.summary().pendingChanges());
        assertTrue(index.observe(observation("the_nether", "chest", Map.of(), 20)));
        assertEquals(1, index.summary().retainedObservations());
    }

    @Test
    void evictsOldestObservationsAtHardCapacity() {
        DungeonObservationIndex index = new DungeonObservationIndex(2);
        DungeonObservation first = observation("overworld", "one", Map.of(), 1);
        DungeonObservation second = observation("overworld", "two", Map.of(), 2);
        DungeonObservation third = observation("overworld", "three", Map.of(), 3);

        index.observe(first);
        index.observe(second);
        index.observe(third);

        assertEquals(2, index.summary().retainedObservations());
        assertEquals(java.util.List.of(second, third), index.drainChanges(10));
        assertTrue(index.observe(first));
        assertEquals(2, index.summary().retainedObservations());
    }

    @Test
    void drainIsBoundedAndKeepsRemainder() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        DungeonObservation first = observation("overworld", "one", Map.of(), 1);
        DungeonObservation second = observation("overworld", "two", Map.of(), 2);
        index.observe(first);
        index.observe(second);

        assertEquals(java.util.List.of(first), index.drainChanges(1));
        assertEquals(java.util.List.of(second), index.drainChanges(1));
    }

    @Test
    void refreshesPendingValuesWithoutChangingFifoOrder() {
        DungeonObservationIndex index = new DungeonObservationIndex(8);
        DungeonObservation first = observation("overworld", "one", Map.of(), 1);
        DungeonObservation second = observation("overworld", "two", Map.of(), 2);
        DungeonObservation refreshed = observation("overworld", "one", Map.of(), 3);
        DungeonObservation changed = observation("overworld", "two", Map.of("state", "open"), 4);
        index.observe(first);
        index.observe(second);
        assertTrue(index.observe(changed));
        assertFalse(index.observe(refreshed));

        assertEquals(List.of(), index.drainChanges(0));
        assertEquals(List.of(), index.drainChanges(-1));
        List<DungeonObservation> drained = index.drainChanges(1);
        assertEquals(List.of(refreshed), drained);
        assertThrows(UnsupportedOperationException.class, () -> drained.add(second));
        assertFalse(index.observe(refreshed));
        DungeonObservation requeued = observation("overworld", "one", Map.of("state", "open"), 5);
        assertTrue(index.observe(requeued));
        assertEquals(List.of(changed, requeued), index.drainChanges(Integer.MAX_VALUE));
    }

    @Test
    void evictionRemovesPendingKeyEvenAfterItWasRefreshedAndRequeued() {
        DungeonObservationIndex index = new DungeonObservationIndex(2);
        DungeonObservation first = observation("overworld", "one", Map.of(), 1);
        DungeonObservation second = observation("overworld", "two", Map.of(), 2);
        DungeonObservation changed = observation("overworld", "one", Map.of("state", "open"), 3);
        DungeonObservation third = observation("overworld", "three", Map.of(), 4);
        index.observe(first);
        index.observe(second);
        assertEquals(List.of(first), index.drainChanges(1));
        assertTrue(index.observe(changed));
        index.observe(third);

        assertEquals(List.of(second, third), index.drainChanges(10));
        assertTrue(index.observe(first));
        assertEquals(List.of(first), index.drainChanges(10));
        assertEquals(2, index.summary().retainedObservations());
    }

    @Test
    void saturatedPendingQueueHandlesRepeatedScansWithinBudget() {
        int capacity = 4096;
        DungeonObservationIndex index = new DungeonObservationIndex(capacity);
        // Warm up observation/key equality before timing the saturated queue.
        for (int i = 0; i < 10_000; i++) index.observe(observation("overworld", "warmup", Map.of(), i));
        index.beginDimension("overworld");
        for (int i = 0; i < capacity; i++) index.observe(observation("overworld", "block-" + i, Map.of(), 0));

        long started = System.nanoTime();
        for (int pass = 1; pass <= 32; pass++) {
            // Alternate unchanged refreshes and semantic replacements, keeping all keys pending.
            Map<String, String> facts = Map.of("state", Integer.toString(pass / 2));
            for (int i = capacity - 1; i >= 0; i--)
                index.observe(observation("overworld", "block-" + i, facts, pass));
        }
        long refreshNanos = System.nanoTime() - started;
        assertEquals(capacity, index.summary().pendingChanges());
        List<DungeonObservation> drained = index.drainChanges(capacity);
        assertEquals(capacity, drained.size());
        for (int i = 0; i < capacity; i++)
            assertEquals(observation("overworld", "block-" + i, Map.of("state", "16"), 32), drained.get(i));

        for (DungeonObservation observation : drained)
            index.observe(observation("overworld", observation.identity(), Map.of(), 33));
        started = System.nanoTime();
        for (int i = capacity; i < capacity * 3; i++)
            index.observe(observation("overworld", "block-" + i, Map.of(), 34));
        long evictionNanos = System.nanoTime() - started;
        assertEquals(capacity, index.summary().retainedObservations());
        assertEquals(capacity, index.summary().pendingChanges());
        drained = index.drainChanges(capacity);
        for (int i = 0; i < capacity; i++)
            assertEquals(observation("overworld", "block-" + (capacity * 2 + i), Map.of(), 34), drained.get(i));
        System.out.printf("DungeonObservationIndex saturated queue: refresh=%.3f ms eviction=%.3f ms%n",
            refreshNanos / 1_000_000.0, evictionNanos / 1_000_000.0);
        // Broad headroom for CI; detects queue-wide work on every observation.
        assertTrue(refreshNanos + evictionNanos < 2_000_000_000L,
            "131072 refreshes and 8192 evictions at capacity 4096 must finish within 2 seconds");
    }

    private static DungeonObservation observation(String dimension, String identity,
                                                  Map<String, String> facts, long seenAt) {
        return new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, dimension, identity,
            "minecraft:chest", "net.minecraft.ChestBlockEntity", "Chest", CHEST, facts, seenAt);
    }
}


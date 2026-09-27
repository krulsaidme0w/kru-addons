package dev.krul.cobblemon.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded semantic index for a single loaded-world observation session. */
public final class DungeonObservationIndex {
    public static final int DEFAULT_MAX_OBSERVATIONS = 4096;

    private final int maxObservations;
    private final Map<Key, DungeonObservation> observations = new HashMap<>();
    private final ArrayDeque<Key> insertionOrder = new ArrayDeque<>();
    // Insertion order keeps a pending key's FIFO position when its value is refreshed.
    private final LinkedHashMap<Key, DungeonObservation> changes = new LinkedHashMap<>();
    private String dimension = "unavailable";
    private long latestSeenAt;

    public DungeonObservationIndex() {
        this(DEFAULT_MAX_OBSERVATIONS);
    }

    public DungeonObservationIndex(int maxObservations) {
        if (maxObservations < 1) throw new IllegalArgumentException("maxObservations must be positive");
        this.maxObservations = maxObservations;
    }

    public void beginDimension(String dimension) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        observations.clear();
        insertionOrder.clear();
        changes.clear();
        latestSeenAt = 0;
    }

    public boolean observe(DungeonObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (!dimension.equals(observation.dimension())) beginDimension(observation.dimension());

        Key key = keyOf(observation);
        DungeonObservation previous = observations.put(key, observation);
        latestSeenAt = Math.max(latestSeenAt, observation.seenAt());
        if (previous == null) {
            insertionOrder.addLast(key);
            changes.put(key, observation);
            evictIfNeeded();
            return true;
        }

        boolean changed = !samePublicData(previous, observation);
        if (changed) changes.put(key, observation);
        else changes.replace(key, observation);
        return changed;
    }

    public List<DungeonObservation> drainChanges(int limit) {
        if (limit <= 0 || changes.isEmpty()) return List.of();
        List<DungeonObservation> drained = new ArrayList<>(Math.min(limit, changes.size()));
        var pending = changes.values().iterator();
        while (drained.size() < limit && pending.hasNext()) {
            drained.add(pending.next());
            pending.remove();
        }
        return List.copyOf(drained);
    }

    public Summary summary() {
        Map<DungeonObservation.Kind, Integer> counts = new java.util.EnumMap<>(DungeonObservation.Kind.class);
        for (DungeonObservation observation : observations.values())
            counts.merge(observation.kind(), 1, Integer::sum);
        return new Summary(dimension, observations.size(), changes.size(), latestSeenAt, counts);
    }

    public List<DungeonObservation> observations() {
        return List.copyOf(observations.values());
    }

    private void evictIfNeeded() {
        while (observations.size() > maxObservations) {
            Key evicted = insertionOrder.removeFirst();
            observations.remove(evicted);
            changes.remove(evicted);
        }
    }

    private static boolean samePublicData(DungeonObservation left, DungeonObservation right) {
        return left.kind() == right.kind()
            && left.dimension().equals(right.dimension())
            && left.identity().equals(right.identity())
            && Objects.equals(left.registryId(), right.registryId())
            && Objects.equals(left.className(), right.className())
            && Objects.equals(left.name(), right.name())
            && Objects.equals(left.position(), right.position())
            && left.facts().equals(right.facts());
    }

    private static Key keyOf(DungeonObservation observation) {
        return new Key(observation.dimension(), observation.kind(), observation.identity());
    }

    public record Summary(String dimension, int retainedObservations, int pendingChanges, long latestSeenAt,
                          Map<DungeonObservation.Kind, Integer> kindCounts) {
        public Summary(String dimension, int retainedObservations, int pendingChanges, long latestSeenAt) {
            this(dimension, retainedObservations, pendingChanges, latestSeenAt, Map.of());
        }

        public Summary {
            kindCounts = kindCounts == null ? Map.of() : Map.copyOf(kindCounts);
        }

        public int count(DungeonObservation.Kind kind) {
            return kindCounts.getOrDefault(kind, 0);
        }
    }

    private record Key(String dimension, DungeonObservation.Kind kind, String identity) {}
}


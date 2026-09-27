package dev.krul.cobblemon.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiscoveryIndex {
    private final int maxEntries;
    private final long expiryMillis;
    private final Map<Integer, PokemonObservation> observations = new HashMap<>();
    private Object world;

    public DiscoveryIndex(int maxEntries, long expiryMillis) {
        this.maxEntries = Math.max(1, maxEntries);
        this.expiryMillis = Math.max(0, expiryMillis);
    }

    /** Entity IDs and remembered observations belong to one exact client world instance. */
    public void bindWorld(Object world) {
        if (this.world != world) {
            this.world = world;
            clear();
        }
    }

    public boolean observe(PokemonObservation observation) {
        PokemonObservation previous = observations.put(observation.entityId(), observation);
        trimToLimit();
        return previous == null || notificationChanged(previous, observation);
    }

    public List<PokemonObservation> recent(long now, SpawnRarity minimum, int limit) {
        return recent(now, minimum, limit, expiryMillis);
    }

    public List<PokemonObservation> recent(long now, SpawnRarity minimum, int limit, long maxAgeMillis) {
        expire(now, Math.max(0, maxAgeMillis));
        return observations.values().stream()
            .filter(observation -> observation.spawnRarity().atLeast(minimum))
            .sorted(Comparator.comparingLong(PokemonObservation::seenAt).reversed())
            .limit(Math.max(0, limit))
            .toList();
    }

    public int size() {
        return observations.size();
    }

    public void clear() {
        observations.clear();
    }

    private void expire(long now, long maxAgeMillis) {
        observations.values().removeIf(observation -> now - observation.seenAt() > maxAgeMillis);
    }

    private void trimToLimit() {
        if (observations.size() <= maxEntries) return;

        List<PokemonObservation> sorted = new ArrayList<>(observations.values());
        sorted.sort(Comparator.comparingLong(PokemonObservation::seenAt).reversed());
        observations.clear();
        sorted.stream().limit(maxEntries).forEach(observation -> observations.put(observation.entityId(), observation));
    }

    private static boolean notificationChanged(PokemonObservation previous, PokemonObservation current) {
        return !previous.species().equals(current.species())
            || previous.shiny() != current.shiny()
            || previous.radiant() != current.radiant()
            || previous.spawnRarity() != current.spawnRarity()
            || !previous.speciesClasses().equals(current.speciesClasses())
            || !previous.speciesLabels().equals(current.speciesLabels());
    }
}

package dev.krul.cobblemon.discovery;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Session-scoped, read-only view of server and bundled rarity observations. */
public final class DiscoveryRarityEvidence {
    private final Map<String, EnumSet<SpawnRarity>> serverBuckets = new LinkedHashMap<>();
    private long revision;
    private final int capacity;
    private Object world;
    public DiscoveryRarityEvidence() { this(2048); }
    public DiscoveryRarityEvidence(int capacity) {
        if (capacity < 1 || capacity > 4096) throw new IllegalArgumentException("Invalid capacity");
        this.capacity = capacity;
    }
    public synchronized void bindWorld(Object world) {
        if (this.world != world) { this.world = world; reset(); }
    }

    /** Records one normalized server bucket and reports whether the evidence changed. */
    public synchronized boolean observeServer(String species, SpawnRarity rarity) {
        String key = normalize(species);
        if (key.isEmpty() || key.length() > 256 || rarity == null || rarity == SpawnRarity.UNKNOWN) return false;
        if (!serverBuckets.containsKey(key) && serverBuckets.size() >= capacity) serverBuckets.remove(serverBuckets.keySet().iterator().next());
        EnumSet<SpawnRarity> buckets = serverBuckets.computeIfAbsent(key,
            ignored -> EnumSet.noneOf(SpawnRarity.class));
        if (!buckets.add(rarity)) return false;
        revision++;
        return true;
    }

    public synchronized Resolution resolve(String species, DiscoveryRarityIndex local) {
        String key = normalize(species);
        Set<SpawnRarity> server = immutable(serverBuckets.get(key));
        Set<SpawnRarity> localBuckets = local == null ? Set.of() : local.rarities(key);
        if (!server.isEmpty()) {
            return new Resolution(minimum(server), Source.SERVER, server, Set.copyOf(localBuckets), revision);
        }
        if (!localBuckets.isEmpty()) {
            return new Resolution(minimum(localBuckets), Source.LOCAL, Set.of(), Set.copyOf(localBuckets), revision);
        }
        return new Resolution(SpawnRarity.UNKNOWN, Source.UNKNOWN, Set.of(), Set.of(), revision);
    }

    public synchronized boolean containsServer(String species) {
        return !immutable(serverBuckets.get(normalize(species))).isEmpty();
    }

    public synchronized long revision() {
        return revision;
    }

    /** Clears all observations at a session/identity boundary. */
    public synchronized void reset() {
        serverBuckets.clear();
        revision++;
    }

    public synchronized void clear() {
        reset();
    }

    private static Set<SpawnRarity> immutable(Set<SpawnRarity> values) {
        return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
    }

    private static SpawnRarity minimum(Set<SpawnRarity> values) {
        return values.stream().min(Comparator.comparingInt(Enum::ordinal)).orElse(SpawnRarity.UNKNOWN);
    }

    public static String normalize(String species) {
        if (species == null) return "";
        String normalized = species.toLowerCase(Locale.ROOT).trim();
        return normalized.isBlank() || normalized.contains(":") ? normalized : "cobblemon:" + normalized;
    }

    public enum Source {
        SERVER,
        LOCAL,
        UNKNOWN
    }

    public record Resolution(SpawnRarity rarity, Source source,
                             Set<SpawnRarity> serverBuckets, Set<SpawnRarity> localBuckets,
                             long revision) {
        public Resolution {
            serverBuckets = Set.copyOf(serverBuckets == null ? Set.of() : serverBuckets);
            localBuckets = Set.copyOf(localBuckets == null ? Set.of() : localBuckets);
        }
    }
}

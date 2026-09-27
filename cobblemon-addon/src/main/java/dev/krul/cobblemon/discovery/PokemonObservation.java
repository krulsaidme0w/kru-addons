package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.species.SpeciesClass;

import java.util.Set;
import java.util.Map;

public record PokemonObservation(
    int entityId,
    String species,
    Boolean shiny,
    Boolean radiant,
    SpawnRarity spawnRarity,
    Set<SpeciesClass> speciesClasses,
    Set<String> speciesLabels,
    double x,
    double y,
    double z,
    long seenAt,
    Map<String, String> details
) {
    public PokemonObservation {
        spawnRarity = spawnRarity == null ? SpawnRarity.UNKNOWN : spawnRarity;
        speciesClasses = speciesClasses == null ? Set.of() : Set.copyOf(speciesClasses);
        speciesLabels = speciesLabels == null ? Set.of() : Set.copyOf(speciesLabels);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public PokemonObservation withSpawnRarity(SpawnRarity value) {
        return new PokemonObservation(entityId, species, shiny, radiant,
            value, speciesClasses, speciesLabels, x, y, z, seenAt, details);
    }

    public PokemonObservation withDiscoveryFacts(SpawnRarity rarity, Set<SpeciesClass> classes,
                                                 Set<String> labels) {
        return new PokemonObservation(entityId, species, shiny, radiant,
            rarity, classes, labels, x, y, z, seenAt, details);
    }

    public static Builder builder(int entityId, String species) {
        return new Builder(entityId, species);
    }

    public static final class Builder {
        private final int entityId;
        private final String species;
        private Boolean shiny;
        private Boolean radiant;
        private SpawnRarity spawnRarity = SpawnRarity.UNKNOWN;
        private Set<SpeciesClass> speciesClasses = Set.of();
        private Set<String> speciesLabels = Set.of();
        private double x;
        private double y;
        private double z;
        private long seenAt;
        private Map<String, String> details = Map.of();

        private Builder(int entityId, String species) {
            this.entityId = entityId;
            this.species = species == null ? "" : species;
        }

        public Builder shiny(Boolean value) { shiny = value; return this; }
        public Builder radiant(Boolean value) { radiant = value; return this; }
        public Builder spawnRarity(SpawnRarity value) { spawnRarity = value == null ? SpawnRarity.UNKNOWN : value; return this; }
        public Builder speciesClasses(Set<SpeciesClass> value) { speciesClasses = value == null ? Set.of() : Set.copyOf(value); return this; }
        public Builder speciesLabels(Set<String> value) { speciesLabels = value == null ? Set.of() : Set.copyOf(value); return this; }
        public Builder position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
        public Builder seenAt(long value) { seenAt = value; return this; }
        public Builder details(Map<String, String> value) { details = value == null ? Map.of() : Map.copyOf(value); return this; }

        public PokemonObservation build() {
            return new PokemonObservation(entityId, species, shiny, radiant, spawnRarity,
                speciesClasses, speciesLabels, x, y, z, seenAt, details);
        }
    }
}

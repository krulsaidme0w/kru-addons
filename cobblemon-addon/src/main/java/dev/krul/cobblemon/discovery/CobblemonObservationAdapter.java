package dev.krul.cobblemon.discovery;

import net.minecraft.entity.Entity;

import dev.krul.cobblemon.pokemon.CobblemonPokemonReader;

import java.util.Optional;
import java.util.Locale;
import java.util.Set;

public final class CobblemonObservationAdapter {
    private final CobblemonPokemonReader reader = new CobblemonPokemonReader();

    public Optional<PokemonObservation> read(Entity entity, long now) {
        return reader.read(entity).map(data -> PokemonObservation.builder(entity.getId(), data.species())
                .shiny(data.shiny())
                .radiant(data.radiant() != null ? data.radiant() : hasRadiantAspect(data.aspects()))
                .details(data.details())
                .spawnRarity(SpawnRarity.UNKNOWN)
                .position(entity.getX(), entity.getY(), entity.getZ())
                .seenAt(now)
                .build());
    }

    /** Server-specific appearance convention belongs at the Cobblemon Discovery boundary. */
    static Boolean hasRadiantAspect(Set<String> aspects) {
        if (aspects == null || aspects.isEmpty()) return null;
        return aspects.stream().anyMatch(aspect -> aspect.toLowerCase(Locale.ROOT).contains("radiant"));
    }
}

package dev.krul.cobblemon.discovery;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Selects live Discovery render targets independently from the bounded HUD history. */
public final class DiscoveryRenderSelector {
    private DiscoveryRenderSelector() {
    }

    public static List<PokemonObservation> select(List<PokemonObservation> observations,
                                                   Set<Integer> loadedEntityIds,
                                                   double playerX, double playerY, double playerZ) {
        if (observations == null || loadedEntityIds == null) return List.of();
        var selected = observations.stream()
            .filter(observation -> loadedEntityIds.contains(observation.entityId()))
            .sorted(Comparator
                .comparingDouble((PokemonObservation observation) -> distanceSquared(
                    observation, playerX, playerY, playerZ))
                .thenComparingInt(PokemonObservation::entityId));
        return selected.toList();
    }

    private static double distanceSquared(PokemonObservation observation,
                                          double x, double y, double z) {
        double dx = observation.x() - x;
        double dy = observation.y() - y;
        double dz = observation.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}


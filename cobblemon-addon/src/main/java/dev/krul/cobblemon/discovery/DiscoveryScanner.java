package dev.krul.cobblemon.discovery;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class DiscoveryScanner {
    private final CobblemonObservationAdapter adapter;

    public DiscoveryScanner() {
        this(new CobblemonObservationAdapter());
    }

    public DiscoveryScanner(CobblemonObservationAdapter adapter) {
        this.adapter = adapter;
    }

    public List<PokemonObservation> scan(MinecraftClient client, double radius, long now) {
        if (client == null || client.world == null || client.player == null) return List.of();

        double radiusSquared = radius * radius;
        List<PokemonObservation> result = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.squaredDistanceTo(client.player) > radiusSquared) continue;
            adapter.read(entity, now).ifPresent(result::add);
        }
        return result;
    }
}


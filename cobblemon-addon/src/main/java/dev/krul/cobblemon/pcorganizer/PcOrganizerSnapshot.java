package dev.krul.cobblemon.pcorganizer;

import java.util.*;

public record PcOrganizerSnapshot(UUID storeUuid, int boxCount, List<String> boxNames, List<PcOrganizerPokemon> pokemon, int currentBox) {
    public PcOrganizerSnapshot(UUID storeUuid, int boxCount, List<String> boxNames, List<PcOrganizerPokemon> pokemon) {
        this(storeUuid, boxCount, boxNames, pokemon, 0);
    }
    public PcOrganizerSnapshot {
        Objects.requireNonNull(storeUuid, "storeUuid");
        if (boxCount < 1 || boxNames == null || boxNames.size() != boxCount) throw new IllegalArgumentException("invalid box layout");
        boxNames = List.copyOf(boxNames);
        if (currentBox < 0 || currentBox >= boxCount) throw new IllegalArgumentException("invalid current box");
        pokemon = pokemon == null ? List.of() : List.copyOf(pokemon);
        Set<UUID> uuids = new HashSet<>();
        Set<String> positions = new HashSet<>();
        for (var value : pokemon) {
            if (value.box() >= boxCount) throw new IllegalArgumentException("pokemon box out of range");
            if (value.slot() >= 30 || !positions.add(value.box() + ":" + value.slot())) throw new IllegalArgumentException("invalid pokemon slot");
            if (!uuids.add(value.uuid())) throw new IllegalArgumentException("duplicate pokemon UUID");
        }
    }
}

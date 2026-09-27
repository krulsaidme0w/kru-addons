package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerBoxMovePlanTest {
    @Test void everyMovePreservesAllPokemonAcrossSeveralPermutationCycles() {
        List<PcOrganizerPokemon> pokemon = new ArrayList<>();
        for (int box = 0; box < 6; box++) for (int slot = 0; slot < 30; slot++)
            pokemon.add(p(UUID.randomUUID(), box, slot, "p"));
        var snapshot = new PcOrganizerSnapshot(UUID.randomUUID(), 7,
            List.of("BREEDING", "MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "Buffer"), pokemon);
        var plan = PcOrganizerBoxMovePlanner.plan(snapshot);
        Map<String, UUID> slots = new HashMap<>();
        for (var p : pokemon) slots.put(p.box() + ":" + p.slot(), p.uuid());
        for (var move : plan.moves()) {
            String source = move.fromBox() + ":" + move.fromSlot(), target = move.toBox() + ":" + move.toSlot();
            assertFalse(slots.containsKey(target), "move overwrites occupied destination " + target);
            assertEquals(move.pokemon().uuid(), slots.remove(source));
            slots.put(target, move.pokemon().uuid());
        }
        assertEquals(pokemon.size(), slots.size());
        for (int box = 0; box < 6; box++) for (int slot = 0; slot < 30; slot++)
            assertEquals(pokemon.get(plan.sourceOrder().get(box) * 30 + slot).uuid(), slots.get(box + ":" + slot));
    }
    @Test void reordersContentsUsingAnEmptyScratchSlotAndThenRenamesBoxes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var snapshot = new PcOrganizerSnapshot(UUID.randomUUID(), 7,
            List.of("BREEDING", "MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "SORT BUFFER"),
            List.of(p(a, 0, 0, "a"), p(b, 1, 0, "b")));
        var plan = PcOrganizerBoxMovePlanner.plan(snapshot);
        assertEquals(List.of(1, 2, 3, 4, 5, 0, 6), plan.sourceOrder());
        assertFalse(plan.moves().isEmpty());
        assertEquals("MAIN", plan.renames().get(0).name());
        assertEquals("BREEDING", plan.renames().get(5).name());
    }
    private static PcOrganizerPokemon p(UUID id, int box, int slot, String species) {
        return new PcOrganizerPokemon(id, box, slot, species, "", "", false, false, "", "", "", 1, 0, 0);
    }
}

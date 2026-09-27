package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerCoordinatorTest {
    private static final UUID STORE = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test void previewNeverSends() {
        var port = new Port();
        var c = new PcOrganizerCoordinator(settings(PcOrganizerRunMode.PREVIEW), port);
        var result = c.tick(snapshot(A, B), 1);
        assertEquals(PcOrganizerCoordinator.Status.PREVIEW, result.status());
        assertTrue(port.moves.isEmpty());
    }

    @Test void applySendsOneMoveAndWaitsForExactAck() {
        var port = new Port();
        var c = new PcOrganizerCoordinator(settings(PcOrganizerRunMode.APPLY), port);
        var before = snapshot(B, A);
        assertEquals(PcOrganizerCoordinator.Status.REQUESTED, c.tick(before, 1).status());
        assertEquals(1, port.moves.size());
        assertEquals(1, port.moves.get(0).fromSlot());
        assertEquals(0, port.moves.get(0).toSlot());
        assertEquals(PcOrganizerCoordinator.Status.WAITING, c.tick(before, 2).status());
        assertEquals(1, port.moves.size());
        var ack = c.tick(snapshot(A, B), 3);
        assertEquals(PcOrganizerCoordinator.Status.ACK, ack.status(), ack.reason());
    }

    @Test void unexpectedChangeBlocksAndNeverRetries() {
        var port = new Port();
        var c = new PcOrganizerCoordinator(settings(PcOrganizerRunMode.APPLY), port);
        c.tick(snapshot(B, A), 1);
        var changed = snapshot(UUID.randomUUID(), A);
        assertEquals(PcOrganizerCoordinator.Status.BLOCKED, c.tick(changed, 2).status());
        assertEquals(PcOrganizerCoordinator.Status.BLOCKED, c.tick(snapshot(A, B), 3).status());
        assertEquals(1, port.moves.size());
    }

    private static PcOrganizerSettings settings(PcOrganizerRunMode mode) {
        return new PcOrganizerSettings(mode, PcOrganizerFilter.empty(), PcOrganizerSortKey.SPECIES,
            PcOrganizerSortKey.IV_TOTAL, PcOrganizerSortKey.ORIGINAL_SLOT,
            new PcOrganizerBoxPolicy(1, Set.of(), Map.of()));
    }

    private static PcOrganizerSnapshot snapshot(UUID first, UUID second) {
        return new PcOrganizerSnapshot(STORE, 1, List.of("Box"), List.of(
            pokemon(first, 0, "zubat"), pokemon(second, 1, "abra")));
    }
    private static PcOrganizerPokemon pokemon(UUID id, int slot, String species) {
        return new PcOrganizerPokemon(id, 0, slot, species, "", "", false, false, "", "", "", 1, 0, 0);
    }
    private static final class Port implements PcOrganizerCoordinator.MutationPort {
        final List<PcOrganizerPlan.Move> moves = new ArrayList<>();
        public void move(PcOrganizerPlan.Move move) { moves.add(move); }
    }
}

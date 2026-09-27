package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BattleMatrixStateTest {
    @Test void sixUnknownMoveFightersRemainVisibleAndRosterShrinkDoesNotLeaveStaleSlots() {
        var state = new BattleMatrixState();
        UUID id = UUID.randomUUID();
        state.update(snapshot(id, 6));
        assertEquals(6, BattleMatrixLayout.rows(state.groups()).size());
        assertEquals(6, state.enemies().size());
        assertFalse(state.update(snapshot(id, 2)));
        assertEquals(2, state.groups().size());
        assertEquals(2, state.enemies().size());
    }
    @Test void refusesSnapshotWhenLiveBattleChangedBeforeNextTick() {
        var state = new BattleMatrixState();
        var snapshot = snapshot(UUID.randomUUID(), 2);
        assertTrue(state.update(snapshot));
        assertSame(snapshot, state.snapshot(snapshot.battleId()));
        assertNull(state.snapshot(UUID.randomUUID()));
        assertNull(state.snapshot(null));
        assertTrue(state.update(snapshot(UUID.randomUUID(), 1)));
        assertNull(state.snapshot(snapshot.battleId()));
    }
    @Test void resetAndBattleEndRemoveOldRoster() {
        var state = new BattleMatrixState();
        state.update(snapshot(UUID.randomUUID(), 6));
        state.reset();
        assertNull(state.snapshot());
        assertTrue(state.groups().isEmpty());
        assertTrue(state.enemies().isEmpty());
        state.update(snapshot(UUID.randomUUID(), 6));
        assertTrue(state.update(null));
        assertTrue(state.groups().isEmpty());
        assertTrue(state.enemies().isEmpty());
    }
    private static MatchupSnapshot snapshot(UUID id, int size) {
        List<Fighter> roster = IntStream.range(0, size).mapToObj(i -> new Fighter(
            "slot-" + i, "Unknown", List.of(), List.of(), List.of(), -1, -1,
            false, false, "", "", false, "", List.of())).toList();
        return new MatchupSnapshot(id, 1, roster, roster, List.of());
    }
}

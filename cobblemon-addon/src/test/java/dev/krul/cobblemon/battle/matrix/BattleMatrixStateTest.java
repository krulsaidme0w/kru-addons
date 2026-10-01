package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fact;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Facts;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Freshness;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BattleMatrixStateTest {
    @Test void keepsOnlyActuallyRevealedOpponentsWhenTheyLeaveActiveSlots() {
        var state = new BattleMatrixState();
        UUID id = UUID.randomUUID();
        Fighter first = fighter("foe-1", "First");
        Fighter second = fighter("foe-2", "Second");
        state.update(new MatchupSnapshot(id, 1, List.of(), List.of(first), List.of()));
        state.update(new MatchupSnapshot(id, 2, List.of(), List.of(second), List.of()));

        assertEquals(List.of("foe-1", "foe-2"), state.enemies().stream().map(Fighter::id).toList());
        assertFalse(state.enemies().getFirst().active(), "previously revealed opponents must not stay marked active");
    }

    @Test void revealedOpponentRefreshesInPlaceAndNeverLeaksIntoNextBattle() {
        var state = new BattleMatrixState();
        UUID id = UUID.randomUUID();
        Fighter first = fighter("foe-1", "Old name");
        Fighter refreshed = fighter("foe-1", "Updated name");
        state.update(new MatchupSnapshot(id, 1, List.of(), List.of(first), List.of()));
        state.update(new MatchupSnapshot(id, 2, List.of(), List.of(refreshed), List.of()));
        assertEquals(List.of("Updated name"), state.enemies().stream().map(Fighter::name).toList());

        state.update(new MatchupSnapshot(UUID.randomUUID(), 1, List.of(), List.of(), List.of()));
        assertTrue(state.enemies().isEmpty());
    }

    @Test void switchesPreviousOpponentTypesFromCurrentToLastObserved() {
        var state = new BattleMatrixState();
        UUID id = UUID.randomUUID();
        Fighter visible = new Fighter("foe", "Visible", List.of(dev.krul.cobblemon.battle.PokemonType.FIRE),
            List.of(dev.krul.cobblemon.battle.PokemonType.FIRE), List.of(), 50, 100, true, false,
            "", "", false, "", List.of(), MatchupSnapshot.Portrait.UNKNOWN, null, false,
            new Facts(Fact.unknown(), new Fact<>(List.of(dev.krul.cobblemon.battle.PokemonType.FIRE),
                Freshness.CURRENT, "active projection"), Fact.unknown(), Fact.unknown(), Fact.unknown(), Fact.unknown()));
        state.update(new MatchupSnapshot(id, 1, List.of(), List.of(visible), List.of()));
        state.update(new MatchupSnapshot(id, 2, List.of(), List.of(), List.of()));

        assertEquals(Freshness.LAST_OBSERVED, state.enemies().getFirst().facts().types().freshness());
    }

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
    private static Fighter fighter(String id, String name) {
        return new Fighter(id, name, List.of(), List.of(), List.of(), 50, 100,
            true, false, "", "", false, "", List.of());
    }
}

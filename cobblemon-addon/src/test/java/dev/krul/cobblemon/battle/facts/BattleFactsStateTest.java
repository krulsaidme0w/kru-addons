package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleFactsStateTest {
    private final BattleFactsState state = new BattleFactsState();
    private final UUID battle = UUID.randomUUID();

    private MatchupSnapshot snapshot(UUID id, int count) {
        return new MatchupSnapshot(id, 3, IntStream.range(0, count).mapToObj(i -> BattleFactsModelTest.fighter("own-" + i)).toList(),
            IntStream.range(0, count).mapToObj(i -> BattleFactsModelTest.fighter("foe-" + i)).toList(), List.of());
    }

    @Test void eachSideHasIndependentBoundedColumnPaging() {
        state.update(snapshot(battle, 6));
        state.page(0, Integer.MAX_VALUE);
        assertEquals(4, state.offset(0));
        assertEquals(0, state.offset(1));
        state.page(0, Integer.MAX_VALUE);
        assertEquals(4, state.offset(0));
        state.page(0, Integer.MIN_VALUE);
        assertEquals(0, state.offset(0));
        state.page(1, 1);
        assertEquals(1, state.offset(1));
    }

    @Test void shrinkingRosterClampsOffsetsAndDropsMissingSelection() {
        state.update(snapshot(battle, 6));
        state.page(1, 4);
        state.select(1, 5);
        state.scrollDetails(20, 30, 4);
        state.update(snapshot(battle, 1));
        assertEquals(0, state.offset(1));
        assertNull(state.selected());
        assertEquals(0, state.detailOffset());
    }

    @Test void selectionTracksIdentityWhenRosterOrderChanges() {
        var first = snapshot(battle, 3);
        state.update(first);
        state.select(0, 1);
        state.update(new MatchupSnapshot(battle, 4, first.own().reversed(), first.enemies(), List.of()));
        assertEquals("own-1", state.selected().id());
        state.moveSelection(1);
        assertEquals("own-0", state.selected().id());
        assertEquals(1, state.offset(0));
    }

    @Test void detailScrollClampsAfterContentChangesAndKeyboardSelectionStaysVisible() {
        state.update(snapshot(battle, 6));
        state.select(0, 0);
        state.moveSelection(Integer.MAX_VALUE);
        assertEquals("own-5", state.selected().id());
        assertEquals(4, state.offset(0));
        state.scrollDetails(Integer.MAX_VALUE, 12, 4);
        assertEquals(8, state.detailOffset());
        state.clampDetails(3, 4);
        assertEquals(0, state.detailOffset());
        state.scrollDetails(Integer.MIN_VALUE, 12, 4);
        assertEquals(0, state.detailOffset());
    }

    @Test void battleReplacementAndDisconnectClearAllPresentationState() {
        state.update(snapshot(battle, 6));
        state.select(1, 3);
        state.page(1, 4);
        assertTrue(state.update(snapshot(UUID.randomUUID(), 6)));
        assertNull(state.selected());
        assertEquals(0, state.offset(1));
        state.select(0, 0);
        state.reset();
        assertNull(state.snapshot());
        assertNull(state.selected());
        assertTrue(state.team(0).isEmpty());
        assertEquals(0, state.offset(0));
    }

    @Test void emptyTeamsAndOutOfBoundsSelectionsAreSafe() {
        state.update(snapshot(battle, 0));
        state.select(0, 0);
        state.select(-1, 0);
        state.select(2, 0);
        state.page(1, 99);
        state.moveSelection(1);
        assertNull(state.selected());
        assertEquals(0, state.offset(1));
    }
}

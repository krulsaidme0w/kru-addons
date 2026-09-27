package dev.krul.cobblemon.gui;

import dev.krul.cobblemon.battle.PokemonType;
import org.junit.jupiter.api.Test;

import static dev.krul.cobblemon.battle.PokemonType.FIRE;
import static dev.krul.cobblemon.battle.PokemonType.GRASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeMatrixSelectionTest {
    @Test
    void rectangularCellGeometryMapsRowsToAttackAndColumnsToDefence() {
        TypeMatrixSelection state = new TypeMatrixSelection(30, 24, 22, 16);

        state.hover(30 + 4 * 22 + 11, 24 + 1 * 16 + 8);

        assertEquals(PokemonType.values()[1], state.hoveredAttack().orElseThrow());
        assertEquals(PokemonType.values()[4], state.hoveredDefend().orElseThrow());
    }

    @Test
    void secondCellClickAndEscapeClearLockedCrosshair() {
        TypeMatrixSelection state = new TypeMatrixSelection(30, 24, 22, 16);
        state.clickCell(FIRE, GRASS);
        assertTrue(state.locked());
        assertEquals(FIRE, state.activeAttack().orElseThrow());
        assertEquals(GRASS, state.activeDefend().orElseThrow());

        state.clickCell(FIRE, GRASS);
        assertFalse(state.locked());

        state.clickAttackHeader(FIRE);
        assertTrue(state.locked());
        state.clear();
        assertFalse(state.locked());
    }
}


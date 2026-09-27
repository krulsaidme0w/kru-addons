package dev.krul.cobblemon.dungeon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DungeonTargetTypeTest {
    @Test void mobsSuppressGeometryAndArrowsButLootKeepsThem() {
        assertTrue(DungeonTargetType.GUARDIAN.nametagOnly());
        assertTrue(DungeonTargetType.STRONG_POKEMON.nametagOnly());
        assertFalse(DungeonTargetType.CHEST.nametagOnly());
        assertFalse(DungeonTargetType.POKE_BALL.nametagOnly());
        assertFalse(DungeonTargetType.WEAPON.nametagOnly());
    }
}


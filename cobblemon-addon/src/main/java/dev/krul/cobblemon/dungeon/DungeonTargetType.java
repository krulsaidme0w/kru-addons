package dev.krul.cobblemon.dungeon;

public enum DungeonTargetType {
    CHEST,
    POKE_BALL,
    WEAPON,
    GUARDIAN,
    STRONG_POKEMON;

    public boolean nametagOnly() { return this == GUARDIAN || this == STRONG_POKEMON; }
}


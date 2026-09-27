package dev.krul.cobblemon.dungeon;

/** Independent visibility switches; alpha Pokémon use their own switch at every level. */
public record DungeonTargetFilters(boolean chests, boolean pokeBalls, boolean stakes,
                                   boolean guardians, boolean strongPokemon, boolean alphaPokemon) {
    public static final DungeonTargetFilters ALL = new DungeonTargetFilters(true, true, true, true, true, true);
}


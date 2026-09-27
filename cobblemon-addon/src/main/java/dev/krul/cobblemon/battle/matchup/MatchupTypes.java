package dev.krul.cobblemon.battle.matchup;

import java.util.List;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;

public final class MatchupTypes {
    private MatchupTypes() {}

    public record Resolved(List<PokemonType> types, boolean potential) {}

    public static Resolved resolve(Fighter fighter) {
        if (!fighter.types().isEmpty()) return new Resolved(fighter.types(), false);
        return new Resolved(fighter.baseTypes(), !fighter.baseTypes().isEmpty());
    }
}


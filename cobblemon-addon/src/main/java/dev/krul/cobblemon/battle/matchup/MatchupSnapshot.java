package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.BattleMove;
import dev.krul.cobblemon.battle.PokemonType;

import java.util.List;
import java.util.UUID;
import java.util.Set;

/** Only facts available to this client's current battle. No inferred enemy loadouts. */
public record MatchupSnapshot(UUID battleId, int turn, List<Fighter> own, List<Fighter> enemies, List<String> notes) {
    public MatchupSnapshot {
        own = List.copyOf(own);
        enemies = List.copyOf(enemies);
        notes = List.copyOf(notes);
    }

    public record Fighter(String id, String name, List<PokemonType> types, List<PokemonType> baseTypes,
                          List<Move> moves, int level, double hpPercent, boolean active, boolean fainted,
                          String ability, String item, boolean currentEffects, String status, List<String> notes, Portrait portrait,
                          PokemonType teraType, boolean teraActive, Facts facts) {
        /** Compatibility constructor: legacy calculator fields do not prove factual freshness. */
        public Fighter(String id, String name, List<PokemonType> types, List<PokemonType> baseTypes,
                       List<Move> moves, int level, double hpPercent, boolean active, boolean fainted,
                       String ability, String item, boolean currentEffects, String status, List<String> notes,
                       Portrait portrait, PokemonType teraType, boolean teraActive) {
            this(id, name, types, baseTypes, moves, level, hpPercent, active, fainted, ability, item,
                currentEffects, status, notes, portrait, teraType, teraActive, Facts.UNKNOWN);
        }
        public Fighter(String id, String name, List<PokemonType> types, List<PokemonType> baseTypes,
                       List<Move> moves, int level, double hpPercent, boolean active, boolean fainted,
                       String ability, String item, boolean currentEffects, String status, List<String> notes) {
            this(id, name, types, baseTypes, moves, level, hpPercent, active, fainted, ability, item, currentEffects, status, notes, Portrait.UNKNOWN, null, false);
        }
        public Fighter(String id, String name, List<PokemonType> types, List<PokemonType> baseTypes,
                       List<Move> moves, int level, double hpPercent, boolean active, boolean fainted,
                       String ability, String item, boolean currentEffects, String status, List<String> notes, Portrait portrait) {
            this(id, name, types, baseTypes, moves, level, hpPercent, active, fainted, ability, item, currentEffects, status, notes, portrait, null, false);
        }
        public Fighter {
            types = List.copyOf(types);
            baseTypes = List.copyOf(baseTypes);
            moves = List.copyOf(moves);
            notes = List.copyOf(notes);
            ability = ability == null ? "" : ability;
            item = item == null ? "" : item;
            status = status == null ? "" : status;
            portrait = portrait == null ? Portrait.UNKNOWN : portrait;
            facts = facts == null ? Facts.UNKNOWN : facts;
        }
    }

    public enum Freshness { UNKNOWN, CURRENT, LAST_OBSERVED, PROFILE }

    /** A value and its actual observation source; absence is never a neutral/default value. */
    public record Fact<T>(T value, Freshness freshness, String source) {
        public Fact {
            freshness = freshness == null || value == null ? Freshness.UNKNOWN : freshness;
            source = source == null ? "" : source;
        }
        public static <T> Fact<T> unknown() { return new Fact<>(null, Freshness.UNKNOWN, "Not supplied by client"); }
        public boolean known() { return value != null && freshness != Freshness.UNKNOWN; }
    }

    /** Exact HP is populated only for the player's own observed flat HP. */
    public record Health(int current, int maximum, double percent) {
        public boolean exact() { return current >= 0 && maximum > 0; }
    }

    /** Presentation facts kept separate from the older matchup calculator's base-type assumptions. */
    public record Facts(Fact<Health> health, Fact<List<PokemonType>> types, Fact<String> status,
                        Fact<List<String>> changes, Fact<String> ability, Fact<String> item) {
        public static final Facts UNKNOWN = new Facts(Fact.unknown(), Fact.unknown(), Fact.unknown(),
            Fact.unknown(), Fact.unknown(), Fact.unknown());
        public Facts {
            health = health == null ? Fact.unknown() : health;
            types = immutable(types);
            status = status == null ? Fact.unknown() : status;
            changes = immutable(changes);
            ability = ability == null ? Fact.unknown() : ability;
            item = item == null ? Fact.unknown() : item;
        }
        private static <T> Fact<List<T>> immutable(Fact<List<T>> fact) {
            return fact == null || !fact.known() ? Fact.unknown()
                : new Fact<>(List.copyOf(fact.value()), fact.freshness(), fact.source());
        }
    }

    /** Render metadata only; never derive it from a nickname or an ambiguous roster projection. */
    public record Portrait(String speciesId, Set<String> aspects) {
        public static final Portrait UNKNOWN = new Portrait("", Set.of());
        public Portrait {
            speciesId = speciesId == null ? "" : speciesId;
            aspects = aspects == null ? Set.of() : Set.copyOf(aspects);
        }
    }

    /** pp=-1 means unknown; availabilityKnown distinguishes active requests from bench/revealed moves. */
    public record Move(BattleMove data, int pp, boolean disabled, boolean availabilityKnown, boolean special) {}
}

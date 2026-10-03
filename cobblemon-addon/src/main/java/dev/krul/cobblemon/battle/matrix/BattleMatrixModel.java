package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.MoveCategory;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.TypeEffectiveness;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fact;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Freshness;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Move;

import java.util.ArrayList;
import java.util.List;

/** Pure projection and type-chart math. It never ranks moves or estimates damage. */
public final class BattleMatrixModel {
    private BattleMatrixModel() {}

    public record MoveGroup(Fighter fighter, List<Move> moves) {
        public MoveGroup { moves = List.copyOf(moves); }
    }
    public record Coverage(int covered, int revealed) {
        public String label() { return covered + "/" + revealed; }
    }
    public record Potential(BattleMatrixCell cell, PokemonType attackType) {}

    public static List<MoveGroup> groups(MatchupSnapshot snapshot) {
        if (snapshot == null) return List.of();
        List<MoveGroup> result = new ArrayList<>();
        for (Fighter fighter : snapshot.own().stream().limit(6).toList()) {
            List<Move> known = fighter.moves().stream()
                .filter(move -> move != null && move.data() != null)
                .limit(4)
                .toList();
            result.add(new MoveGroup(fighter, known));
        }
        return List.copyOf(result);
    }

    public static List<Fighter> enemies(MatchupSnapshot snapshot) {
        if (snapshot == null) return List.of();
        return snapshot.enemies().stream().limit(6).toList();
    }

    /** Counts revealed opponents with at least one confirmed super-effective known move. */
    public static Coverage coverage(List<Move> moves, List<Fighter> enemies) {
        if (moves == null || enemies == null) return new Coverage(0, 0);
        int revealed = 0, covered = 0;
        for (Fighter enemy : enemies) {
            if (enemy == null || currentTypes(enemy).types().isEmpty()) continue;
            revealed++;
            if (moves.stream().anyMatch(move -> {
                BattleMatrixCell result = evaluate(move, enemy);
                return result.multiplier() != null && result.multiplier() > 1;
            })) covered++;
        }
        return new Coverage(covered, revealed);
    }

    /** Advisory learnset ceiling; kept separate from confirmed-move coverage. */
    public static Potential potential(List<PokemonType> possibleAttackTypes, Fighter target) {
        if (target == null || target.fainted() || possibleAttackTypes == null || possibleAttackTypes.isEmpty())
            return potentialUnknown("No possible ordinary attack type or target is unavailable.", null, target);
        TypeBasis basis = potentialTargetTypes(target);
        if (basis.types().isEmpty()) return potentialUnknown("Target species/types are unknown; no potential value inferred.", null, target);

        PokemonType bestType = null;
        double best = -1;
        for (PokemonType type : new java.util.LinkedHashSet<>(possibleAttackTypes)) {
            if (type == null) continue;
            double multiplier = TypeEffectiveness.multiplier(type, basis.types().toArray(PokemonType[]::new));
            if (multiplier > best) { best = multiplier; bestType = type; }
        }
        if (bestType == null) return potentialUnknown("No possible ordinary attack type is available.", null, target);
        String label = "~" + format(best);
        List<String> tooltip = new ArrayList<>();
        tooltip.add("Potential " + bestType.label() + " attack -> " + target.name() + ": " + label + "x by type chart only.");
        tooltip.add("Possible ordinary move from the species/form learnset; not confirmed in this Pokemon's moveset or usable now.");
        tooltip.add(basis.description() + (basis.description().endsWith(".") ? "" : "."));
        tooltip.add("Not damage, a guaranteed move, or a safe-switch recommendation; ability/item/weather effects are excluded.");
        return new Potential(new BattleMatrixCell(label, best, BattleMatrixCell.Evidence.POTENTIAL, tooltip), bestType);
    }

    /** Counts only identified targets whose current or species-baseline typing can be evaluated. */
    public static Coverage potentialCoverage(List<PokemonType> possibleAttackTypes, List<Fighter> enemies) {
        if (possibleAttackTypes == null || enemies == null) return new Coverage(0, 0);
        int revealed = 0, covered = 0;
        for (Fighter enemy : enemies) {
            if (enemy == null || potentialTargetTypes(enemy).types().isEmpty()) continue;
            revealed++;
            var candidate = potential(possibleAttackTypes, enemy).cell();
            if (candidate.multiplier() != null && candidate.multiplier() > 1) covered++;
        }
        return new Coverage(covered, revealed);
    }

    public static BattleMatrixCell evaluate(Move move, Fighter target) {
        if (move == null || move.data() == null) return unknown("Move details are unknown.", move, target);
        if (move.data().category() == MoveCategory.STATUS) {
            return cell("—", null, BattleMatrixCell.Evidence.NOT_APPLICABLE,
                "Status move; no type-effectiveness multiplier.", move, target);
        }
        if (move.special() || move.data().category() == MoveCategory.UNKNOWN) {
            return unknown("Special or dynamic move semantics are not represented by the ordinary type chart; nominal type is not confirmed.", move, target);
        }
        if (move.data().type() == null) return unknown("Move type unknown.", move, target);
        if (target == null) return unknown("Target unknown.", move, null);

        TypeBasis basis = currentTypes(target);
        if (basis.types().isEmpty()) {
            return unknown("Current target typing is not confirmed. No species, profile or last-observed fallback.", move, target);
        }

        double multiplier = TypeEffectiveness.multiplier(move.data().type(), basis.types().toArray(PokemonType[]::new));
        String value = format(multiplier);
        return cell(value, multiplier, basis.evidence(), basis.description(), move, target);
    }

    private static TypeBasis currentTypes(Fighter target) {
        Fact<List<PokemonType>> fact = target.facts().types();
        if (fact.known() && !fact.value().isEmpty() && fact.freshness() == Freshness.CURRENT) {
            return new TypeBasis(fact.value(), BattleMatrixCell.Evidence.CURRENT,
                "Current battle types" + source(fact.source()));
        }
        return new TypeBasis(List.of(), BattleMatrixCell.Evidence.UNKNOWN, "");
    }

    private static TypeBasis potentialTargetTypes(Fighter target) {
        TypeBasis current = currentTypes(target);
        if (!current.types().isEmpty()) return current;
        if (target.portrait().speciesId().isBlank() || target.baseTypes().isEmpty())
            return new TypeBasis(List.of(), BattleMatrixCell.Evidence.UNKNOWN, "");
        return new TypeBasis(target.baseTypes(), BattleMatrixCell.Evidence.POTENTIAL,
            "Species/form baseline defensive types (battle typing may differ)");
    }

    private static Potential potentialUnknown(String reason, PokemonType attackType, Fighter target) {
        return new Potential(new BattleMatrixCell("?", null, BattleMatrixCell.Evidence.UNKNOWN,
            List.of(reason, "Potential analysis is advisory only.")), attackType);
    }

    private static BattleMatrixCell unknown(String reason, Move move, Fighter target) {
        return cell("?", null, BattleMatrixCell.Evidence.UNKNOWN, reason, move, target);
    }

    private static BattleMatrixCell cell(String label, Double multiplier, BattleMatrixCell.Evidence evidence,
                                         String basis, Move move, Fighter target) {
        List<String> tooltip = new ArrayList<>();
        String moveName = move == null || move.data() == null ? "Unknown move" : move.data().name();
        String targetName = target == null || target.name().isBlank() ? "Unknown target" : target.name();
        tooltip.add(moveName + " -> " + targetName + ": " + label + (multiplier == null ? "" : "x"));
        if (multiplier != null) tooltip.add("Reference only: type chart; not damage. Abilities, items and weather are not included.");
        if (basis != null && !basis.isBlank()) tooltip.add(basis + (basis.endsWith(".") ? "" : "."));
        if (move != null && move.availabilityKnown()) {
            String pp = move.pp() < 0 ? "PP unknown" : "PP " + move.pp();
            tooltip.add(pp + (move.disabled() ? "; disabled" : "; currently available"));
        }
        return new BattleMatrixCell(label, multiplier, evidence, tooltip);
    }

    private static String source(String source) {
        return source == null || source.isBlank() ? "" : " (" + source + ")";
    }

    private static String format(double value) {
        if (value == .25) return "¼";
        if (value == .5) return "½";
        if (value == Math.rint(value)) return Integer.toString((int) value);
        return Double.toString(value);
    }

    private record TypeBasis(List<PokemonType> types, BattleMatrixCell.Evidence evidence, String description) {
        private TypeBasis { types = List.copyOf(types); }
    }
}

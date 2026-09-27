package dev.krul.cobblemon.battle.matchup;

import java.util.*;
import java.util.regex.Pattern;
import dev.krul.cobblemon.battle.*;
import static dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;

public final class MatchupCalculator {
    private static final Pattern STAT_MULTIPLIER = Pattern.compile("([A-Z_]+)=(-?[0-9]+(?:\\.[0-9]+)?)");
    public record Attack(String move, Double multiplier, double stab, String reason) {}
    /** A possible ordinary attack of the enemy's types, never a revealed move or probability. */
    public record Potential(PokemonType type, Double multiplier) {}
    public record Cell(String ownId, String enemyId, Attack outgoing, Attack incoming, Potential potential,
                       boolean unavailable, List<String> details) {}

    public static List<Cell> calculate(MatchupSnapshot snapshot) {
        List<Cell> cells = new ArrayList<>();
        for (Fighter enemy : snapshot.enemies()) for (Fighter mine : snapshot.own()) {
            Attack outgoing = best(mine, enemy);
            Attack incoming = best(enemy, mine);
            Potential potential = potential(enemy, mine);
            List<String> details = new ArrayList<>();
            details.add(mine.name() + " Lv." + mine.level() + " vs " + enemy.name() + " Lv." + enemy.level());
            details.add("HP: yours " + health(mine) + " / opponent " + health(enemy));
            details.add("Hit: " + attackSummary(outgoing, mine, false));
            details.add("Reply: " + attackSummary(incoming, enemy, true));
            details.add("Potential: " + (potential.type() == null ? "?" : potential.type().label() + " " + label(potential.multiplier()) + " (by type; move not confirmed)"));
            if (mine.teraActive()) details.add("Your Tera: " + teraLabel(mine) + "; current type used for this chart.");
            if (enemy.teraActive()) details.add("Enemy Tera: " + teraLabel(enemy) + "; risk uses the current Tera type.");
            List<String> warnings = new ArrayList<>();
            if (mine.fainted() || enemy.fainted()) warnings.add("fainted Pokemon; unavailable pair");
            else if (mine.hpPercent() >= 0 && mine.hpPercent() <= 25) warnings.add("low HP");
            if (mine.level() > 0 && enemy.level() - mine.level() >= 5) warnings.add("opponent +" + (enemy.level() - mine.level()) + " levels");
            if (!mine.status().isBlank()) warnings.add(mine.status());
            for (String note : mine.notes()) {
                if (note.contains("trapped")) warnings.add("switching may be blocked");
                if (note.contains("forced switch")) warnings.add("switch required");
            }
            if (!warnings.isEmpty()) details.add("Careful: " + String.join("; ", warnings));
            cells.add(new Cell(mine.id(), enemy.id(), outgoing, incoming, potential, mine.fainted() || enemy.fainted(), List.copyOf(details)));
        }
        return List.copyOf(cells);
    }

    private static Potential potential(Fighter attacker, Fighter defender) {
        MatchupTypes.Resolved attackerTypes = MatchupTypes.resolve(attacker);
        MatchupTypes.Resolved defenderTypes = MatchupTypes.resolve(defender);
        if (attacker.fainted() || defender.fainted() || attackerTypes.types().isEmpty() || defenderTypes.types().isEmpty()) return new Potential(null, null);
        Potential worst = new Potential(null, null);
        for (PokemonType type : new LinkedHashSet<>(attackerTypes.types())) {
            double value = TypeEffectiveness.multiplier(type, defenderTypes.types().toArray(PokemonType[]::new));
            if (!immunity(attacker, defender, type, value).isEmpty()) value = 0;
            if (worst.multiplier() == null || value > worst.multiplier()) worst = new Potential(type, value);
        }
        return worst;
    }
    public static boolean potentialDominates(Cell cell) {
        return cell.potential().multiplier() != null && (cell.incoming().multiplier() == null
            || cell.potential().multiplier() > cell.incoming().multiplier());
    }
    public static Double risk(Cell cell) {
        return potentialDominates(cell) ? cell.potential().multiplier() : cell.incoming().multiplier();
    }

    private static Attack best(Fighter attacker, Fighter defender) {
        if (attacker.fainted() || defender.fainted()) return unknown("Fainted: not an available matchup.");
        if (MatchupTypes.resolve(defender).types().isEmpty()) return unknown("Defender types unknown.");
        Attack best = null;
        for (Move candidate : attacker.moves()) {
            Attack attack = assess(candidate, attacker, defender);
            if (attack.multiplier() != null && (best == null || attack.multiplier() > best.multiplier())) best = attack;
        }
        if (best == null) return unknown("No ordinary usable attack known (PP / disabled / status / special / unrevealed).");
        return best;
    }

    private static Attack assess(Move candidate, Fighter attacker, Fighter defender) {
        if (attacker.fainted() || defender.fainted()) return unknown("Fainted: not an available matchup.");
        if (candidate.disabled()) return unknown("Disabled.");
        if (candidate.pp() == 0) return unknown("No PP.");
        if (candidate.special()) return unknown("Special mechanics; not evaluated.");
        BattleMove move = candidate.data();
        if (!move.damaging()) return unknown("Status / unknown move; no ordinary multiplier.");
        List<PokemonType> defenderTypes = MatchupTypes.resolve(defender).types();
        if (defenderTypes.isEmpty()) return unknown("Defender types unknown.");
        double value = TypeEffectiveness.multiplier(move.type(), defenderTypes.toArray(PokemonType[]::new));
        String immunity = immunity(attacker, defender, move.type(), value);
        if (!immunity.isEmpty()) value = 0;
        String reason = immunity.isEmpty() ? "By known defensive types." : immunity;
        if (!candidate.availabilityKnown()) reason += " PP/availability not confirmed for this matchup.";
        if (!defender.currentEffects()) reason += " Ability/item effects not confirmed current.";
        double stab = attacker.baseTypes().contains(move.type()) || attacker.teraActive() && attacker.teraType() == move.type() ? 1.5 : 1;
        return new Attack(move.name(), value, stab, reason);
    }

    private static String immunity(Fighter attacker, Fighter defender, PokemonType type, double multiplier) {
        if (!defender.currentEffects()) return "";
        String ability = normalize(defender.ability());
        boolean bypass = attacker.currentEffects() && Set.of("moldbreaker", "teravolt", "turboblaze").contains(normalize(attacker.ability()));
        if (!bypass) {
            boolean immune = switch (type) {
                case GROUND -> ability.equals("levitate");
                case FIRE -> Set.of("flashfire", "wellbakedbody").contains(ability);
                case WATER -> Set.of("waterabsorb", "stormdrain", "dryskin").contains(ability);
                case ELECTRIC -> Set.of("voltabsorb", "lightningrod", "motordrive").contains(ability);
                case GRASS -> ability.equals("sapsipper");
                default -> false;
            };
            if (immune || (ability.equals("wonderguard") && multiplier <= 1)) return "Known current ability: " + defender.ability();
        }
        if (type == PokemonType.GROUND && normalize(defender.item()).equals("airballoon")) return "Known current item: Air Balloon";
        return "";
    }

    public static List<String> fighterDetails(Fighter fighter, Fighter defender) {
        List<String> lines = new ArrayList<>();
        lines.add(fighter.name() + " Lv." + fighter.level() + " | HP " + health(fighter));
        MatchupTypes.Resolved resolvedTypes = MatchupTypes.resolve(fighter);
        lines.add("Types: " + types(resolvedTypes.types()) + (resolvedTypes.potential() ? " (base; current unavailable)" : ""));
        if (fighter.teraActive()) lines.add("Tera: " + (fighter.teraType() == null ? "unknown" : fighter.teraType().label()) + " (active; current chart used)");
        else if (fighter.teraType() != null) lines.add("Tera type: " + fighter.teraType().label() + " (not active)");
        lines.add("Ability: " + unknownLabel(fighter.ability()) + (fighter.ability().isBlank() ? "" : fighter.currentEffects() ? " (current)" : " (reported)"));
        lines.add("Item: " + unknownLabel(fighter.item()));
        if (!fighter.status().isBlank()) lines.add("Status: " + fighter.status());
        if (fighter.moves().isEmpty()) lines.add("Moves: not revealed");
        for (Move move : fighter.moves()) {
            Attack attack = assess(move, fighter, defender);
            String effect = move.disabled() ? "Disabled" : move.pp() == 0 ? "No PP"
                : move.data().category() == MoveCategory.STATUS ? "Status"
                : attack.multiplier() == null ? shortReason(attack.reason())
                : (move.data().category() == MoveCategory.PHYSICAL ? "Physical " : "Special ") + label(attack.multiplier()) + effectQualifier(attack);
            lines.add(move.data().name() + " | " + (move.data().type() == null ? "?" : move.data().type().label())
                + " | " + effect + " | PP " + (move.pp() < 0 ? "?" : move.pp()));
        }
        for (String note : fighter.notes()) {
            if (note.startsWith("Reported buffs:")) {
                var matches = STAT_MULTIPLIER.matcher(note);
                List<String> changes = new ArrayList<>();
                while (matches.find()) {
                    double value = Double.parseDouble(matches.group(2));
                    if (value != 1 && Double.isFinite(value)) changes.add(statName(matches.group(1)) + " x"
                        + java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                }
                if (!changes.isEmpty()) lines.add("Changes: " + String.join(", ", changes));
            } else if (note.contains("trapped")) lines.add("Switching may be blocked");
            else if (note.contains("forced switch")) lines.add("Switch required");
            else if (note.contains("Special Tera")) lines.add("Special Tera effects not evaluated");
            else if (note.contains("Terastallized")) lines.add("Tera active; damage/STAB not simulated");
            else if (note.toLowerCase(Locale.ROOT).contains("ambiguous") || note.contains("Unrevealed") || note.toLowerCase(Locale.ROOT).contains("identity")) lines.add("Identity not confirmed");
        }
        if (fighter.moves().stream().anyMatch(m -> !m.availabilityKnown())) lines.add("Move availability not confirmed");
        if (!fighter.active()) lines.add("Bench: HP/types last reported");
        if (!fighter.currentEffects()) lines.add("Current ability/item effects unconfirmed");
        if (!fighter.types().isEmpty()) for (double multiplier : new double[]{4, 2, .5, .25, 0}) {
            var attacks = Arrays.stream(PokemonType.values()).filter(t -> TypeEffectiveness.multiplier(t, fighter.types().toArray(PokemonType[]::new)) == multiplier)
                .map(PokemonType::label).toList();
            if (!attacks.isEmpty()) lines.add("Takes " + label(multiplier) + " by type: " + String.join(", ", attacks));
        }
        return List.copyOf(lines);
    }

    private static String attackSummary(Attack attack, Fighter fighter, boolean revealed) {
        if (attack.multiplier() == null) return "? " + shortReason(attack.reason());
        var move = fighter.moves().stream().filter(m -> m.data().name().equals(attack.move())).findFirst().orElse(null);
        String type = move == null || move.data().type() == null ? "?" : move.data().type().label();
        return attack.move() + " | " + type + " " + label(attack.multiplier()) + effectQualifier(attack)
            + (revealed ? " | revealed" : " | PP " + (move == null || move.pp() < 0 ? "?" : move.pp())
            + (move != null && !move.availabilityKnown() ? " (availability ?)" : ""));
    }
    private static String effectQualifier(Attack attack) {
        if (attack.reason().startsWith("Known current ")) return " (" + attack.reason().split(": ", 2)[1].split("\\.", 2)[0] + ")";
        return attack.reason().contains("Ability/item effects not confirmed current.") ? " (type only)" : "";
    }
    private static String shortReason(String reason) {
        if (reason.contains("Fainted")) return "Fainted";
        if (reason.contains("types unknown")) return "Target types unknown";
        if (reason.contains("Disabled")) return "Disabled";
        if (reason.contains("No PP")) return "No PP";
        if (reason.contains("Special mechanics")) return "Special effect";
        return "No usable damage move known";
    }
    private static String types(List<PokemonType> types) { return types.isEmpty() ? "?" : String.join(" / ", types.stream().map(PokemonType::label).toList()); }
    private static String teraLabel(Fighter fighter) { return fighter.teraType() == null ? "unknown" : fighter.teraType().label(); }
    private static String health(Fighter fighter) { return fighter.fainted() ? "FNT" : fighter.hpPercent() < 0 ? "?" : (fighter.active() ? "" : "~") + Math.round(fighter.hpPercent()) + "%"; }
    private static String statName(String stat) {
        return switch (stat) {
            case "SPECIAL_ATTACK", "SP_ATTACK" -> "Sp. Atk";
            case "SPECIAL_DEFENCE", "SPECIAL_DEFENSE", "SP_DEFENCE", "SP_DEFENSE" -> "Sp. Def";
            default -> stat.charAt(0) + stat.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static Attack unknown(String reason) { return new Attack("?", null, 1, reason); }
    private static String unknownLabel(String value) { return value.isBlank() ? "?" : value; }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replace("minecraft:", "").replaceAll("[^a-z0-9]", ""); }
    public static String label(Double value) {
        if (value == null) return "?";
        return "x" + (value == Math.rint(value) ? Integer.toString(value.intValue()) : Double.toString(value));
    }
}

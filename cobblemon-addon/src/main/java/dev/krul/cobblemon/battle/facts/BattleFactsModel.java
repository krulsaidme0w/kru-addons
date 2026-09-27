package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Read-only presentation. No species-derived current types or numeric stat estimates. */
final class BattleFactsModel {
    private BattleFactsModel() {}

    static String name(Fighter fighter) {
        return fighter.name() == null || fighter.name().isBlank() || fighter.name().equals("Unknown")
            || fighter.name().equals("Own Pokemon") ? "?" : fighter.name();
    }

    static String position(Fighter fighter) {
        if (fighter.fainted()) {
            var hp = fighter.facts().health();
            return hp.known() && hp.freshness() == Freshness.CURRENT && hp.value().percent() == 0 ? "Fainted" : "~Fainted";
        }
        if (fighter.active()) return "Active";
        return name(fighter).equals("?") ? "Unrevealed" : "Bench";
    }

    static String level(Fighter fighter) { return fighter.level() > 0 ? String.valueOf(fighter.level()) : "?"; }

    static String types(Fact<List<PokemonType>> fact) {
        return !fact.known() || fact.value().isEmpty() ? "?" : marker(fact) + typeNames(fact.value());
    }

    static String typeNames(List<PokemonType> types) {
        return types.isEmpty() ? "?" : types.stream().map(PokemonType::label).collect(Collectors.joining(" / "));
    }

    static String health(Fact<Health> fact) {
        if (!fact.known()) return "?";
        Health hp = fact.value();
        if (!Double.isFinite(hp.percent()) || hp.percent() < 0 || hp.percent() > 100) return "?";
        return marker(fact) + (hp.exact() ? hp.current() + "/" + hp.maximum()
            : String.format(Locale.ROOT, "%.1f%%", hp.percent()));
    }

    static String value(Fact<String> fact) {
        return !fact.known() || fact.value().isBlank() ? "?" : marker(fact) + fact.value();
    }

    static String marker(Fact<?> fact) { return fact.freshness() == Freshness.CURRENT ? "" : "~"; }

    static String provenance(Fact<?> fact) {
        if (!fact.known()) return "unavailable";
        return switch (fact.freshness()) {
            case CURRENT -> "current";
            case LAST_OBSERVED -> "last observed";
            case PROFILE -> "profile; current battle value unknown";
            case UNKNOWN -> "unavailable";
        } + " | " + fact.source();
    }

    static List<String> details(Fighter fighter, boolean own) {
        Facts facts = fighter.facts();
        List<String> lines = new ArrayList<>();
        lines.add("Name: " + name(fighter) + " | " + position(fighter));
        lines.add("HP: " + health(facts.health()) + " | " + provenance(facts.health()));
        lines.add("Types: " + types(facts.types()) + " | " + provenance(facts.types()));
        lines.add("Status: " + value(facts.status()) + " | " + provenance(facts.status()));
        lines.add("Ability: " + value(facts.ability()) + " | " + provenance(facts.ability()));
        lines.add("Item: " + value(facts.item()) + " | " + provenance(facts.item()));
        lines.add("Changes: " + (!facts.changes().known() || facts.changes().value().isEmpty() ? "?"
            : String.join(", ", facts.changes().value())) + " | " + provenance(facts.changes()));
        lines.add("Base types (species/profile only): " + typeNames(fighter.baseTypes()));
        lines.add("Level: " + level(fighter) + " | " + (own ? "own profile/request" : "last revealed projection"));
        lines.add(own ? "Known moves (profile/request):" : "Revealed moves (last observed):");
        if (fighter.moves().isEmpty()) lines.add("  ? | No move data supplied");
        for (MatchupSnapshot.Move move : fighter.moves()) {
            String moveName = move.data().name();
            if (moveName == null || moveName.isBlank()) moveName = move.data().id();
            lines.add("  " + moveName + " | PP " + (move.pp() >= 0 ? move.pp() : "?")
                + (move.availabilityKnown() ? " (request)" + (move.disabled() ? " | disabled in request" : "")
                    : " (last reported); availability ?"));
        }
        // Old reader notes include calculator terminology. Only structured factual fields are presented.
        return List.copyOf(lines);
    }

    /** Pixel wrapping keeps every revealed detail reachable without horizontal clipping. */
    static List<String> wrap(List<String> lines, int width, ToIntFunction<String> measure) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String remaining = line;
            while (measure.applyAsInt(remaining) > width && !remaining.isEmpty()) {
                int end = 0, space = -1;
                while (end < remaining.length()) {
                    int next = remaining.offsetByCodePoints(end, 1);
                    if (end > 0 && measure.applyAsInt(remaining.substring(0, next)) > width) break;
                    if (remaining.charAt(end) == ' ') space = end;
                    end = next;
                }
                if (space > 0) end = space;
                result.add(remaining.substring(0, end));
                remaining = remaining.substring(end).stripLeading();
            }
            if (!remaining.isEmpty()) result.add(remaining);
        }
        return List.copyOf(result);
    }
}

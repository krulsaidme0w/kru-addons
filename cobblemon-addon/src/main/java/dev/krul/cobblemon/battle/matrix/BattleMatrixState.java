package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Battle-scoped immutable roster projection; the complete roster fits without paging. */
public final class BattleMatrixState {
    private MatchupSnapshot snapshot;
    private List<BattleMatrixModel.MoveGroup> groups = List.of();
    private List<Fighter> enemies = List.of();

    /** @return true when a new battle identity replaced the prior state. */
    public boolean update(MatchupSnapshot next) {
        boolean changed = !Objects.equals(snapshot == null ? null : snapshot.battleId(), next == null ? null : next.battleId());
        if (next == null) {
            reset();
            return changed;
        }
        List<Fighter> enemies = changed ? next.enemies() : mergeRevealed(this.enemies, next.enemies());
        snapshot = changed || enemies.equals(next.enemies()) ? next
            : new MatchupSnapshot(next.battleId(), next.turn(), next.own(), enemies, next.notes());
        groups = BattleMatrixModel.groups(next);
        this.enemies = BattleMatrixModel.enemies(snapshot);
        return changed;
    }

    private static List<Fighter> mergeRevealed(List<Fighter> previous, List<Fighter> current) {
        java.util.LinkedHashMap<String, Fighter> byId = new java.util.LinkedHashMap<>();
        for (Fighter fighter : previous) if (revealed(fighter)) byId.put(fighter.id(), inactive(fighter));
        for (Fighter fighter : current) if (revealed(fighter)) byId.put(fighter.id(), fighter);
        for (Fighter fighter : current) if (fighter != null && !revealed(fighter) && !fighter.id().isBlank())
            byId.putIfAbsent(fighter.id(), fighter);
        return byId.values().stream().limit(6).toList();
    }

    private static boolean revealed(Fighter fighter) {
        return fighter != null && fighter.id() != null && !fighter.id().isBlank()
            && ((fighter.name() != null && !fighter.name().isBlank() && !fighter.name().equalsIgnoreCase("Unknown"))
                || !fighter.portrait().speciesId().isBlank() || !fighter.types().isEmpty() || !fighter.moves().isEmpty());
    }

    private static Fighter inactive(Fighter fighter) {
        if (!fighter.active()) return fighter;
        List<String> notes = new java.util.ArrayList<>(fighter.notes());
        notes.add("Previously revealed; no longer the currently visible opponent.");
        return new Fighter(fighter.id(), fighter.name(), fighter.types(), fighter.baseTypes(), fighter.moves(),
            fighter.level(), fighter.hpPercent(), false, fighter.fainted(), fighter.ability(), fighter.item(),
            fighter.currentEffects(), fighter.status(), notes, fighter.portrait(), fighter.teraType(),
            fighter.teraActive(), lastObserved(fighter.facts()));
    }

    private static MatchupSnapshot.Facts lastObserved(MatchupSnapshot.Facts facts) {
        return new MatchupSnapshot.Facts(lastObserved(facts.health()), lastObserved(facts.types()),
            lastObserved(facts.status()), lastObserved(facts.changes()), lastObserved(facts.ability()),
            lastObserved(facts.item()));
    }

    private static <T> MatchupSnapshot.Fact<T> lastObserved(MatchupSnapshot.Fact<T> fact) {
        if (!fact.known() || fact.freshness() != MatchupSnapshot.Freshness.CURRENT) return fact;
        return new MatchupSnapshot.Fact<>(fact.value(), MatchupSnapshot.Freshness.LAST_OBSERVED, fact.source());
    }
    public MatchupSnapshot snapshot() { return snapshot; }
    public MatchupSnapshot snapshot(UUID liveBattleId) {
        return snapshot != null && liveBattleId != null && Objects.equals(snapshot.battleId(), liveBattleId) ? snapshot : null;
    }
    public List<BattleMatrixModel.MoveGroup> groups() { return groups; }
    public List<Fighter> enemies() { return enemies; }
    public void reset() {
        snapshot = null;
        groups = List.of();
        enemies = List.of();
    }
}

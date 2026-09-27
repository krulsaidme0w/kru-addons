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
        snapshot = next;
        groups = BattleMatrixModel.groups(next);
        enemies = BattleMatrixModel.enemies(next);
        return changed;
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

package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;

import java.util.List;
import java.util.Objects;

/** Bounded local navigation only; never retains battle observations outside the current snapshot. */
final class BattleFactsState {
    static final int COLUMNS = 2;
    private MatchupSnapshot snapshot;
    private final int[] offsets = new int[2];
    private String selectedId;
    private int selectedSide;
    private int detailOffset;

    boolean update(MatchupSnapshot next) {
        boolean changed = snapshot != null && !Objects.equals(snapshot.battleId(), next.battleId());
        if (changed || next.battleId() == null) reset();
        snapshot = next;
        for (int side = 0; side < 2; side++) offsets[side] = clamp(offsets[side], team(side).size() - COLUMNS);
        if (selected() == null) {
            selectedId = null;
            detailOffset = 0;
        }
        return changed;
    }

    MatchupSnapshot snapshot() { return snapshot; }
    List<Fighter> team(int side) { return snapshot == null ? List.of() : side == 0 ? snapshot.own() : snapshot.enemies(); }
    int offset(int side) { return offsets[side]; }
    int selectedSide() { return selectedSide; }
    int detailOffset() { return detailOffset; }
    Fighter selected() {
        return selectedId == null ? null : team(selectedSide).stream().filter(f -> selectedId.equals(f.id())).findFirst().orElse(null);
    }
    boolean isSelected(int side, Fighter fighter) { return side == selectedSide && Objects.equals(selectedId, fighter.id()); }

    void page(int side, int direction) {
        offsets[side] = clamp((long) offsets[side] + direction, team(side).size() - COLUMNS);
    }

    void select(int side, int index) {
        if (side < 0 || side > 1 || index < 0 || index >= team(side).size()) return;
        selectedSide = side;
        selectedId = team(side).get(index).id();
        detailOffset = 0;
    }

    void moveSelection(int direction) {
        if (selected() == null) return;
        List<Fighter> team = team(selectedSide);
        int index = team.indexOf(selected());
        int next = clamp((long) index + direction, team.size() - 1);
        select(selectedSide, next);
        if (next < offsets[selectedSide]) offsets[selectedSide] = next;
        if (next >= offsets[selectedSide] + COLUMNS) offsets[selectedSide] = next - COLUMNS + 1;
    }

    void scrollDetails(int direction, int lineCount, int visible) {
        detailOffset = clamp((long) detailOffset + direction, lineCount - visible);
    }
    void clampDetails(int lineCount, int visible) { detailOffset = clamp(detailOffset, lineCount - visible); }
    void reset() {
        snapshot = null;
        offsets[0] = offsets[1] = 0;
        selectedId = null;
        selectedSide = 0;
        detailOffset = 0;
    }
    private static int clamp(long value, int max) { return (int) Math.max(0, Math.min(value, Math.max(0, max))); }
}

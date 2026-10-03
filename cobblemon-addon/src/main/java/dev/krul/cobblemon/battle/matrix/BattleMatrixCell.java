package dev.krul.cobblemon.battle.matrix;

import java.util.List;

/** One type-chart result with explicit evidence; unknown and not-applicable are never neutral. */
public record BattleMatrixCell(String label, Double multiplier, Evidence evidence, List<String> tooltip) {
    public enum Evidence { CURRENT, POTENTIAL, UNKNOWN, NOT_APPLICABLE }

    public BattleMatrixCell {
        label = label == null ? "?" : label;
        evidence = evidence == null ? Evidence.UNKNOWN : evidence;
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
    }
}

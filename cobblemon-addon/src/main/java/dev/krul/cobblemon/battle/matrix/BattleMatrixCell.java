package dev.krul.cobblemon.battle.matrix;

import java.util.List;

/** One factual type-chart result; unknown and not-applicable are explicit, never neutral. */
public record BattleMatrixCell(String label, Double multiplier, Evidence evidence, List<String> tooltip) {
    public enum Evidence { CURRENT, UNKNOWN, NOT_APPLICABLE }

    public BattleMatrixCell {
        label = label == null ? "?" : label;
        evidence = evidence == null ? Evidence.UNKNOWN : evidence;
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
    }
}

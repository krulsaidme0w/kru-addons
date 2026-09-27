package dev.krul.cobblemon;

import dev.krul.cobblemon.battle.matrix.BattleMatrixContent;
import dev.krul.cobblemon.gui.overlay.OverlayModule;

/** Historical ID retained for Meteor binds/settings; content is the contextual battle matrix. */
public final class BattleMatchups extends OverlayModule {
    public BattleMatchups() {
        super("battle-matchups", "Known moves against revealed opponents using reference-only type matchups.",
            new BattleMatrixContent(), -1, 45);
    }
    // Legacy pane code is reference-only and must never activate.
    @Deprecated public static boolean isEnabled() { return false; }
    @Deprecated public static void reportUnavailable(String reason) {}
}

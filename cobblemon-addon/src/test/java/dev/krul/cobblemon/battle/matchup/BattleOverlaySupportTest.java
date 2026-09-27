package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleOverlaySupportTest {
    @Test void linkageFailureInCapabilityProbeReportsUnsupportedInsteadOfEscaping() {
        var incompatible = new BattleOverlaySupport.Hook() {
            public boolean kru$renderHookObserved() { throw new NoClassDefFoundError("missing optional GUI class"); }
        };
        String diagnostic = assertDoesNotThrow(() -> BattleOverlaySupport.check(incompatible));
        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("NoClassDefFoundError"));
    }
    public static final class Unrendered implements BattleOverlaySupport.Hook {
        public boolean kru$renderHookObserved() { return false; }
        public Object getMessagePane() { return new Object(); }
    }
    @Test void appliedInterfaceAloneCannotProveRenderInjectionWorked() {
        assertTrue(BattleOverlaySupport.check(new Unrendered()).contains("render hook"));
    }
    @Test void absentMixinAndNativePaneAreExplainedInsteadOfClaimedSupported() {
        assertNotNull(BattleOverlaySupport.check(new Object()));
        assertNotNull(BattleOverlaySupport.check(new BattleOverlaySupport.Hook() {
            public boolean kru$renderHookObserved() { return true; }
        }));
    }
}

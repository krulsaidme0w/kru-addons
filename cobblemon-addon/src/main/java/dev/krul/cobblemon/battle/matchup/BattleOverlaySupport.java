package dev.krul.cobblemon.battle.matchup;

/** ABI diagnostics are separate from server rules: battle UI is a client capability. */
public final class BattleOverlaySupport {
    private BattleOverlaySupport() {}
    public interface Hook { boolean kru$renderHookObserved(); }

    public static String check(Object screen) {
        try {
            if (!(screen instanceof Hook hook)) return "Cobblemon battle GUI mixin was not applied";
            if (!hook.kru$renderHookObserved()) return "Cobblemon render hook was not observed";
            Object pane = screen.getClass().getMethod("getMessagePane").invoke(screen);
            if (pane == null) return "native message pane unavailable";
            new MatchupNativePane(pane);
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return "unsupported battle message-pane ABI (" + error.getClass().getSimpleName() + ")";
        }
    }
}

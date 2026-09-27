package dev.krul.cobblemon.battle.matrix;

import java.util.UUID;
import java.util.function.Supplier;

import static dev.krul.cobblemon.battle.matchup.BattleReflection.get;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.singleton;

/** Cheap live context lookup, deliberately independent from the last tick snapshot. */
public final class BattleMatrixContext {
    private final Supplier<Object> battle;

    BattleMatrixContext(Supplier<Object> battle) {
        this.battle = battle;
    }

    public static BattleMatrixContext nativeContext() {
        return new BattleMatrixContext(() -> get(singleton("com.cobblemon.mod.common.client.CobblemonClient"), "getBattle"));
    }

    public Object currentBattle() {
        try { return battle.get(); }
        catch (RuntimeException | LinkageError ignored) { return null; }
    }

    public UUID currentId() {
        return id(currentBattle());
    }

    public UUID id(Object currentBattle) {
        Object value = get(currentBattle, "getBattleId");
        return value instanceof UUID uuid ? uuid : null;
    }
}

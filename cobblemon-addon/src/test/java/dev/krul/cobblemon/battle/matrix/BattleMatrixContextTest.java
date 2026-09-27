package dev.krul.cobblemon.battle.matrix;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BattleMatrixContextTest {
    public record Battle(UUID getBattleId) {}

    @Test void readsCurrentBattleIdentityEveryTimeAndReturnsNullOutsideBattle() {
        var current = new AtomicReference<Object>();
        var context = new BattleMatrixContext(current::get);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();

        assertNull(context.currentId());
        current.set(new Battle(first));
        assertEquals(first, context.currentId());
        current.set(new Battle(second));
        assertEquals(second, context.currentId());
        current.set(null);
        assertNull(context.currentId());
    }

    @Test void rejectsObjectsWithoutANativeUuidBattleIdentity() {
        var context = new BattleMatrixContext(() -> new Object() {
            @SuppressWarnings("unused") public String getBattleId() { return "stale"; }
        });

        assertNull(context.currentId());
    }
}

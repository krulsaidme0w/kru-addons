package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PcSessionScopeTest {
    private final Object world = new Object(), network = new Object(), screen = new Object(), pc = new Object();
    private final PcSessionScope scope = new PcSessionScope();

    private void save() {
        assertTrue(scope.save(world, network, screen, pc, List.of("Species: cobblemon:ralts")));
    }

    @Test void localCloseCanRestoreOnlyImmutableFactsWithoutRelinking() {
        save();
        scope.observe(world, network, null, null);
        var restored = scope.restore(world, network).orElseThrow();
        assertEquals(List.of("Species: cobblemon:ralts"), restored.facts());
        assertFalse(restored.serverLinkVerified());
        assertFalse(restored.storageInteractionAllowed());
    }

    @Test void changedWorldInvalidatesEvenWithSameConnection() {
        save();
        assertTrue(scope.restore(new Object(), network).isEmpty());
        assertTrue(scope.restore(world, network).isEmpty());
    }

    @Test void changedNetworkInvalidatesEvenWithSameWorld() {
        save();
        assertTrue(scope.restore(world, new Object()).isEmpty());
        assertTrue(scope.restore(world, network).isEmpty());
    }

    @Test void disconnectInvalidationIsIrreversible() {
        save();
        scope.observe(null, null, null, null);
        assertTrue(scope.restore(world, network).isEmpty());
    }

    @Test void scopeUsesIdentityRatherThanEquals() {
        Object a = new String("world"), b = new String("world");
        assertTrue(scope.save(a, network, screen, pc, List.of("saved")));
        assertTrue(scope.restore(b, network).isEmpty());
    }

    @Test void anotherPcScreenInvalidatesOldViewEvenWithSameStorage() {
        save();
        scope.observe(world, network, new Object(), pc);
        assertTrue(scope.restore(world, network).isEmpty());
    }

    @Test void changedStorageInvalidatesEvenWithSameScreen() {
        save();
        scope.observe(world, network, screen, new Object());
        assertTrue(scope.restore(world, network).isEmpty());
    }

    @Test void snapshotDoesNotFollowMutableClientFacts() {
        var source = new ArrayList<>(List.of("Level: 5"));
        assertTrue(scope.save(world, network, screen, pc, source));
        source.set(0, "Level: 99");
        var view = scope.restore(world, network).orElseThrow();
        assertEquals(List.of("Level: 5"), view.facts());
        assertThrows(UnsupportedOperationException.class, () -> view.facts().add("changed"));
    }

    @Test void incompleteContextCannotBeSavedAndClearsPriorView() {
        save();
        assertFalse(scope.save(world, null, screen, pc, List.of("invalid")));
        assertTrue(scope.restore(world, network).isEmpty());
        assertFalse(scope.save(world, network, null, pc, List.of()));
        assertFalse(scope.save(world, network, screen, null, List.of()));
    }

    @Test void explicitResetDropsSavedView() {
        save();
        scope.reset();
        assertTrue(scope.restore(world, network).isEmpty());
    }
}

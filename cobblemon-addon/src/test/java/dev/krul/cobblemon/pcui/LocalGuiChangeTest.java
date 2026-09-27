package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalGuiChangeTest {
    @Test void fabricatorGuardsTheDeferredRenderTransitionNotJustClose() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var bytes = getClass().getResourceAsStream("/dev/krul/cobblemon/pcui/PacketFabricatorScreen.class")) {
            assertNotNull(bytes);
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
        }
        var deferred = node.methods.stream().filter(m -> m.name.equals("runAfterRenderTasks")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(deferred.instructions.toArray()).anyMatch(i ->
            i instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("restoreLocally")));
        assertTrue(java.util.Arrays.stream(deferred.instructions.toArray()).anyMatch(i ->
            i instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals("sameContext")));
    }
    @Test void suppressesOnlyGuiPacketsDuringNestedChangesAndResetsAfterFailure() {
        var guard = new LocalGuiChange();
        assertFalse(guard.suppresses(true));
        assertThrows(IllegalStateException.class, () -> guard.run(() -> {
            assertTrue(guard.suppresses(true));
            assertFalse(guard.suppresses(false));
            guard.run(() -> assertTrue(guard.suppresses(true)));
            assertTrue(guard.suppresses(true));
            throw new IllegalStateException();
        }));
        assertFalse(guard.suppresses(true));
    }
    @Test void doesNotSuppressOtherThreads() throws Exception {
        var guard = new LocalGuiChange();
        var observed = new java.util.concurrent.atomic.AtomicBoolean(true);
        guard.run(() -> {
            Thread worker = new Thread(() -> observed.set(guard.suppresses(true)));
            worker.start();
            try { worker.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        assertFalse(observed.get());
    }
}

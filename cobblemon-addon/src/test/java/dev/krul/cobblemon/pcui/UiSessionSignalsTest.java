package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiSessionSignalsTest {
    @Test void closeAndUnlinkInvalidatePcButStorageUpdatesDoNot() {
        var signals = new UiSessionSignals();
        long before = signals.pcRevision();
        signals.payload("com.cobblemon.mod.common.net.messages.client.storage.pc.SetPCPokemonPacket");
        assertEquals(before, signals.pcRevision());
        signals.payload("com.cobblemon.mod.common.net.messages.client.storage.pc.ClosePCPacket");
        assertTrue(signals.pcRevision() > before);
        before = signals.pcRevision();
        signals.payload("com.cobblemon.mod.common.net.messages.server.storage.pc.UnlinkPlayerFromPCPacket");
        assertTrue(signals.pcRevision() > before);
    }
    @Test void newPcSessionCannotReuseOldAuthorization() {
        var signals = new UiSessionSignals();
        long before = signals.pcRevision();
        signals.payload("com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket");
        assertTrue(signals.pcRevision() > before);
        assertEquals(0, signals.containerRevision());
    }
}

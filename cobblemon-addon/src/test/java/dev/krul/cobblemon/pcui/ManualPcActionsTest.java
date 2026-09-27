package dev.krul.cobblemon.pcui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManualPcActionsTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    private final Object screen = new Object(), pc = new Object(), party = new Object();
    private final Storage storage = new Storage();
    private long now;
    private final ManualPcActions actions = new ManualPcActions(storage, () -> now);

    @Test void depositsFirstOccupiedPartySlotIntoFirstFreeSlotOfCurrentBox() {
        actions.request(screen, true, () -> true);
        assertEquals(List.of(new ManualPcActions.Transfer(A, true, 7, 1, 1)), storage.sent);
        assertTrue(actions.busy());
    }
    @Test void withdrawsFirstOccupiedCurrentBoxSlotIntoFirstFreePartySlot() {
        actions.request(screen, false, () -> true);
        assertEquals(List.of(new ManualPcActions.Transfer(B, false, 7, 0, 0)), storage.sent);
    }
    @Test void pendingDoubleClickAndObservationNeverRepeatSemanticMove() {
        actions.request(screen, true, () -> true);
        actions.request(screen, false, () -> true);
        for (int i = 0; i < 30; i++) actions.tick();
        assertEquals(1, storage.sent.size());
        assertTrue(actions.busy());
    }
    @Test void fullCurrentBoxDoesNotChooseAnotherBox() {
        storage.box = slots(B, new UUID(0, 9));
        actions.request(screen, true, () -> true);
        assertTrue(storage.sent.isEmpty());
        assertTrue(actions.status().toLowerCase().contains("full"));
    }
    @Test void fullPartyDoesNotSwap() {
        storage.members = slots(A, C);
        actions.request(screen, false, () -> true);
        assertTrue(storage.sent.isEmpty());
        assertTrue(actions.status().toLowerCase().contains("full"));
    }
    @Test void emptySourcesDoNotSend() {
        storage.members = slots(null, null);
        actions.request(screen, true, () -> true);
        assertTrue(actions.status().toLowerCase().contains("empty"));
        storage.box = slots(null, null);
        actions.request(screen, false, () -> true);
        assertTrue(actions.status().toLowerCase().contains("empty"));
        assertTrue(storage.sent.isEmpty());
    }
    @Test void exactDestinationUuidAndSourceRemovalAreBothRequiredForAcknowledgement() {
        actions.request(screen, true, () -> true);
        storage.box = slots(B, A, null);
        actions.tick();
        assertTrue(actions.busy(), "An intermediate duplicate is not an ACK");
        storage.members = slots(null, null, C);
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().contains("confirmed"));
        assertEquals(1, storage.sent.size());
        assertEquals(7, storage.observedBox);
    }
    @Test void sourceRemovalAloneDoesNotAcknowledgeWithdrawal() {
        actions.request(screen, false, () -> true);
        storage.box = slots(null, null, null);
        actions.tick();
        assertTrue(actions.busy());
        storage.members = slots(B, A, C);
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().contains("confirmed"));
    }
    @Test void differentDestinationUuidFailsClosedWithoutRetry() {
        actions.request(screen, true, () -> true);
        storage.box = slots(B, new UUID(0, 9), null);
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().toLowerCase().contains("mismatch"));
        actions.request(screen, true, () -> true);
        assertEquals(1, storage.sent.size());
    }
    @Test void invalidationBeforeRequestOrAfterReadingPreventsSend() {
        AtomicBoolean valid = new AtomicBoolean(false);
        actions.request(screen, true, valid::get);
        assertTrue(storage.sent.isEmpty());
        actions.reset();
        valid.set(true);
        storage.afterRead = () -> valid.set(false);
        actions.request(screen, true, valid::get);
        assertTrue(storage.sent.isEmpty());
    }
    @Test void pendingInvalidationStopsBeforeAnotherStorageRead() {
        AtomicBoolean valid = new AtomicBoolean(true);
        actions.request(screen, true, valid::get);
        int reads = storage.reads;
        valid.set(false);
        actions.tick();
        assertEquals(reads, storage.reads);
        assertFalse(actions.busy());
        assertTrue(actions.status().toLowerCase().contains("invalid"));
    }
    @Test void timeoutIsMonotonicTerminalAndNeverRetries() {
        actions.request(screen, true, () -> true);
        now = 19_999_999_999L;
        actions.tick();
        assertTrue(actions.busy());
        now = 20_000_000_000L;
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().toLowerCase().contains("timed out"));
        actions.request(screen, true, () -> true);
        assertEquals(1, storage.sent.size());
    }
    @Test void replacedLiveStorageCannotAcknowledgeOldRequest() {
        actions.request(screen, true, () -> true);
        storage.pcIdentity = new Object();
        storage.box = slots(B, A, null);
        storage.members = slots(null, null, C);
        actions.tick();
        assertFalse(actions.busy());
        assertFalse(actions.status().contains("confirmed"));
    }
    @Test void unavailableSnapshotNeverSendsAndUncertainSendNeverRetries() {
        storage.unavailable = true;
        actions.request(screen, true, () -> true);
        assertTrue(storage.sent.isEmpty());
        assertTrue(actions.status().toLowerCase().contains("unavailable"));
        storage.unavailable = false;
        storage.failSend = true;
        actions.request(screen, true, () -> true);
        actions.request(screen, true, () -> true);
        actions.tick();
        assertEquals(1, storage.sent.size());
    }
    @Test void duplicatesBeforeRequestAndServerLastPartyRestrictionPreventSend() {
        storage.box = slots(A, null);
        actions.request(screen, true, () -> true);
        assertTrue(storage.sent.isEmpty());
        storage.box = slots(B, null);
        storage.members = slots(null, A);
        storage.preventLast = true;
        actions.request(screen, true, () -> true);
        assertTrue(storage.sent.isEmpty());
        assertTrue(actions.status().toLowerCase().contains("last"));
    }
    @Test void resetDropsPendingObservationAndAllowsNewSession() {
        actions.request(screen, true, () -> true);
        actions.reset();
        int reads = storage.reads;
        actions.tick();
        assertFalse(actions.busy());
        assertEquals(reads, storage.reads);
        actions.request(screen, false, () -> true);
        assertEquals(2, storage.sent.size());
    }
    private static List<UUID> slots(UUID... values) { return Arrays.asList(values); }
    private final class Storage implements ManualPcActions.Access {
        List<UUID> box = slots(B, null, null), members = slots(null, A, C);
        List<ManualPcActions.Transfer> sent = new ArrayList<>();
        Object pcIdentity = pc;
        boolean unavailable, failSend, preventLast;
        int reads;
        Integer observedBox;
        Runnable afterRead = () -> {};
        public boolean supports(Object s) { return s == screen; }
        public boolean live(Object s) { return s == screen; }
        public ManualPcActions.Snapshot read(Object s, Integer pinnedBox) {
            reads++;
            observedBox = pinnedBox;
            afterRead.run();
            if (unavailable) throw new IllegalStateException("Unavailable");
            return new ManualPcActions.Snapshot(pcIdentity, party, 7, box, members, preventLast);
        }
        public void send(Object s, ManualPcActions.Snapshot before, ManualPcActions.Transfer move, BooleanSupplier valid) {
            if (!valid.getAsBoolean()) throw new IllegalStateException("Invalid");
            sent.add(move);
            if (failSend) throw new IllegalStateException("Transport result unknown");
        }
    }
}

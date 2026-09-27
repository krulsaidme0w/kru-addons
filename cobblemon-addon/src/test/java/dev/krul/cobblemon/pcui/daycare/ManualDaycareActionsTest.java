package dev.krul.cobblemon.pcui.daycare;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.*;

class ManualDaycareActionsTest {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);
    static final Pokemon FIRST = new Pokemon(A, "ralts", Set.of(), 12);
    static final Pokemon OTHER = new Pokemon(B, "abra", Set.of(), 20);
    final Fake port = new Fake();
    long now;
    final ManualDaycareActions actions = new ManualDaycareActions(port, () -> now);

    @Test void hiddenRootWithLiveHandlerStartsOnlyOneLeftSlotRequest() {
        port.current = null;
        actions.request(port.root, true, () -> true);
        actions.request(port.root, true, () -> true);
        actions.tick();
        assertTrue(actions.busy());
        assertEquals(List.of("left:insert"), port.sent);
    }

    @Test void occupiedEmptyUnknownAndUnrecognizedRootsNeverClick() {
        for (Role role : List.of(Role.OCCUPIED, Role.UNKNOWN)) {
            port.role = role;
            actions.request(port.root, true, () -> true);
        }
        port.role = Role.EMPTY;
        actions.request(port.root, false, () -> true);
        port.kind = Kind.UNRELATED;
        assertFalse(actions.supports(port.root));
        actions.request(port.root, true, () -> true);
        assertTrue(port.sent.isEmpty());
    }

    @Test void pickerSelectsFirstOccupiedUuidAndAckRequiresItsRemovalAndLeftCard() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.PICKER);
        assertTrue(actions.ownsScreen(port.current));
        actions.tick(); actions.tick();
        assertEquals(List.of("left:insert", "select:" + A), port.sent);
        port.show(Kind.CONFIRM);
        actions.tick(); actions.tick();
        assertEquals(List.of("left:insert", "select:" + A, "confirm"), port.sent);
        port.show(Kind.ROOT); port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.tick();
        assertTrue(actions.busy(), "card alone is not an ACK");
        port.party = List.of(OTHER);
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().contains(A.toString()));
    }

    @Test void wrongUuidRemovalOrMovedToPcDoesNotAcknowledgeInsertion() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.PICKER); actions.tick();
        port.show(Kind.ROOT); port.role = Role.OCCUPIED; port.card = FIRST.projection();
        port.party = List.of(FIRST); actions.tick();
        assertTrue(actions.busy());
        port.party = List.of(OTHER); port.pc = List.of(FIRST); actions.tick();
        assertTrue(actions.busy());
    }

    @Test void visibleUnknownUuidWithdrawalRequiresExactlyOneNewStorageUuid() {
        port.role = Role.OCCUPIED; port.card = new Projection("ditto", Set.of());
        actions.request(port.root, false, () -> true);
        actions.tick();
        port.role = Role.EMPTY;
        actions.tick(); assertTrue(actions.busy());
        Pokemon returned = new Pokemon(C, "ditto", Set.of(), 7);
        port.pc = List.of(returned);
        actions.tick();
        assertFalse(actions.busy());
        assertEquals(List.of("left:out"), port.sent);
        assertTrue(actions.status().contains(C.toString()));
    }

    @Test void ambiguousReturnStaysPendingAndTimeoutNeverRetries() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.role = Role.EMPTY;
        port.pc = List.of(new Pokemon(C, "ralts", Set.of(), 1), new Pokemon(new UUID(0, 4), "ralts", Set.of(), 1));
        actions.tick(); assertTrue(actions.busy());
        now = 31_000_000_000L;
        actions.tick();
        assertFalse(actions.busy());
        actions.request(port.root, false, () -> true);
        assertEquals(List.of("left:out"), port.sent);
        assertTrue(actions.status().contains("Unknown"));
    }

    @Test void unrelatedMenuIsNotOwnedAndInvalidSessionStopsTickBeforeMutation() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.UNRELATED);
        assertFalse(actions.ownsScreen(port.current));
        actions.tick(); assertFalse(actions.busy());
        actions.reset(); port.show(Kind.ROOT);
        boolean[] valid = {true};
        actions.request(port.root, true, () -> valid[0]);
        valid[0] = false; port.show(Kind.PICKER);
        assertTrue(actions.ownsScreen(port.current), "local handoff proof must not call the session predicate");
        actions.tick();
        assertFalse(actions.busy(), "terminal closure is enforced before any pending mutation");
        assertEquals(List.of("left:insert", "left:insert"), port.sent);
    }

    @Test void confirmationBeforeInsertionSelectionAndSecondPickerAreNotOwned() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.CONFIRM);
        assertFalse(actions.ownsScreen(port.current));
        actions.tick(); assertFalse(actions.busy());
        actions.reset(); port.show(Kind.ROOT);
        actions.request(port.root, true, () -> true);
        port.show(Kind.PICKER); actions.tick();
        port.show(Kind.PICKER);
        assertFalse(actions.ownsScreen(port.current));
        actions.tick();
        assertEquals(1, port.sent.stream().filter(s -> s.startsWith("select:")).count());
    }

    @Test void changedPenOrHandlerCannotAcknowledgeOrStartHiddenAction() {
        port.live = false;
        actions.request(port.root, true, () -> true);
        assertTrue(port.sent.isEmpty());
        port.live = true;
        actions.request(port.root, true, () -> true);
        port.pen = 2; actions.tick();
        assertFalse(actions.busy());
    }

    @Test void noCapacityOrNoSourceOrUnavailableStorageNeverClicks() {
        port.party = List.of(); actions.request(port.root, true, () -> true);
        port.role = Role.OCCUPIED; port.capacity = false;
        actions.request(port.root, false, () -> true);
        port.capacity = true; port.storageAvailable = false;
        actions.request(port.root, false, () -> true);
        assertTrue(port.sent.isEmpty());
    }

    @Test void ownedNullGapSurvivesPickerAutocloseButExpires() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.PICKER); actions.tick();
        port.kind = Kind.GAP; port.current = null;
        assertTrue(actions.ownsScreen(null));
        actions.tick(); assertTrue(actions.busy());
        port.live = false;
        assertFalse(actions.ownsScreen(null));
        port.live = true; now = 31_000_000_000L;
        assertFalse(actions.ownsScreen(null));
        actions.tick(); assertFalse(actions.busy());
    }

    @Test void changedDialogueInputOnSameScreenIsNotOwned() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.show(Kind.CONFIRM); actions.tick();
        port.transition = new Object();
        assertFalse(actions.ownsScreen(port.current));
        actions.tick(); assertFalse(actions.busy());
        assertEquals(List.of("left:out", "confirm"), port.sent);
    }

    @Test void renderingConfirmationWaitsThenDispatchesOnceAndStillRequiresStorageAck() {
        actions.request(port.root, true, () -> true);
        port.show(Kind.PICKER); actions.tick();
        port.show(Kind.CONFIRM); port.confirmationReady = false;
        actions.tick(); actions.tick();
        assertTrue(actions.busy(), "gradual dialogue rendering is not a failed mutation");
        assertTrue(actions.ownsScreen(port.current));
        assertEquals(0, port.confirmAttempts);
        assertEquals(List.of("left:insert", "select:" + A), port.sent);
        port.confirmationReady = true;
        actions.tick(); actions.tick();
        assertEquals(1, port.confirmAttempts);
        assertEquals(List.of("left:insert", "select:" + A, "confirm"), port.sent);
        assertTrue(actions.busy(), "dispatch is not an ACK");
        port.show(Kind.ROOT); port.role = Role.OCCUPIED; port.card = FIRST.projection();
        port.party = List.of(OTHER); actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().startsWith("Inserted"));
    }

    @Test void readinessWaitUsesOriginalDeadlineAndNeverDispatchesOnTimeout() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        now = 29_000_000_000L;
        port.show(Kind.CONFIRM); port.confirmationReady = false;
        actions.tick(); assertTrue(actions.busy());
        now = 30_000_000_000L;
        port.confirmationReady = true;
        actions.tick(); actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().contains("timed out"));
        assertEquals(0, port.confirmAttempts);
    }

    @Test void changedInputWhileWaitingCannotBecomeAConfirmationRequest() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.show(Kind.CONFIRM); port.confirmationReady = false;
        actions.tick(); assertTrue(actions.busy());
        port.transition = new Object(); port.confirmationReady = true;
        assertFalse(actions.ownsScreen(port.current));
        actions.tick();
        assertFalse(actions.busy());
        assertEquals(0, port.confirmAttempts);
    }

    @Test void ambiguousBooleanDispatchFailureIsTerminalWithoutRetry() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.show(Kind.CONFIRM); port.dispatchResult = false;
        actions.tick(); actions.tick();
        actions.request(port.root, false, () -> true);
        assertFalse(actions.busy());
        assertTrue(actions.status().startsWith("Unknown"));
        assertEquals(1, port.confirmAttempts);
        assertEquals(List.of("left:out", "confirm"), port.sent);
    }

    @Test void sessionInvalidatedWhileRenderingStopsBeforeDispatch() {
        boolean[] valid = {true};
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> valid[0]);
        port.show(Kind.CONFIRM); port.confirmationReady = false;
        actions.tick(); assertTrue(actions.busy());
        valid[0] = false; port.confirmationReady = true;
        actions.tick();
        assertFalse(actions.busy());
        assertEquals(0, port.confirmAttempts);
    }

    @Test void replacementStorageObjectCannotSupplyTransactionAck() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.pcIdentity = new Object();
        port.role = Role.EMPTY; port.pc = List.of(new Pokemon(C, "ralts", Set.of(), 1));
        actions.tick();
        assertFalse(actions.busy());
        assertTrue(actions.status().contains("Unknown"));
    }

    @Test void directReturnCanAcknowledgeFreshSamePenRoot() {
        port.role = Role.OCCUPIED; port.card = FIRST.projection();
        actions.request(port.root, false, () -> true);
        port.handler = new Object(); port.current = new Object();
        port.role = Role.EMPTY; port.pc = List.of(new Pokemon(C, "ralts", Set.of(), 1));
        assertTrue(actions.ownsScreen(port.current));
        actions.tick();
        assertTrue(actions.status().startsWith("Returned"));
    }

    static final class Fake implements DaycarePort {
        final Object root = new Object();
        Object handler = new Object(), transition = new Object(), pcIdentity = new Object();
        Object current = root;
        Kind kind = Kind.ROOT;
        Role role = Role.EMPTY;
        Projection card;
        int pen = 1;
        boolean live = true, capacity = true, storageAvailable = true;
        boolean confirmationReady = true, dispatchResult = true;
        int confirmAttempts;
        List<Pokemon> party = List.of(FIRST, OTHER), pc = List.of();
        final List<String> sent = new ArrayList<>();
        void show(Kind next) { kind = next; current = next == Kind.ROOT ? root : new Object(); }
        public Frame read(Object retainedRoot) {
            return new Frame(current, handler, kind, pen, role, card, "right", live,
                storageAvailable ? new Storage(party, pc, capacity, this, pcIdentity) : null, transition);
        }
        public void openLeft(Object root, boolean insert) { sent.add("left:" + (insert ? "insert" : "out")); }
        public boolean select(Object screen, Pokemon expected, Storage baseline) { sent.add("select:" + expected.uuid()); return true; }
        public ConfirmationReadiness confirmationReadiness(Object screen, boolean insert) {
            return confirmationReady ? ConfirmationReadiness.READY : ConfirmationReadiness.WAITING;
        }
        public boolean confirm(Object screen, boolean insert) {
            confirmAttempts++;
            if (!confirmationReady) return false; // Native renderInput=false currently takes this path.
            sent.add("confirm"); return dispatchResult;
        }
    }
}

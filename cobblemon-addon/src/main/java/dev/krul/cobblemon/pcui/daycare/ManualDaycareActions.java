package dev.krul.cobblemon.pcui.daycare;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.*;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.*;

/** One explicitly requested left-parent action. Enabled only by the controller's opt-in. */
public final class ManualDaycareActions {
    private final DaycarePort port;
    private final LongSupplier clock;
    private Pending pending;
    private boolean blocked;
    private String status = "Idle";
    private static final long TIMEOUT = 30_000_000_000L;

    public ManualDaycareActions() { this(new NativeDaycarePort(), System::nanoTime); }
    ManualDaycareActions(DaycarePort port, LongSupplier clock) { this.port = port; this.clock = clock; }

    public boolean supports(Object screen) {
        if (screen == null) return false;
        try { Frame f = port.inspect(screen); return f != null && f.live() && f.kind() == Kind.ROOT; }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    public String request(Object screen, boolean insert, BooleanSupplier valid) {
        if (busy() || blocked) return status;
        try {
            if (valid == null || !valid.getAsBoolean()) return status = "Session is no longer valid";
            Frame f = port.read(screen);
            if (screen == null || f == null || !f.live() || f.kind() != Kind.ROOT)
                return status = "Current Academy pen handler unavailable";
            if (f.left() == Role.UNKNOWN) return status = "Unknown left role";
            if (insert != (f.left() == Role.EMPTY)) return status = insert ? "Left role occupied" : "Left role empty";
            if (f.storage() == null || f.storage().all() == null) return status = "Live PC/party storage unavailable";
            if (insert && f.storage().party().isEmpty()) return status = "Party is empty";
            if (!insert && !f.storage().capacity()) return status = "PC and party are full";
            if (!insert && f.card() == null) return status = "Unknown left card projection";
            Pending p = new Pending(screen, f, insert, valid, clock.getAsLong());
            if (!valid.getAsBoolean()) return status = "Session is no longer valid";
            pending = p; // Commit before dispatch; an exception must never enable an automatic retry.
            status = "Waiting for left-parent " + (insert ? "picker" : "return");
            port.openLeft(screen, insert);
        } catch (RuntimeException | LinkageError unavailable) { fail("native action unavailable; inspect server state"); }
        return status;
    }

    public void tick() {
        Pending p = pending;
        if (p == null) return;
        try {
            if (!p.valid.getAsBoolean()) { fail("session ended"); return; }
            if (clock.getAsLong() - p.started >= TIMEOUT) { fail("acknowledgement timed out; no retry"); return; }
            Frame f = port.read(p.root);
            if (!allowed(p, f)) { fail("unrelated screen, changed pen or closed handler"); return; }
            if (f.storage() != null && !sameStorageIdentity(p.baseline.storage(), f.storage())) {
                fail("native storage objects changed"); return;
            }
            if (f.kind() == Kind.PICKER && !p.selected) {
                if (!sameStorage(p.baseline.storage(), f.storage())) { fail("party/storage changed before selection"); return; }
                if (!p.valid.getAsBoolean()) { fail("session ended"); return; }
                p.picker = f.screen(); p.selected = true;
                if (!port.select(f.screen(), p.target, f.storage())) { fail("first party entry is unavailable or ambiguous in picker"); return; }
                status = "Waiting for insertion confirmation / storage acknowledgement";
            } else if (f.kind() == Kind.CONFIRM && !p.confirmed) {
                if (!sameStorage(p.baseline.storage(), f.storage())) { fail("storage changed before confirmation"); return; }
                if (!p.valid.getAsBoolean()) { fail("session ended"); return; }
                p.confirmation = f.screen(); p.confirmationIdentity = f.transitionIdentity();
                ConfirmationReadiness readiness = port.confirmationReadiness(f.screen(), p.insert);
                if (readiness == ConfirmationReadiness.WAITING) {
                    status = "Waiting for daycare confirmation input readiness";
                    return;
                }
                if (readiness != ConfirmationReadiness.READY) { fail("daycare confirmation readiness unavailable"); return; }
                if (!p.valid.getAsBoolean()) { fail("session ended"); return; }
                // Consume only when dispatch begins. Its boolean failure/exception is ambiguous,
                // so neither may reopen the readiness wait or permit another dispatch.
                p.confirmed = true;
                if (!port.confirm(f.screen(), p.insert)) { fail("unrecognized or unavailable daycare confirmation"); return; }
                status = "Waiting for storage acknowledgement";
            } else if (f.kind() == Kind.ROOT) {
                UUID ack = acknowledgement(p, f);
                if (ack != null) {
                    pending = null;
                    status = (p.insert ? "Inserted " : "Returned ") + ack;
                }
            }
        } catch (RuntimeException | LinkageError unavailable) { fail("native observation unavailable; no retry"); }
    }

    public boolean busy() { return pending != null; }
    public String status() { return status; }
    public void reset() { pending = null; blocked = false; status = "Idle"; }

    public boolean ownsScreen(Object screen) {
        Pending p = pending;
        if (p == null) return false;
        try {
            Frame f = port.inspect(p.root);
            // Local handoff evidence only. The controller accepts/rejects closure epochs first;
            // request/tick then enforce the resulting session predicate before any dispatch.
            return clock.getAsLong() - p.started < TIMEOUT
                && f != null && f.screen() == screen && allowed(p, f);
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    private boolean allowed(Pending p, Frame f) {
        if (f == null || !f.live()) return false;
        return switch (f.kind()) {
            case ROOT -> f.pen() == p.baseline.pen()
                && (f.handler() == p.baseline.handler() || p.selected || p.confirmed || (!p.insert && f.left() == Role.EMPTY))
                && Objects.equals(f.rightFingerprint(), p.baseline.rightFingerprint());
            case PICKER -> p.insert && !p.confirmed && (!p.selected || p.picker == f.screen());
            case CONFIRM -> (!p.insert || p.selected) && (p.confirmation == null || (p.confirmation == f.screen()
                && Objects.equals(p.confirmationIdentity, f.transitionIdentity())));
            case GAP -> true;
            case UNRELATED -> false;
        };
    }

    private UUID acknowledgement(Pending p, Frame f) {
        if (f.storage() == null || f.storage().all() == null) return null;
        var before = p.baseline.storage().all();
        var after = f.storage().all();
        var added = new HashSet<>(after.keySet()); added.removeAll(before.keySet());
        var removed = new HashSet<>(before.keySet()); removed.removeAll(after.keySet());
        if (p.insert) {
            if (p.selected && f.left() == Role.OCCUPIED && p.target.projection().equals(f.card())
                && added.isEmpty() && removed.equals(Set.of(p.target.uuid()))) return p.target.uuid();
        } else if (f.left() == Role.EMPTY && removed.isEmpty() && added.size() == 1) {
            UUID id = added.iterator().next();
            if (p.baseline.card().equals(after.get(id).projection())) return id;
        }
        return null;
    }

    private boolean sameStorage(Storage a, Storage b) {
        return b != null && b.all() != null && sameStorageIdentity(a, b) && a.party().equals(b.party()) && a.pc().equals(b.pc());
    }

    private boolean sameStorageIdentity(Storage a, Storage b) {
        return a.partyIdentity() == b.partyIdentity() && a.pcIdentity() == b.pcIdentity();
    }

    private void fail(String reason) { pending = null; blocked = true; status = "Unknown: " + reason; }

    private static final class Pending {
        final Object root;
        final Frame baseline;
        final boolean insert;
        final BooleanSupplier valid;
        final long started;
        final Pokemon target;
        boolean selected, confirmed;
        Object picker, confirmation, confirmationIdentity;
        Pending(Object root, Frame baseline, boolean insert, BooleanSupplier valid, long started) {
            this.root = root; this.baseline = baseline; this.insert = insert; this.valid = valid; this.started = started;
            target = insert ? baseline.storage().party().getFirst() : null;
        }
    }
}

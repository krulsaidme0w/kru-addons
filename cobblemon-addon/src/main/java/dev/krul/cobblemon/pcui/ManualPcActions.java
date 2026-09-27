package dev.krul.cobblemon.pcui;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One manual transfer, called on the client thread; the controller owns the screen/session lifetime. */
public final class ManualPcActions {
    record Snapshot(Object pc, Object party, int box, List<UUID> boxSlots, List<UUID> partySlots,
                    boolean preventLastDeposit) {
        Snapshot {
            if (pc == null || party == null || box < 0 || boxSlots.isEmpty() || partySlots.isEmpty())
                throw new IllegalArgumentException("Storage unavailable");
            boxSlots = Collections.unmodifiableList(new ArrayList<>(boxSlots));
            partySlots = Collections.unmodifiableList(new ArrayList<>(partySlots));
        }
    }
    record Transfer(UUID uuid, boolean intoPc, int box, int source, int destination) {}
    interface Access {
        boolean supports(Object screen);
        boolean live(Object screen);
        Snapshot read(Object screen, Integer box) throws Exception;
        void send(Object screen, Snapshot before, Transfer transfer, BooleanSupplier valid) throws Exception;
    }

    private static final long TIMEOUT_NANOS = 20_000_000_000L;
    private final Access access;
    private final LongSupplier nanos;
    private Pending pending;
    private boolean failed;
    private String status = "Ready";
    private record Pending(Object screen, Snapshot before, Transfer transfer, BooleanSupplier valid, long started) {}

    public ManualPcActions() { this(new NativePcAccess(), System::nanoTime); }
    ManualPcActions(Access access, LongSupplier nanos) { this.access = access; this.nanos = nanos; }
    public boolean supports(Object screen) { return access.supports(screen); }
    /** Storage identity evidence only; the controller must also prove connection and closure epochs. */
    public boolean live(Object screen) { return access.live(screen); }

    public String request(Object screen, boolean intoPc, BooleanSupplier valid) {
        if (pending != null || failed) return status;
        try {
            if (valid == null || !valid.getAsBoolean()) return stop("PC session invalidated");
            if (!supports(screen)) return status = "PC action unavailable for this screen";
            Snapshot snapshot = access.read(screen, null);
            if (duplicates(snapshot)) return status = "Storage unavailable: duplicate UUID";
            List<UUID> source = intoPc ? snapshot.partySlots : snapshot.boxSlots;
            List<UUID> destination = intoPc ? snapshot.boxSlots : snapshot.partySlots;
            int from = -1;
            for (int i = 0; i < source.size(); i++) if (source.get(i) != null) { from = i; break; }
            if (from < 0) return status = intoPc ? "Party is empty" : "Current box is empty";
            int to = destination.indexOf(null);
            if (to < 0) return status = intoPc ? "Current box is full" : "Party is full";
            if (intoPc && snapshot.preventLastDeposit && source.stream().filter(java.util.Objects::nonNull).count() == 1)
                return status = "Server prevents depositing the last party Pokemon";
            if (!valid.getAsBoolean()) return stop("PC session invalidated before send");
            Transfer transfer = new Transfer(source.get(from), intoPc, snapshot.box, from, to);
            // Arm before the send: a thrown send may already have reached the transport.
            pending = new Pending(screen, snapshot, transfer, valid, nanos.getAsLong());
            access.send(screen, snapshot, transfer, valid);
            return status = "Waiting for exact UUID transfer confirmation";
        } catch (Exception | LinkageError unavailable) {
            return pending == null ? status = "PC storage unavailable" : stop("PC send uncertain; end session before retrying");
        }
    }

    public void tick() {
        Pending p = pending;
        if (p == null) return;
        try {
            if (!p.valid.getAsBoolean()) { stop("PC session invalidated"); return; }
            if (nanos.getAsLong() - p.started >= TIMEOUT_NANOS) { stop("PC transfer timed out; end session before retrying"); return; }
            Snapshot current = access.read(p.screen, p.transfer.box);
            if (!p.valid.getAsBoolean()) { stop("PC session invalidated"); return; }
            if (current.pc != p.before.pc || current.party != p.before.party || current.box != p.transfer.box
                || current.boxSlots.size() != p.before.boxSlots.size() || current.partySlots.size() != p.before.partySlots.size()) {
                stop("PC storage identity mismatch"); return;
            }
            Transfer move = p.transfer;
            List<UUID> source = move.intoPc ? current.partySlots : current.boxSlots;
            List<UUID> destination = move.intoPc ? current.boxSlots : current.partySlots;
            UUID observed = destination.get(move.destination);
            if (observed != null && !move.uuid.equals(observed)) { stop("PC destination UUID mismatch"); return; }
            if (move.uuid.equals(observed) && !source.contains(move.uuid) && !duplicates(current)) {
                pending = null;
                status = "PC transfer confirmed";
            }
        } catch (Exception | LinkageError unavailable) {
            stop("PC storage unavailable during transfer; end session before retrying");
        }
    }
    public boolean busy() { return pending != null; }
    public String status() { return status; }
    /** Call only when ending/changing session, not to retry an uncertain transaction. */
    public void reset() { pending = null; failed = false; status = "Ready"; }
    private String stop(String reason) { pending = null; failed = true; return status = reason; }
    private static boolean duplicates(Snapshot snapshot) {
        var seen = new HashSet<UUID>();
        for (var slots : List.of(snapshot.boxSlots, snapshot.partySlots))
            for (UUID uuid : slots) if (uuid != null && !seen.add(uuid)) return true;
        return false;
    }
}

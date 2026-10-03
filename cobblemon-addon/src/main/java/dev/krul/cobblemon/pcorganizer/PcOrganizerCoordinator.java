package dev.krul.cobblemon.pcorganizer;

import java.util.Objects;
import java.util.UUID;

/** Snapshot-driven PC organizer: one request, then one exact server observation. */
public final class PcOrganizerCoordinator {
    public enum Status { PREVIEW, REQUESTED, WAITING, ACK, COMPLETE, BLOCKED }
    public record Tick(Status status, PcOrganizerPlan.Move move, String reason, boolean requestSent) {}
    @FunctionalInterface public interface MutationPort { void move(PcOrganizerPlan.Move move); }

    private static final long ACK_DEADLINE = 400;
    private final PcOrganizerSettings settings;
    private final MutationPort port;
    private final PcOrganizerSnapshot target;
    private PcOrganizerSnapshot expected;
    private PcOrganizerPlan.Move pending;
    private PcOrganizerSnapshot before;
    private long requestedAt;
    private UUID store;
    private int boxCount;
    private boolean terminal;

    public PcOrganizerCoordinator(PcOrganizerSettings settings, MutationPort port) {
        this(settings, port, null);
    }
    public PcOrganizerCoordinator(PcOrganizerSettings settings, MutationPort port, PcOrganizerSnapshot target) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.port = Objects.requireNonNull(port, "port");
        this.target = target;
    }

    public Tick tick(PcOrganizerSnapshot snapshot, long tick) {
        if (terminal) return blocked("terminal");
        if (snapshot == null) return block("snapshot-unavailable");
        if (store == null) { store = snapshot.storeUuid(); boxCount = snapshot.boxCount(); expected = snapshot; }
        if (!store.equals(snapshot.storeUuid())) return block("pc-store-changed");
        if (boxCount != snapshot.boxCount()) return block("pc-capacity-changed");
        if (pending != null) return observe(snapshot, tick);
        if (!PcOrganizerPreparation.sameStorage(expected, snapshot)) return block("PC changed between transfers; preview again");

        PcOrganizerPlan plan;
        try { plan = target == null ? PcOrganizerPlanner.plan(snapshot, settings) : remaining(snapshot); }
        catch (RuntimeException failure) { return block("planning-failed:" + failure.getMessage()); }
        if (settings.runMode() == PcOrganizerRunMode.PREVIEW)
            return new Tick(Status.PREVIEW, plan.moves().isEmpty() ? null : plan.moves().get(0),
                "preview-moves=" + plan.moves().size(), false);
        if (plan.moves().isEmpty()) { terminal = true; return new Tick(Status.COMPLETE, null, "complete", false); }
        pending = plan.moves().get(0);
        before = snapshot;
        requestedAt = tick;
        try { port.move(pending); }
        catch (RuntimeException failure) { pending = null; before = null; return block("send-failed:" + failure.getClass().getSimpleName()); }
        return new Tick(Status.REQUESTED, pending, "move-requested", true);
    }

    private PcOrganizerPlan remaining(PcOrganizerSnapshot snapshot) {
        if (!target.storeUuid().equals(snapshot.storeUuid()) || !target.boxNames().equals(snapshot.boxNames())
            || target.pokemon().size() != snapshot.pokemon().size()) throw new IllegalStateException("Preview no longer matches PC");
        var current = new java.util.HashMap<UUID, PcOrganizerPokemon>();
        snapshot.pokemon().forEach(p -> current.put(p.uuid(), p));
        var moves = new java.util.ArrayList<PcOrganizerPlan.Move>();
        var touched = new java.util.HashSet<Integer>();
        for (var destination : target.pokemon()) {
            var source = current.get(destination.uuid());
            if (source == null || !PcOrganizerPreparation.position(source, destination.box(), destination.slot()).equals(destination))
                throw new IllegalStateException("Pokemon changed since preview");
            if (source.box() == destination.box() && source.slot() == destination.slot()) continue;
            boolean protectedMove = settings.boxPolicy().isProtected(source.box()) || settings.boxPolicy().isProtected(destination.box());
            if (protectedMove && !internalManualRoleSort(source.box(), destination.box()))
                throw new IllegalStateException("Protected box differs from preview");
            moves.add(new PcOrganizerPlan.Move(source, source.box(), source.slot(), destination.box(), destination.slot()));
            touched.add(source.box()); touched.add(destination.box());
        }
        return new PcOrganizerPlan(moves, moves.size(), 0, touched);
    }

    private boolean internalManualRoleSort(int sourceBox, int destinationBox) {
        if (settings.boxPolicy().explicitProtected().contains(sourceBox)
            || settings.boxPolicy().explicitProtected().contains(destinationBox)) return false;
        String sourceRole = manualRole(expected.boxNames().get(sourceBox));
        String destinationRole = manualRole(expected.boxNames().get(destinationBox));
        return sourceRole.equals(destinationRole) && (sourceRole.equals("TRADE") || sourceRole.equals("BREEDING"));
    }

    private static String manualRole(String name) {
        return PcOrganizerLayoutPlanner.normalize(name).replaceFirst(" [1-9][0-9]*(?:/[1-9][0-9]*)?$", "");
    }

    private Tick observe(PcOrganizerSnapshot after, long tick) {
        if (tick - requestedAt > ACK_DEADLINE) return block("ack-deadline");
        PcOrganizerPokemon source = at(after, pending.fromBox(), pending.fromSlot());
        PcOrganizerPokemon destination = at(after, pending.toBox(), pending.toSlot());
        var displaced = at(before, pending.toBox(), pending.toSlot());
        boolean sourceValid = displaced == null ? source == null : source != null && source.uuid().equals(displaced.uuid());
        if (!sourceValid || destination == null || !pending.pokemon().uuid().equals(destination.uuid()))
            return unchanged(before, after) ? new Tick(Status.WAITING, pending, "awaiting-exact-ack", false)
                : block("unexpected-layout-delta");
        if (!sameOutside(before, after, pending)) return block("unrelated-layout-delta");
        var done = pending;
        expected = after;
        pending = null; before = null;
        return new Tick(Status.ACK, done, "exact-move-ack", false);
    }

    private static PcOrganizerPokemon at(PcOrganizerSnapshot snapshot, int box, int slot) {
        return snapshot.pokemon().stream().filter(p -> p.box() == box && p.slot() == slot).findFirst().orElse(null);
    }
    private static boolean unchanged(PcOrganizerSnapshot a, PcOrganizerSnapshot b) {
        return a.pokemon().equals(b.pokemon());
    }
    private static boolean sameOutside(PcOrganizerSnapshot a, PcOrganizerSnapshot b, PcOrganizerPlan.Move move) {
        if (a.pokemon().size() != b.pokemon().size() || !a.boxNames().equals(b.boxNames())) return false;
        for (PcOrganizerPokemon p : a.pokemon()) {
            if (p.box() == move.fromBox() && p.slot() == move.fromSlot()) continue;
            if (p.box() == move.toBox() && p.slot() == move.toSlot()) continue;
            PcOrganizerPokemon q = at(b, p.box(), p.slot());
            if (q == null || !p.uuid().equals(q.uuid())) return false;
        }
        return true;
    }
    private Tick block(String reason) { terminal = true; return blocked(reason); }
    private static Tick blocked(String reason) { return new Tick(Status.BLOCKED, null, reason, false); }
}

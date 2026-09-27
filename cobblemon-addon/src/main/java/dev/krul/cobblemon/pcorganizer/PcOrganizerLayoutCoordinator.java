package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Applies the named-box layout before ordinary Pokémon sorting. */
public final class PcOrganizerLayoutCoordinator {
    public enum Status { REQUESTED, WAITING, ACK, COMPLETE, BLOCKED }
    public record Tick(Status status, String reason, boolean requestSent) {}
    public interface MutationPort {
        void move(PcOrganizerPlan.Move move);
        void rename(UUID store, int box, String name);
    }
    private final MutationPort port;
    private PcOrganizerBoxMovePlanner.Plan plan;
    private PcOrganizerSnapshot before;
    private PcOrganizerSnapshot expected;
    private int moveIndex, renameIndex;
    private PcOrganizerPlan.Move pendingMove;
    private PcOrganizerBoxMovePlanner.Rename pendingRename;
    private UUID store;
    private boolean terminal;
    private long requestedAt, sequence;

    public PcOrganizerLayoutCoordinator(MutationPort port) { this.port = Objects.requireNonNull(port); }
    public PcOrganizerLayoutCoordinator(MutationPort port, PcOrganizerBoxMovePlanner.Plan plan) {
        this(port); this.plan = Objects.requireNonNull(plan);
    }
    public Tick tick(PcOrganizerSnapshot snapshot) {
        return tick(snapshot, ++sequence);
    }
    public Tick tick(PcOrganizerSnapshot snapshot, long tick) {
        if (terminal) return new Tick(Status.BLOCKED, "terminal", false);
        try {
            if (snapshot == null) return block("snapshot-unavailable");
            if (store == null) { store = snapshot.storeUuid(); if (plan == null) plan = PcOrganizerBoxMovePlanner.plan(snapshot); expected = snapshot; }
            if (!store.equals(snapshot.storeUuid())) return block("pc-store-changed");
            if ((pendingMove != null || pendingRename != null) && tick - requestedAt > 400) return block("ack-deadline");
            if (pendingMove != null) return observeMove(snapshot);
            if (pendingRename != null) return observeRename(snapshot);
            if (expected.boxCount() != snapshot.boxCount() || !same(expected, snapshot)) return block("layout-changed-between-requests");
            if (moveIndex < plan.moves().size()) {
                pendingMove = plan.moves().get(moveIndex); before = snapshot;
                var source = at(snapshot, pendingMove.fromBox(), pendingMove.fromSlot());
                if (source == null || !source.uuid().equals(pendingMove.pokemon().uuid()) || at(snapshot, pendingMove.toBox(), pendingMove.toSlot()) != null)
                    return block("layout-changed-before-send");
                requestedAt = tick;
                port.move(pendingMove); return new Tick(Status.REQUESTED, "box-content-move", true);
            }
            if (renameIndex < plan.renames().size()) {
                pendingRename = plan.renames().get(renameIndex); before = snapshot;
                requestedAt = tick;
                port.rename(store, pendingRename.box(), pendingRename.name());
                return new Tick(Status.REQUESTED, "box-name-change", true);
            }
            terminal = true; return new Tick(Status.COMPLETE, "layout-complete", false);
        } catch (RuntimeException failure) { return block("layout-failed:" + failure.getMessage()); }
    }
    private Tick observeMove(PcOrganizerSnapshot after) {
        var source = at(after, pendingMove.fromBox(), pendingMove.fromSlot());
        var destination = at(after, pendingMove.toBox(), pendingMove.toSlot());
        if (source == null && destination != null && destination.uuid().equals(pendingMove.pokemon().uuid())) {
            if (!sameOutside(before, after, pendingMove)) return block("unrelated-layout-delta");
            expected = after; pendingMove = null; before = null; moveIndex++; return new Tick(Status.ACK, "exact-content-ack", false);
        }
        if (same(before, after)) return new Tick(Status.WAITING, "awaiting-content-ack", false);
        return block("content-move-mismatch");
    }
    private Tick observeRename(PcOrganizerSnapshot after) {
        if (!after.pokemon().equals(before.pokemon()) || after.boxCount() != before.boxCount()) return block("layout-changed-during-rename");
        if (after.boxNames().equals(before.boxNames())) return new Tick(Status.WAITING, "awaiting-name-ack", false);
        for (int i = 0; i < after.boxNames().size(); i++) if (i != pendingRename.box() && !after.boxNames().get(i).equals(before.boxNames().get(i))) return block("unrelated-name-delta");
        if (!after.boxNames().get(pendingRename.box()).equals(pendingRename.name())) return block("name-mismatch");
        expected = after; pendingRename = null; before = null; renameIndex++; return new Tick(Status.ACK, "exact-name-ack", false);
    }
    private static PcOrganizerPokemon at(PcOrganizerSnapshot s, int box, int slot) { return s.pokemon().stream().filter(p -> p.box() == box && p.slot() == slot).findFirst().orElse(null); }
    private static boolean same(PcOrganizerSnapshot a, PcOrganizerSnapshot b) { return a.pokemon().equals(b.pokemon()) && a.boxNames().equals(b.boxNames()); }
    private static boolean sameOutside(PcOrganizerSnapshot a, PcOrganizerSnapshot b, PcOrganizerPlan.Move m) {
        if (a.pokemon().size() != b.pokemon().size() || !a.boxNames().equals(b.boxNames())) return false;
        for (var p : a.pokemon()) {
            if (p.box() == m.fromBox() && p.slot() == m.fromSlot()) continue;
            if (p.box() == m.toBox() && p.slot() == m.toSlot()) continue;
            var q = at(b, p.box(), p.slot()); if (q == null || !q.uuid().equals(p.uuid())) return false;
        }
        return true;
    }
    private Tick block(String reason) { terminal = true; return new Tick(Status.BLOCKED, reason, false); }
}

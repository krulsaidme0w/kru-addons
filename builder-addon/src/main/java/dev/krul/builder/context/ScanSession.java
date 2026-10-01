package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;

/** Client-thread-owned lifecycle, separated from Minecraft for deterministic verification. */
public final class ScanSession {
    private final Selection selection = new Selection();
    private Object world;
    private Object connection;
    private Object player;
    private UUID epoch = UUID.randomUUID();
    private long revision;
    private ScanCursor cursor;
    private WorldSnapshot complete;

    public void bind(Object world, Object connection, Object player) {
        if (this.world != world || this.connection != connection || this.player != player) {
            reset();
            this.world = world;
            this.connection = connection;
            this.player = player;
        }
    }

    public void reset() {
        world = null;
        connection = null;
        player = null;
        epoch = UUID.randomUUID();
        revision = 0;
        clearSelection();
    }

    public void setFirst(Cell cell) { selection.setFirst(cell); discardEvidence(); }
    public void setSecond(Cell cell) { selection.setSecond(cell); discardEvidence(); }
    public void clearSelection() { selection.clear(); discardEvidence(); }
    private void discardEvidence() { cursor = null; complete = null; }
    public Optional<Bounds> bounds() { return selection.bounds(); }
    public Optional<WorldSnapshot> snapshot() { return Optional.ofNullable(complete); }

    private boolean ready() { return world != null && connection != null && player != null; }

    private boolean oversize(Bounds bounds) {
        try { return bounds.volume() > ScanCursor.MAX_CELLS; }
        catch (ArithmeticException overflow) { return true; }
    }

    public void requestScan(WorldReader reader) {
        var bounds = bounds();
        if (!ready() || bounds.isEmpty() || oversize(bounds.orElseThrow()) || cursor != null) return;
        cursor = new ScanCursor(epoch, ++revision, bounds.orElseThrow(), reader);
    }

    /** Called once from the client tick, never from render or UI actions. */
    public void tick() {
        if (cursor == null) return;
        var result = cursor.step(ScanCursor.MAX_READS_PER_TICK);
        if (result.isPresent()) {
            complete = result.orElseThrow();
            cursor = null;
        }
    }

    public String status() {
        if (!ready()) return "No world: selection unavailable";
        var bounds = bounds();
        if (bounds.isEmpty()) return "Incomplete selection: enter six coordinates";
        if (oversize(bounds.orElseThrow())) return "Oversize selection: maximum " + ScanCursor.MAX_CELLS + " cells";
        if (cursor != null) return "Scanning; " + (complete == null ? "no complete snapshot yet" :
            "retaining revision " + complete.revision() + ", unknown=" + complete.unknown().size());
        if (complete != null) return "Scan complete: known=" + complete.known().size() + ", unknown=" + complete.unknown().size();
        return "Selection ready: " + bounds.orElseThrow().volume() + " cells";
    }
}

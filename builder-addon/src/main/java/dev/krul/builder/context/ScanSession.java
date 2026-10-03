package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;

/** Client-thread-owned lifecycle, separated from Minecraft for deterministic verification. */
public final class ScanSession {
    private static final long INCOMPLETE_RETRY_TICKS = 20;
    private final Selection selection = new Selection();
    private Object world;
    private Object connection;
    private Object player;
    private UUID epoch = UUID.randomUUID();
    private long revision;
    private long ticks;
    private long retryAtTick;
    private ScanCursor cursor;
    private WorldSnapshot complete;
    private boolean waitingForChunks;
    private boolean retryingIncomplete;

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
    private void discardEvidence() {
        cursor = null; complete = null; retryAtTick = 0;
        waitingForChunks = false; retryingIncomplete = false;
    }
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
        if (!reader.isReady(bounds.orElseThrow())) { waitingForChunks = true; return; }
        waitingForChunks = false;
        cursor = new ScanCursor(epoch, ++revision, bounds.orElseThrow(), reader);
    }

    /** Starts one scan when a ready selection has no active or complete snapshot. */
    public boolean ensureScan(WorldReader reader) {
        if (!ready() || cursor != null || complete != null || bounds().isEmpty() || oversize(bounds().orElseThrow())) return false;
        Bounds selected = bounds().orElseThrow();
        if (!reader.isReady(selected)) { waitingForChunks = true; return false; }
        waitingForChunks = false;
        if (ticks < retryAtTick) return false;
        requestScan(reader);
        return cursor != null;
    }

    /** Called once from the client tick, never from render or UI actions. */
    public void tick() {
        ticks++;
        if (cursor == null) return;
        var result = cursor.step(ScanCursor.MAX_READS_PER_TICK);
        if (result.isPresent()) {
            cursor = null;
            WorldSnapshot candidate = result.orElseThrow();
            if (!candidate.unknown().isEmpty()) {
                complete = null;
                retryingIncomplete = true;
                retryAtTick = ticks + INCOMPLETE_RETRY_TICKS;
                return;
            }
            complete = candidate;
            retryAtTick = 0;
            waitingForChunks = false;
            retryingIncomplete = false;
        }
    }

    public String status() {
        if (!ready()) return "No world: selection unavailable";
        var bounds = bounds();
        if (bounds.isEmpty()) return "Incomplete selection: enter six coordinates";
        if (oversize(bounds.orElseThrow())) return "Oversize selection: maximum " + ScanCursor.MAX_CELLS + " cells";
        if (cursor != null) return "Scanning; " + (complete == null ? "no complete snapshot yet" :
            "retaining revision " + complete.revision() + ", unknown=" + complete.unknown().size());
        if (waitingForChunks) return "Waiting for selected chunks to load";
        if (retryingIncomplete) return "Scan incomplete; retrying after chunks load";
        if (complete != null) return "Scan complete: known=" + complete.known().size() + ", unknown=" + complete.unknown().size();
        return "Selection ready: " + bounds.orElseThrow().volume() + " cells";
    }
}

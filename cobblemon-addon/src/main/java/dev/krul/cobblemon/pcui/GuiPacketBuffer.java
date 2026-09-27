package dev.krul.cobblemon.pcui;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** Pure, connection/world-scoped FIFO. The caller owns packet classification and client-thread dispatch. */
public final class GuiPacketBuffer<P> {
    public enum Disposition { PASS, BLOCKED, QUEUED, FULL }
    private final int capacity;
    private final ArrayDeque<P> queue = new ArrayDeque<>();
    private Object connection, world;
    private boolean active, send = true, delay;
    private boolean flushing;

    public GuiPacketBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void context(Object connection, Object world, boolean enabled) {
        boolean nextActive = enabled && connection != null && world != null;
        if (this.connection != connection || this.world != world || active != nextActive) reset();
        this.connection = connection;
        this.world = world;
        active = nextActive;
    }

    public synchronized Disposition intercept(P packet) {
        Objects.requireNonNull(packet);
        if (!active) return Disposition.PASS;
        if (!send) return Disposition.BLOCKED;
        if (!delay) return Disposition.PASS;
        if (queue.size() >= capacity) return Disposition.FULL;
        queue.addLast(packet);
        return Disposition.QUEUED;
    }

    public synchronized boolean sendEnabled() { return send; }
    public synchronized boolean delayEnabled() { return delay; }
    public synchronized int queued() { return queue.size(); }
    public synchronized void setSendEnabled(boolean enabled) { send = enabled; }
    /** Dispatch is deliberately separate: only the runtime may select the current connection. */
    public synchronized void setDelayEnabled(boolean enabled) { delay = enabled; }
    public synchronized void clear() { queue.clear(); }
    public synchronized void reset() { clear(); send = true; delay = false; }

    /** Remove before dispatch: a throwing sender is never retried, even if it sent before throwing. */
    public synchronized void flush(Consumer<P> sender) {
        Objects.requireNonNull(sender);
        if (flushing) return;
        flushing = true;
        try {
            int count = queue.size();
            while (active && count-- > 0 && !queue.isEmpty()) sender.accept(queue.removeFirst());
        } finally {
            flushing = false;
        }
    }
}

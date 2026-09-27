package dev.krul.tools.packet;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** Adapted from DupersUnited PacketPauseManager; see THIRD_PARTY.md. */
public final class PacketPauseSession<T> {
    public enum Result { BYPASS, HELD, FULL }
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final int capacity;
    private Object connection;
    private Object world;
    private Object player;
    private boolean respawning;
    private boolean releasing;

    public PacketPauseSession(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void bindPlayer(Object connection, Object world, Object player) {
        if (this.connection != connection || this.world != world || this.player != player) {
            queue.clear();
            respawning = false;
        }
        this.connection = connection;
        this.world = world;
        this.player = player;
    }

    public synchronized Result holdFrom(Object origin, T packet, boolean selected) {
        if (origin != connection || connection == null || world == null || player == null
            || respawning || releasing || !selected) return Result.BYPASS;
        Objects.requireNonNull(packet);
        if (queue.size() >= capacity) return Result.FULL;
        queue.addLast(packet);
        return Result.HELD;
    }

    public synchronized void flushTo(Object destination, Consumer<T> sender) {
        if (destination != connection) return;
        if (releasing) return;
        if (connection == null || world == null || player == null || respawning) { queue.clear(); return; }
        releasing = true;
        try {
            T packet;
            while ((packet = queue.pollFirst()) != null) sender.accept(packet);
        } finally {
            // Failed/closed transports must not leave a replayable stale tail.
            queue.clear();
            releasing = false;
        }
    }

    public synchronized int size() { return queue.size(); }
    public synchronized void clear() { queue.clear(); }
    public synchronized void respawn(Object origin) {
        if (origin != connection || connection == null) return;
        queue.clear();
        respawning = true;
    }
}

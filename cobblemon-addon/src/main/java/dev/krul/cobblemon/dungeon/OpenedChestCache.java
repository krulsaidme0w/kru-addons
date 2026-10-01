package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Session-only evidence that a dungeon chest has already been opened by any player. */
public final class OpenedChestCache {
    private static final long PENDING_ACTIVATION_TICKS = 100;
    private final Set<Key> opened = new HashSet<>();
    private Pending pending;

    public void noteActivation(String dimension, BlockPos position, long tick) {
        pending = dimension == null || position == null ? null
            : new Pending(dimension, position.toImmutable(), tick);
    }

    /** Called only after a generic container screen actually opens. */
    public boolean containerOpened(String dimension, long tick) {
        Pending activation = pending;
        pending = null;
        if (activation == null || dimension == null || !activation.dimension.equals(dimension)) return false;
        long age = tick - activation.tick;
        if (age < 0 || age > PENDING_ACTIVATION_TICKS) return false;
        markOpen(dimension, activation.position);
        return true;
    }

    public void cancelPending() { pending = null; }

    public boolean markOpen(String dimension, BlockPos position) {
        if (dimension == null || position == null) return false;
        return opened.add(new Key(dimension, position.getX(), position.getY(), position.getZ()));
    }

    public boolean isOpen(String dimension, BlockPos position) {
        return dimension != null && position != null
            && opened.contains(new Key(dimension, position.getX(), position.getY(), position.getZ()));
    }

    public void beginDimension(String dimension) {
        opened.removeIf(key -> !key.dimension.equals(dimension));
        if (pending != null && !pending.dimension.equals(dimension)) pending = null;
    }

    public void clear() {
        opened.clear();
        pending = null;
    }

    public int size() {
        return opened.size();
    }

    private record Key(String dimension, int x, int y, int z) {}
    private record Pending(String dimension, BlockPos position, long tick) {}
}

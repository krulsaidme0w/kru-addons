package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/** Session-only evidence that a dungeon chest has already been opened by any player. */
public final class OpenedChestCache {
    private final Set<Key> opened = new HashSet<>();

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
    }

    public void clear() {
        opened.clear();
    }

    public int size() {
        return opened.size();
    }

    private record Key(String dimension, int x, int y, int z) {}
}


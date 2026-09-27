package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Session-only guardian evidence keyed by dimension and block position, not entity UUID. */
public final class GuardianPositionCache {
    private final Set<Key> positions = new HashSet<>();

    public boolean remember(String dimension, BlockPos position) {
        if (dimension == null || dimension.isBlank() || position == null) return false;
        return positions.add(new Key(dimension, position.getX(), position.getY(), position.getZ()));
    }

    public boolean contains(String dimension, BlockPos position) {
        return dimension != null && position != null
            && positions.contains(new Key(dimension, position.getX(), position.getY(), position.getZ()));
    }

    public List<BlockPos> positions(String dimension) {
        if (dimension == null) return List.of();
        List<BlockPos> result = new ArrayList<>();
        for (Key key : positions) if (dimension.equals(key.dimension)) result.add(new BlockPos(key.x, key.y, key.z));
        return List.copyOf(result);
    }

    public void beginDimension(String dimension) {
        positions.removeIf(key -> !key.dimension.equals(dimension));
    }

    public void clear() {
        positions.clear();
    }

    public int size() {
        return positions.size();
    }

    private record Key(String dimension, int x, int y, int z) {}
}


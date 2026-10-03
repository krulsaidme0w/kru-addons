package dev.krul.builder.minecraft;

import dev.krul.builder.context.WorldReader;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

/** Reads only already-loaded client chunks. A reader is bound to one world identity. */
public final class ClientWorldReader implements WorldReader {
    private final MinecraftClient client;
    private final ClientWorld world;

    public ClientWorldReader(MinecraftClient client, ClientWorld world) {
        this.client = Objects.requireNonNull(client);
        this.world = Objects.requireNonNull(world);
    }

    @Override
    public boolean isReady(dev.krul.builder.model.Bounds bounds) {
        if (!client.isOnThread() || client.world != world
            || world.isOutOfHeightLimit(bounds.min().y()) || world.isOutOfHeightLimit(bounds.max().y())) return false;
        int minChunkX = bounds.min().x() >> 4, maxChunkX = bounds.max().x() >> 4;
        int minChunkZ = bounds.min().z() >> 4, maxChunkZ = bounds.max().z() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) return false;
            }
        }
        return true;
    }

    @Override
    public Optional<StateKey> read(Cell cell) {
        if (!client.isOnThread()) throw new IllegalStateException("World reads require the client thread");
        if (client.world != world || world.isOutOfHeightLimit(cell.y())) return Optional.empty();
        // false is essential: neither request a load nor obtain the synthetic empty chunk.
        var chunk = world.getChunkManager().getChunk(cell.x() >> 4, cell.z() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) return Optional.empty();
        var state = chunk.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
        var properties = new HashMap<String, String>();
        for (var property : state.getProperties()) properties.put(property.getName(), value(state, property));
        return Optional.of(new StateKey(Registries.BLOCK.getId(state.getBlock()).toString(), properties));
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }
}

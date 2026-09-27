package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;

public record DungeonRenderTarget(
    String categoryId,
    boolean nametagOnly,
    String identity,
    String label,
    BlockPos position,
    double distance
) {
}

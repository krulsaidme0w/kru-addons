package dev.krul.cobblemon.dungeon;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Client-thread selection shared by HUD, 2D and 3D for twenty ticks (normally one second). */
public final class DungeonRenderCache {
    private long tick;
    private Object world;
    private Object configuration;
    private List<DungeonRenderTarget> targets;

    public List<DungeonRenderTarget> get(long tick, Object world, Object configuration,
                                         Supplier<List<DungeonRenderTarget>> selection) {
        if (targets == null || tick < this.tick || tick - this.tick >= 20 || this.world != world
            || !Objects.equals(this.configuration, configuration)) {
            var selected = selection.get();
            this.tick = tick;
            this.world = world;
            this.configuration = configuration;
            targets = selected;
        }
        return targets;
    }

    public void clear() {
        targets = null;
        world = null;
        configuration = null;
    }
}


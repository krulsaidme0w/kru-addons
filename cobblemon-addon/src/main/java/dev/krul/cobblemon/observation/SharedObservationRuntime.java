package dev.krul.cobblemon.observation;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

/** Single always-on lifecycle owner. Both Discovery consumers subscribe to this same instance. */
public final class SharedObservationRuntime {
    private static final ObservationRuntime RUNTIME = new ObservationRuntime(64, 1024);
    private static ClientObservationSource source;
    private static boolean initialized;
    private SharedObservationRuntime() {}
    public static ObservationRuntime runtime() { return RUNTIME; }
    public static void init() {
        if (initialized) return;
        initialized = true;
        source = new ClientObservationSource(MinecraftClient.getInstance());
        MeteorClient.EVENT_BUS.subscribe(SharedObservationRuntime.class);
    }
    @EventHandler private static void tick(TickEvent.Pre event) {
        var mc = MinecraftClient.getInstance();
        RUNTIME.tick(mc.getNetworkHandler(), mc.player == null ? null : mc.world, source, System.currentTimeMillis());
        if (mc.world == null) source.reset();
    }
    @EventHandler private static void left(GameLeftEvent event) {
        RUNTIME.tick(null, null, source, System.currentTimeMillis()); source.reset();
    }
}

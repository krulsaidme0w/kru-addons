package dev.krul.cobblemon.discovery;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;

/** Always-on owner for server spawn-map evidence; Discovery toggles do not erase it. */
public final class DiscoveryRarityRuntime {
    private static final DiscoveryRarityEvidence EVIDENCE = new DiscoveryRarityEvidence();
    private static boolean initialized;
    private static Object connection;

    private DiscoveryRarityRuntime() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        MeteorClient.EVENT_BUS.subscribe(DiscoveryRarityRuntime.class);
    }

    public static DiscoveryRarityEvidence evidence() {
        return EVIDENCE;
    }

    public static void reset() {
        EVIDENCE.reset();
    }

    @EventHandler private static void onTick(TickEvent.Pre event) { bindCurrentScope(); }

    private static void bindCurrentScope() {
        var mc = MinecraftClient.getInstance();
        Object current = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getConnection();
        if (connection != current) { connection = current; EVIDENCE.reset(); }
    }

    @EventHandler
    private static void onPacket(PacketEvent.Receive event) {
        var mc = MinecraftClient.getInstance();
        var origin = event.connection;
        var world = mc.world;
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getConnection() != origin) return;
        CobblemonSpawnMapPacketReader.SpawnMap spawnMap = CobblemonSpawnMapPacketReader.read(event.packet);
        if (!spawnMap.found()) return;
        mc.execute(() -> {
            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getConnection() != origin) return;
            bindCurrentScope();
            spawnMap.entries().stream().limit(2048).forEach(entry -> EVIDENCE.observeServer(entry.species(), entry.spawnRarity()));
        });
    }

    @EventHandler
    private static void onGameLeft(GameLeftEvent event) {
        connection = null;
        reset();
    }

    @EventHandler
    private static void onGameJoined(GameJoinedEvent event) {
        // A joined session may reuse the client process with a different
        // server/player identity; never carry rarity evidence across it.
        reset();
    }
}

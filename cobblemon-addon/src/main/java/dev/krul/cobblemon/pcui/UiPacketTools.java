package dev.krul.cobblemon.pcui;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.text.Text;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Independent UIUtils-intent implementation; never hooks chat, movement, keepalive or all payloads. */
public final class UiPacketTools {
    private static final UiPacketTools SHARED = new UiPacketTools();
    public static final int QUEUE_LIMIT = 512;
    private static final String SERVER = "com.cobblemon.mod.common.net.messages.server.";
    private static final Set<String> INVENTORY_PAYLOADS = Set.of(
        SERVER + "storage.pc.MovePCPokemonPacket",
        SERVER + "storage.pc.MovePCPokemonToPartyPacket",
        SERVER + "storage.pc.MovePartyPokemonToPCPacket",
        SERVER + "storage.pc.SwapPCPokemonPacket",
        SERVER + "storage.SwapPCPartyPokemonPacket",
        SERVER + "storage.party.MovePartyPokemonPacket",
        SERVER + "storage.party.SwapPartyPokemonPacket",
        SERVER + "storage.pc.RequestRenamePCBoxPacket",
        SERVER + "pokemon.update.SetNicknamePacket"
    );
    private final GuiPacketBuffer<Packet<?>> buffer = new GuiPacketBuffer<>(QUEUE_LIMIT);
    private final ThreadLocal<Packet<?>> replay = new ThreadLocal<>();
    private final LocalGuiChange localChange = new LocalGuiChange();
    private volatile BooleanSupplier active = () -> false;
    private boolean registered;

    private UiPacketTools() {}
    public static UiPacketTools shared() { return SHARED; }

    public synchronized void configure(BooleanSupplier active) {
        this.active = Objects.requireNonNull(active);
        if (!registered) { MeteorClient.EVENT_BUS.subscribe(this); registered = true; }
        context();
    }

    public boolean sendEnabled() { context(); return buffer.sendEnabled(); }
    public boolean delayEnabled() { context(); return buffer.delayEnabled(); }
    public int queued() { context(); return buffer.queued(); }
    public void setSendEnabled(boolean enabled) { onClient(() -> { if (context()) buffer.setSendEnabled(enabled); }); }
    public void setDelayEnabled(boolean enabled) {
        onClient(() -> {
            if (!context()) return;
            buffer.setDelayEnabled(enabled);
            if (!enabled) flushNow();
        });
    }
    public void flush() { onClient(this::flushNow); }
    public void clear() { buffer.clear(); }
    public void reset() { buffer.reset(); }
    void restoreLocally(Runnable change) { localChange.run(change); }

    public void disconnectAndSend() {
        onClient(() -> {
            if (!context()) return;
            ClientConnection connection = connection();
            Object world = mc.world;
            // Normal connection.send dispatches each queued write before the channel close request.
            // This is dispatch ordering, not a promise of server acknowledgement before disconnect.
            flushNow();
            if (connection == connection() && world == mc.world && connection != null)
                connection.disconnect(Text.literal("Disconnected after flushing UI packets"));
            reset();
        });
    }

    /** Desync deliberately sends only the native close/unlink message and retains the local screen. */
    public String desync(Screen screen) {
        if (mc == null || !mc.isOnThread()) return "Desync requires the client thread";
        if (!context() || mc.player == null || screen == null || mc.currentScreen != screen)
            return "Screen or connection is no longer available";
        if (screen instanceof HandledScreen<?> handled) {
            connection().send(new CloseHandledScreenC2SPacket(handled.getScreenHandler().syncId));
            return "Server close requested; local screen retained";
        }
        NativePcAccess pc = new NativePcAccess();
        if (!pc.live(screen)) return "Desync is unavailable for this screen";
        Object store = PcReflection.get(screen, "getPc");
        if (!(PcReflection.get(store, "getUuid") instanceof UUID)) return "PC identity unavailable";
        try {
            // Cobblemon 1.8.1: native PCGUI.closeNormally uses ()V. Server resolves PLAYER UUID
            // from the connection; no PC UUID is encoded. Never invent a vanilla sync ID here.
            Class<?> type = Class.forName(SERVER + "storage.pc.UnlinkPlayerFromPCPacket");
            Object packet = type.getConstructor().newInstance();
            type.getMethod("sendToServer").invoke(packet);
            return "PC unlink requested; local screen retained";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return "Native PC unlink is unavailable";
        }
    }

    /** Fabrication is for native ScreenHandlers only; PCGUI has a separate payload contract. */
    public static boolean supports(Screen screen) { return screen instanceof HandledScreen<?>; }
    public void openFabricator(Screen parent) {
        onClient(() -> {
            if (context() && supports(parent) && mc.currentScreen == parent)
                mc.setScreen(new PacketFabricatorScreen(parent));
        });
    }

    boolean available() { return context(); }
    static boolean inventoryPayload(String className) { return INVENTORY_PAYLOADS.contains(className); }
    private static boolean inventoryPacket(Packet<?> packet) {
        return packet instanceof ClickSlotC2SPacket || packet instanceof ButtonClickC2SPacket || packet instanceof RenameItemC2SPacket
            || packet instanceof CustomPayloadC2SPacket custom && inventoryPayload(custom.payload().getClass().getName());
    }
    private static ClientConnection connection() {
        return mc == null || mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getConnection();
    }
    private boolean context() {
        ClientConnection connection = connection();
        Object world = mc == null ? null : mc.world;
        boolean enabled = active.getAsBoolean() && connection != null && connection.isOpen() && world != null;
        buffer.context(connection, world, enabled);
        return enabled;
    }
    private static void onClient(Runnable action) {
        if (mc == null) return;
        if (mc.isOnThread()) action.run(); else mc.execute(action);
    }
    private void flushNow() {
        if (!context()) return;
        ClientConnection connection = connection();
        Object world = mc.world;
        buffer.flush(packet -> {
            if (!context() || connection != connection() || world != mc.world) { buffer.clear(); return; }
            replay.set(packet);
            try { connection.send(packet); }
            finally { replay.remove(); }
        });
    }

    @EventHandler private void tick(TickEvent.Pre event) { context(); }
    @EventHandler private void left(GameLeftEvent event) { buffer.context(null, null, false); }
    @EventHandler private void send(PacketEvent.Send event) {
        boolean guiLifecycle = inventoryPacket(event.packet) || event.packet instanceof CloseHandledScreenC2SPacket
            || event.packet instanceof CustomPayloadC2SPacket custom
                && custom.payload().getClass().getName().equals(SERVER + "storage.pc.UnlinkPlayerFromPCPacket");
        if (localChange.suppresses(guiLifecycle)) { event.cancel(); return; }
        if (replay.get() == event.packet || !inventoryPacket(event.packet)) return;
        if (!context() || event.connection != connection() || event.isCancelled()) return;
        GuiPacketBuffer.Disposition result = buffer.intercept(event.packet);
        if (result != GuiPacketBuffer.Disposition.PASS) event.cancel();
        if (result == GuiPacketBuffer.Disposition.FULL) {
            onClient(() -> { if (mc.player != null) mc.player.sendMessage(
                Text.literal("UI packet queue full (512); newest packet discarded. Flush or clear."), true); });
        }
    }
}

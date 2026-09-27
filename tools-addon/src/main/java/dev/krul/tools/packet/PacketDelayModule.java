package dev.krul.tools.packet;

import dev.krul.tools.ToolsCategory;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import java.util.Set;

/** DupersUnited's selective outgoing hold/release, integrated through Meteor events/UI. */
public final class PacketDelayModule extends Module {
    private final PacketPauseSession<Packet<?>> session = new PacketPauseSession<>(4096);
    private final Setting<Set<Class<? extends Packet<?>>>> packets = settings.getDefaultGroup().add(
        new PacketListSetting.Builder()
            .name("outgoing-packets")
            .description("Hold only these outgoing classes. Empty means nothing. KeepAlive/teleport delays can disconnect you.")
            .filter(type -> PacketUtils.getC2SPackets().contains(type))
            .build());

    public PacketDelayModule() {
        super(ToolsCategory.INSTANCE, "packet-delay", "Hold selected outgoing packets; turning off sends the queue on the same world/connection.");
        // Meteor must not invoke manual flush callbacks at automatic world boundaries.
        runInMainMenu = true;
    }

    private void bind() {
        var handler = mc.getNetworkHandler();
        session.bindPlayer(handler != null && handler.getConnection().isOpen() ? handler.getConnection() : null, mc.world, mc.player);
    }

    @Override public void onActivate() { session.clear(); bind(); }
    @Override public void onDeactivate() { sendQueued(); session.bindPlayer(null, null, null); }
    @Override public String getInfoString() { return Integer.toString(session.size()); }

    @EventHandler private void onTick(TickEvent.Pre event) { bind(); }
    @EventHandler private void onLeft(GameLeftEvent event) { session.bindPlayer(null, null, null); }
    @EventHandler private void onJoined(GameJoinedEvent event) { session.bindPlayer(null, null, null); }
    @EventHandler private void onReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerRespawnS2CPacket) session.respawn(event.connection);
    }

    @EventHandler private void onSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        bind();
        switch (session.holdFrom(event.connection, event.packet, packets.get().contains(event.packet.getClass()))) {
            case HELD -> event.cancel();
            case FULL -> {
                // Release the older queue before allowing this packet through.
                sendQueued();
                warning("Queue limit reached: released held packets.");
            }
            case BYPASS -> { }
        }
    }

    private void sendQueued() {
        bind();
        var handler = mc.getNetworkHandler();
        if (handler == null) { session.clear(); return; }
        try { session.flushTo(handler.getConnection(), handler::sendPacket); }
        catch (RuntimeException failure) {
            error("Packet send failed; remaining queue discarded: %s", failure.getClass().getSimpleName());
        }
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Selected C2S only. Off = send. World/connection change = discard."));
        list.add(theme.button("Send queued now")).expandX().widget().action = this::sendQueued;
        list.add(theme.button("Discard queued")).expandX().widget().action = session::clear;
        return list;
    }
}

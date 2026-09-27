package dev.krul.cobblemon.pcui;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import java.util.concurrent.atomic.AtomicLong;

/** Observe lifecycle only; never cancel, queue, inspect inventory contents or delay packets. */
final class UiSessionSignals {
    private static final UiSessionSignals SHARED = new UiSessionSignals();
    private static boolean initialized;
    private final AtomicLong pc = new AtomicLong(), container = new AtomicLong();
    static UiSessionSignals shared() {
        if (!initialized) { MeteorClient.EVENT_BUS.subscribe(SHARED); initialized = true; }
        return SHARED;
    }
    long pcRevision() { return pc.get(); }
    long containerRevision() { return container.get(); }
    void payload(String type) {
        switch (type) {
            case "com.cobblemon.mod.common.net.messages.client.storage.pc.ClosePCPacket",
                 "com.cobblemon.mod.common.net.messages.client.storage.pc.OpenPCPacket",
                 "com.cobblemon.mod.common.net.messages.server.storage.pc.UnlinkPlayerFromPCPacket" -> pc.incrementAndGet();
            default -> { }
        }
    }
    @EventHandler private void receive(PacketEvent.Receive event) {
        if (event.packet instanceof CloseScreenS2CPacket) container.incrementAndGet();
        if (event.packet instanceof CustomPayloadS2CPacket custom) payload(custom.payload().getClass().getName());
    }
    @EventHandler private void send(PacketEvent.Send event) {
        if (event.packet instanceof CloseHandledScreenC2SPacket) container.incrementAndGet();
        if (event.packet instanceof CustomPayloadC2SPacket custom) payload(custom.payload().getClass().getName());
    }
}

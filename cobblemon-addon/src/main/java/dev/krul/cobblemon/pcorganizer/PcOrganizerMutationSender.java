package dev.krul.cobblemon.pcorganizer;

/** Narrow packet boundary: it emits only the exact move chosen by the coordinator. */
public final class PcOrganizerMutationSender implements PcOrganizerCoordinator.MutationPort, PcOrganizerLayoutCoordinator.MutationPort {
    @Override public void move(PcOrganizerPlan.Move move) {
        try {
            var snapshot = new PcOrganizerLiveReader().read(net.minecraft.client.MinecraftClient.getInstance().currentScreen)
                .orElseThrow(() -> new IllegalStateException("Live PC unavailable"));
            var source = snapshot.pokemon().stream().filter(p -> p.box() == move.fromBox() && p.slot() == move.fromSlot()).findFirst().orElseThrow();
            if (!source.uuid().equals(move.pokemon().uuid())) throw new IllegalStateException("PC source changed");
            var destination = snapshot.pokemon().stream().filter(p -> p.box() == move.toBox() && p.slot() == move.toSlot()).findFirst().orElse(null);
            Class<?> position = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
            Object from = position.getConstructor(int.class, int.class).newInstance(move.fromBox(), move.fromSlot());
            Object to = position.getConstructor(int.class, int.class).newInstance(move.toBox(), move.toSlot());
            Class<?> packet = Class.forName("com.cobblemon.mod.common.net.messages.server.storage.pc." + packetName(destination != null));
            Object request = destination == null
                ? packet.getConstructor(java.util.UUID.class, position, position).newInstance(source.uuid(), from, to)
                : packet.getConstructor(java.util.UUID.class, position, java.util.UUID.class, position).newInstance(source.uuid(), from, destination.uuid(), to);
            packet.getMethod("sendToServer").invoke(request);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Cobblemon PC move packet unavailable", failure);
        }
    }
    static String packetName(boolean occupied) { return occupied ? "SwapPCPokemonPacket" : "MovePCPokemonPacket"; }
    @Override public void rename(java.util.UUID store, int box, String name) {
        try {
            var live = new PcOrganizerLiveReader().read(net.minecraft.client.MinecraftClient.getInstance().currentScreen).orElseThrow();
            if (!live.storeUuid().equals(store)) throw new IllegalStateException("PC store changed");
            Class<?> packet = Class.forName("com.cobblemon.mod.common.net.messages.server.storage.pc.RequestRenamePCBoxPacket");
            Object request = packet.getConstructor(java.util.UUID.class, int.class, String.class).newInstance(store, box, name);
            packet.getMethod("sendToServer").invoke(request);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Cobblemon PC rename packet unavailable", failure);
        }
    }
}

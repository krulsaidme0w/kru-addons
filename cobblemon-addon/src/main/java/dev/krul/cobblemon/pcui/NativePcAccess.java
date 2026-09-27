package dev.krul.cobblemon.pcui;

import java.util.function.BooleanSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reflection boundary for the optional Cobblemon runtime. */
final class NativePcAccess implements ManualPcActions.Access {
    private static final String ROOT = "com.cobblemon.mod.common.";
    private final Classes classes;
    interface Classes { Class<?> find(String name) throws ClassNotFoundException; }
    NativePcAccess() { this(Class::forName); }
    NativePcAccess(Classes classes) { this.classes = classes; }
    public boolean supports(Object screen) {
        try { return screen != null && screen.getClass() == classes.find(ROOT + "client.gui.pc.PCGUI"); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
    public boolean live(Object screen) {
        if (!supports(screen)) return false;
        try {
            Object pc = required(screen, "getPc"), party = required(screen, "getParty");
            Object manager = required(singleton("client.CobblemonClient"), "getStorage");
            Object stores = required(manager, "getPcStores");
            return stores instanceof Map<?, ?> map && required(pc, "getUuid") instanceof UUID uuid
                && map.get(uuid) == pc && required(manager, "getParty") == party;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
    public ManualPcActions.Snapshot read(Object screen, Integer box) throws Exception {
        if (!live(screen)) throw unavailable("Live PC/party identity unavailable");
        Object configuration = required(screen, "getConfiguration");
        if (configuration.getClass() != classes.find(ROOT + "client.gui.pc.PCGUIConfiguration")
            || !Boolean.TRUE.equals(required(configuration, "getShowParty"))
            || value(configuration, "getSelectOverride") != null)
            throw unavailable("Selection-only PC does not permit manual transfers");
        Object pc = required(screen, "getPc"), party = required(screen, "getParty");
        // Retained screen is metadata only. Never use boxSlots/partySlots widgets or restore it.
        Object selected = box == null ? required(required(screen, "getStorage"), "getBox") : box;
        if (!(selected instanceof Integer index)) throw unavailable("Current box unavailable");
        Object rawBoxes = required(pc, "getBoxes");
        if (!(rawBoxes instanceof List<?> boxes) || index < 0 || index >= boxes.size())
            throw unavailable("Current box unavailable");
        List<UUID> boxSlots = uuids(required(boxes.get(index), "getSlots"));
        List<UUID> partySlots = uuids(required(party, "getSlots"));
        Object prevent = required(singleton("client.settings.ServerSettings"), "getPreventCompletePartyDeposit");
        if (!(prevent instanceof Boolean flag)) throw unavailable("Party deposit policy unavailable");
        return new ManualPcActions.Snapshot(pc, party, index, boxSlots, partySlots, flag);
    }
    public void send(Object screen, ManualPcActions.Snapshot before, ManualPcActions.Transfer move, BooleanSupplier valid) throws Exception {
        Class<?> pcPosition = classes.find(ROOT + "api.storage.pc.PCPosition");
        Class<?> partyPosition = classes.find(ROOT + "api.storage.party.PartyPosition");
        Object pc = pcPosition.getConstructor(int.class, int.class)
            .newInstance(move.box(), move.intoPc() ? move.destination() : move.source());
        Object party = partyPosition.getConstructor(int.class)
            .newInstance(move.intoPc() ? move.source() : move.destination());
        Class<?> packetType = classes.find(ROOT + "net.messages.server.storage.pc."
            + (move.intoPc() ? "MovePartyPokemonToPCPacket" : "MovePCPokemonToPartyPacket"));
        Object packet = move.intoPc()
            ? packetType.getConstructor(UUID.class, partyPosition, pcPosition).newInstance(move.uuid(), party, pc)
            : packetType.getConstructor(UUID.class, pcPosition, partyPosition).newInstance(move.uuid(), pc, party);
        var send = packetType.getMethod("sendToServer");
        // Recheck live capacity and identities after reflection/preparation, immediately before dispatch.
        ManualPcActions.Snapshot fresh = read(screen, null);
        if (fresh.pc() != before.pc() || fresh.party() != before.party() || !fresh.equals(before))
            throw unavailable("Storage changed before send");
        if (!valid.getAsBoolean()) throw unavailable("PC session invalidated before send");
        send.invoke(packet);
    }

    private Object singleton(String name) throws ReflectiveOperationException {
        return classes.find(ROOT + name).getField("INSTANCE").get(null);
    }
    private static List<UUID> uuids(Object values) {
        if (!(values instanceof List<?> slots) || slots.isEmpty()) throw unavailable("Storage slots unavailable");
        List<UUID> result = new ArrayList<>(slots.size());
        for (Object pokemon : slots) {
            if (pokemon == null) result.add(null);
            else if (required(pokemon, "getUuid") instanceof UUID uuid) result.add(uuid);
            else throw unavailable("Pokemon UUID unavailable");
        }
        return result;
    }
    private static Object required(Object receiver, String getter) {
        Object result = value(receiver, getter);
        if (result == null) throw unavailable(getter + " unavailable");
        return result;
    }
    private static Object value(Object receiver, String getter) {
        var read = PcReflection.read(receiver, getter);
        if (!read.available()) throw unavailable(getter + " unavailable");
        return read.value();
    }
    private static IllegalStateException unavailable(String reason) { return new IllegalStateException(reason); }
}

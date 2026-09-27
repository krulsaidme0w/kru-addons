package dev.krul.cobblemon.pcui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the real reflection adapter against in-memory native-shaped storage and packet boundaries. */
class NativePcAccessTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private final Screen screen = new Screen();
    private final NativePcAccess nativeAccess = new NativePcAccess(NativePcAccessTest::type);
    @BeforeEach void connect() {
        Client.INSTANCE = new Client();
        Settings.INSTANCE = new Settings();
        Client.INSTANCE.storage.party = screen.party;
        Client.INSTANCE.storage.pcs.put(screen.pc.getUuid(), screen.pc);
        screen.pc.boxes.add(new UnreadBox());
        screen.pc.boxes.add(new Box());
        screen.pc.boxes.get(1).slots.set(0, new Pokemon(B));
        screen.party.slots.set(1, new Pokemon(A));
        Packets.sent.clear();
        Packets.constructed = () -> {};
    }
    @Test void acceptsOnlyOrdinaryPcAndRequiresLiveStorageObjectIdentity() {
        assertTrue(nativeAccess.supports(screen));
        assertFalse(nativeAccess.supports(new Object()));
        assertTrue(nativeAccess.live(screen));
        Client.INSTANCE.storage.pcs.put(screen.pc.getUuid(), new Pc());
        assertFalse(nativeAccess.live(screen));
    }
    @Test void partyReplacementOrPcRemovalInvalidatesRetainedHandle() {
        Client.INSTANCE.storage.party = new Party();
        assertFalse(nativeAccess.live(screen));
        Client.INSTANCE.storage.party = screen.party;
        Client.INSTANCE.storage.pcs.clear();
        assertFalse(nativeAccess.live(screen));
    }
    @Test void readsLiveListsAndCurrentBoxMetadataWithoutReadingSlotWidgetsOrOtherBoxes() throws Exception {
        var first = nativeAccess.read(screen, null);
        assertEquals(1, first.box());
        assertEquals(B, first.boxSlots().get(0));
        assertEquals(A, first.partySlots().get(1));
        screen.pc.boxes.get(1).slots.set(0, null);
        assertNull(nativeAccess.read(screen, null).boxSlots().get(0));
        assertEquals(B, first.boxSlots().get(0), "Snapshot must not alias the native slot list");
        screen.metadata.box = 0;
        assertEquals(1, nativeAccess.read(screen, 1).box());
    }
    @Test void unknownBoxAndMalformedPokemonAreUnavailableRatherThanEmpty() {
        screen.metadata.box = -1;
        assertThrows(Exception.class, () -> nativeAccess.read(screen, null));
        screen.metadata.box = 1;
        screen.party.slots.set(0, new Pokemon(null));
        assertThrows(Exception.class, () -> nativeAccess.read(screen, null));
    }
    @Test void hiddenPartyOrSelectionOverrideCannotUseTransferAdapter() {
        screen.configuration.showParty = false;
        assertThrows(Exception.class, () -> nativeAccess.read(screen, null));
        screen.configuration.showParty = true;
        screen.configuration.override = new Object();
        assertThrows(Exception.class, () -> nativeAccess.read(screen, null));
    }
    @Test void readsServerRestrictionOnLastPartyDeposit() throws Exception {
        Settings.INSTANCE.prevent = true;
        assertTrue(nativeAccess.read(screen, null).preventLastDeposit());
    }
    @Test void depositUsesNativeUuidAndTypedPartyThenPcPositionsExactlyOnce() throws Exception {
        var before = nativeAccess.read(screen, null);
        nativeAccess.send(screen, before, new ManualPcActions.Transfer(A, true, 1, 1, 1), () -> true);
        assertEquals(1, Packets.sent.size());
        InPacket sent = assertInstanceOf(InPacket.class, Packets.sent.getFirst());
        assertEquals(A, sent.uuid);
        assertEquals(1, sent.from.slot);
        assertEquals(1, sent.to.box);
        assertEquals(1, sent.to.slot);
        assertNotNull(screen.party.slots.get(1), "Sending must not optimistically mutate live storage");
        assertNull(screen.pc.boxes.get(1).slots.get(1));
    }
    @Test void withdrawalUsesNativeUuidAndTypedPcThenPartyPositions() throws Exception {
        nativeAccess.send(screen, nativeAccess.read(screen, null), new ManualPcActions.Transfer(B, false, 1, 0, 0), () -> true);
        OutPacket sent = assertInstanceOf(OutPacket.class, Packets.sent.getFirst());
        assertEquals(B, sent.uuid);
        assertEquals(1, sent.from.box);
        assertEquals(0, sent.from.slot);
        assertEquals(0, sent.to.slot);
    }
    @Test void changesBetweenPlanningAndSendFailClosed() throws Exception {
        var before = nativeAccess.read(screen, null);
        screen.pc.boxes.get(1).slots.set(1, new Pokemon(new UUID(0, 9)));
        assertThrows(Exception.class, () -> nativeAccess.send(screen, before,
            new ManualPcActions.Transfer(A, true, 1, 1, 1), () -> true));
        assertTrue(Packets.sent.isEmpty());
    }
    @Test void validityIsCheckedAfterPacketPreparationImmediatelyBeforeDispatch() throws Exception {
        var before = nativeAccess.read(screen, null);
        AtomicBoolean valid = new AtomicBoolean(true);
        Packets.constructed = () -> valid.set(false);
        assertThrows(Exception.class, () -> nativeAccess.send(screen, before,
            new ManualPcActions.Transfer(A, true, 1, 1, 1), valid::get));
        assertTrue(Packets.sent.isEmpty());
    }
    // These fixtures expose only the ABI used by the adapter. No GUI mutation methods exist.
    public static class Screen {
        final Pc pc = new Pc(); final Party party = new Party();
        final Metadata metadata = new Metadata(); final Configuration configuration = new Configuration();
        public Pc getPc() { return pc; }
        public Party getParty() { return party; }
        public Metadata getStorage() { return metadata; }
        public Configuration getConfiguration() { return configuration; }
    }
    public static class Configuration {
        boolean showParty = true; Object override;
        public boolean getShowParty() { return showParty; }
        public Object getSelectOverride() { return override; }
    }
    public static class Metadata { int box = 1; public int getBox() { return box; } }
    public static class Pc {
        final List<Box> boxes = new ArrayList<>();
        public UUID getUuid() { return new UUID(10, 1); }
        public List<Box> getBoxes() { return boxes; }
    }
    public static class Party {
        final List<Pokemon> slots = new ArrayList<>(java.util.Collections.nCopies(6, null));
        public List<Pokemon> getSlots() { return slots; }
    }
    public static class Box {
        final List<Pokemon> slots = new ArrayList<>(java.util.Collections.nCopies(30, null));
        public List<Pokemon> getSlots() { return slots; }
    }
    public static class UnreadBox extends Box {
        @Override public List<Pokemon> getSlots() { throw new AssertionError("Must not scan another box"); }
    }
    public record Pokemon(UUID uuid) { public UUID getUuid() { return uuid; } }
    public static class Client {
        public static Client INSTANCE;
        final Storage storage = new Storage();
        public Storage getStorage() { return storage; }
    }
    public static class Storage {
        Party party; Map<UUID, Pc> pcs = new HashMap<>();
        public Party getParty() { return party; }
        public Map<UUID, Pc> getPcStores() { return pcs; }
    }
    public static class Settings {
        public static Settings INSTANCE;
        boolean prevent;
        public boolean getPreventCompletePartyDeposit() { return prevent; }
    }
    public record PcPosition(int box, int slot) {}
    public record PartyPosition(int slot) {}
    static class Packets {
        static final List<Object> sent = new ArrayList<>();
        static Runnable constructed = () -> {};
    }
    public static class InPacket {
        final UUID uuid; final PartyPosition from; final PcPosition to;
        public InPacket(UUID uuid, PartyPosition from, PcPosition to) {
            this.uuid = uuid; this.from = from; this.to = to; Packets.constructed.run();
        }
        public void sendToServer() { Packets.sent.add(this); }
    }
    public static class OutPacket {
        final UUID uuid; final PcPosition from; final PartyPosition to;
        public OutPacket(UUID uuid, PcPosition from, PartyPosition to) {
            this.uuid = uuid; this.from = from; this.to = to; Packets.constructed.run();
        }
        public void sendToServer() { Packets.sent.add(this); }
    }
    private static Class<?> type(String name) throws ClassNotFoundException {
        return switch (name) {
            case "com.cobblemon.mod.common.client.gui.pc.PCGUI" -> Screen.class;
            case "com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration" -> Configuration.class;
            case "com.cobblemon.mod.common.client.CobblemonClient" -> Client.class;
            case "com.cobblemon.mod.common.client.settings.ServerSettings" -> Settings.class;
            case "com.cobblemon.mod.common.api.storage.pc.PCPosition" -> PcPosition.class;
            case "com.cobblemon.mod.common.api.storage.party.PartyPosition" -> PartyPosition.class;
            case "com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket" -> InPacket.class;
            case "com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonToPartyPacket" -> OutPacket.class;
            default -> throw new ClassNotFoundException(name);
        };
    }
}

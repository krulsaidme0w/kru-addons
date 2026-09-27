package dev.krul.cobblemon.pcui.daycare;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DaycareStorageReaderTest {
    // Boundary DTOs mirror the pinned public getter ABI; exercise the real reflective decoder.
    public record Species(String getResourceIdentifier) {}
    public record Mon(UUID getUuid, Species getSpecies, Set<String> getAspects, int getLevel) {}
    public record Party(List<Mon> getSlots) {}
    public record Box(List<Mon> getSlots) {}
    public record Pc(List<Box> getBoxes) {}
    public record Manager(Party getParty, Map<UUID, Pc> getPcStores) {}
    final UUID a = new UUID(0, 1), b = new UUID(0, 2);
    final Mon first = new Mon(a, new Species("cobblemon:ralts"), Set.of("shiny"), 12);
    final Mon second = new Mon(b, new Species("cobblemon:abra"), Set.of(), 7);

    @Test void physicalPartyHolesDoNotChangeFirstSourceAndPcDeltaIsLive() {
        var slots = new ArrayList<Mon>(Arrays.asList(null, first, null, second, null, null));
        var box = new ArrayList<Mon>(Collections.nCopies(30, null));
        var manager = new Manager(new Party(slots), Map.of(a, new Pc(List.of(new Box(box)))));
        var read = DaycareStorageReader.read(manager);
        assertNotNull(read);
        assertEquals(a, read.party().getFirst().uuid());
        assertEquals("ralts", read.party().getFirst().species());
        assertTrue(read.capacity());
        assertTrue(read.pc().isEmpty());
        slots.set(1, null); box.set(4, first);
        var next = DaycareStorageReader.read(manager);
        assertEquals(List.of(b), next.party().stream().map(DaycarePort.Pokemon::uuid).toList());
        assertEquals(List.of(a), next.pc().stream().map(DaycarePort.Pokemon::uuid).toList());
        assertEquals(2, read.party().size(), "old snapshots stay immutable");
    }

    @Test void unavailableDuplicateOrAmbiguousStorageIsNotAuthoritative() {
        assertNull(DaycareStorageReader.read(new Object()));
        var party = new Party(Arrays.asList(first, null, null, null, null, null));
        assertNull(DaycareStorageReader.read(new Manager(party, Map.of())));
        var slots = new ArrayList<Mon>(Collections.nCopies(30, null)); slots.set(0, first);
        var pc = new Pc(List.of(new Box(slots)));
        assertNull(DaycareStorageReader.read(new Manager(party, Map.of(a, pc))));
        assertNull(DaycareStorageReader.read(new Manager(party, Map.of(a, pc, b, pc))));
    }
}

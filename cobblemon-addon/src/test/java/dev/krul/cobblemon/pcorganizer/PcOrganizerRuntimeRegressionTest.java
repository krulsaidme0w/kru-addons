package dev.krul.cobblemon.pcorganizer;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerRuntimeRegressionTest {
    @Test void readerUsesFreshPcNamesPositionsAndTypesAfterReopening() {
        var reader = new PcOrganizerLiveReader();
        var first = reader.readPc(new FakePc("MAIN"), 0).orElseThrow();
        var second = reader.readPc(new FakePc("WATER"), 0).orElseThrow();
        assertEquals(List.of("MAIN"), first.boxNames());
        assertEquals(List.of("WATER"), second.boxNames());
        assertNotEquals(first.storeUuid(), second.storeUuid());
        assertEquals("water/dragon", second.pokemon().getFirst().type());
        assertEquals("cobblemon:kingdra", second.pokemon().getFirst().species());
        assertEquals(75, second.pokemon().getFirst().level());
    }
    public static class FakePc {
        private final UUID id = UUID.randomUUID();
        private final String name;
        FakePc(String name) { this.name = name; }
        public UUID getUuid() { return id; }
        public List<FakeBox> getBoxes() { return List.of(new FakeBox(name)); }
    }
    public record FakeBox(String name) {
        public Text getName() { return Text.literal(name); }
        public List<Object> getSlots() {
            var slots = new ArrayList<Object>(Collections.nCopies(30, null));
            slots.set(0, new FakePokemon()); return slots;
        }
    }
    public static class FakePokemon {
        private final UUID id = UUID.randomUUID();
        public UUID getUuid() { return id; }
        public FakeSpecies getSpecies() { return new FakeSpecies(); }
        public List<FakeType> getTypes() { return List.of(new FakeType("water"), new FakeType("dragon")); }
        public int getLevel() { return 75; }
        public boolean getShiny() { return false; }
    }
    public static class FakeSpecies { public String getResourceIdentifier() { return "cobblemon:kingdra"; } }
    public record FakeType(String name) { public String getName() { return name; } }
    @Test void textBoxNamesAreActualLabels() {
        assertEquals("MAIN", PcOrganizerLiveReader.text(Text.literal("MAIN"), "Box 1"));
        assertEquals("UTILITY", PcOrganizerLayoutPlanner.normalize("§aUtilities"));
    }
    @Test void namedRolesAndScopeProtectSourcesAndDestinations() {
        var snapshot = new PcOrganizerSnapshot(UUID.randomUUID(), 8,
            List.of("MAIN", "SPECIAL", "SHINIES", "UTILITIES", "TRADE", "BREEDING", "Fire", "Water"), List.of(), 6);
        assertEquals(Set.of(0, 1, 2, 3, 4, 5), PcOrganizerBoxPolicy.forSnapshot(snapshot, Set.of(), null).protectedIndexes());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 7), PcOrganizerBoxPolicy.forSnapshot(snapshot, Set.of(), 6).protectedIndexes());
    }
    @Test void pokemonGoToTheirNamedPrimaryTypeBoxes() {
        var water = pokemon(0, "water/dragon");
        var fire = pokemon(1, "fire");
        var snapshot = new PcOrganizerSnapshot(UUID.randomUUID(), 3, List.of("FIRE", "WATER", "MAIN"),
            List.of(water, fire, pokemon(2, "water")));
        var defaults = PcOrganizerSettings.defaults(3);
        var settings = new PcOrganizerSettings(PcOrganizerRunMode.PREVIEW, defaults.filter(), defaults.primary(), defaults.secondary(), defaults.tertiary(),
            PcOrganizerBoxPolicy.forSnapshot(snapshot, Set.of(), null));
        var plan = PcOrganizerPlanner.plan(snapshot, settings);
        assertEquals(2, plan.moves().size());
        assertEquals(1, plan.protectedCount());
        assertTrue(plan.moves().stream().allMatch(m -> m.toBox() == (m.pokemon().type().startsWith("water") ? 1 : 0)));
    }
    @Test void lostSwapPartnerCannotCountAsAck() {
        var a = pokemon(0, "fire");
        var b = new PcOrganizerPokemon(UUID.randomUUID(), 0, 1, "aaa", "fire", "", false, false, "", "", "", 1, 0, 0);
        UUID store = UUID.randomUUID();
        var initial = new PcOrganizerSnapshot(store, 1, List.of("Box"), List.of(a, b));
        var defaults = PcOrganizerSettings.defaults(1);
        var settings = new PcOrganizerSettings(PcOrganizerRunMode.APPLY, defaults.filter(), defaults.primary(), defaults.secondary(), defaults.tertiary(), defaults.boxPolicy());
        var coordinator = new PcOrganizerCoordinator(settings, move -> {});
        assertEquals(PcOrganizerCoordinator.Status.REQUESTED, coordinator.tick(initial, 0).status());
        var moved = new PcOrganizerPokemon(b.uuid(), 0, 0, b.species(), "fire", "", false, false, "", "", "", 1, 0, 0);
        assertEquals(PcOrganizerCoordinator.Status.BLOCKED,
            coordinator.tick(new PcOrganizerSnapshot(store, 1, List.of("Box"), List.of(moved)), 1).status());
    }
    private static PcOrganizerPokemon pokemon(int box, String type) {
        return new PcOrganizerPokemon(UUID.randomUUID(), box, 0, "zzz", type, "", false, false, "", "", "", 1, 0, 0);
    }
}

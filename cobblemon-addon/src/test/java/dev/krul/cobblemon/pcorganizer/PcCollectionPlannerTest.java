package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PcCollectionPlannerTest {
    private static final UUID STORE = new UUID(9, 9);
    private static final List<String> ANCHORS = List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING");

    @Test void expandsBothAutomaticZonesAndPreservesEveryUuid() {
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < 35; i++) values.add(p(i + 1, i / 30, i % 30, "fire", "ordinary", true, false));
        for (int i = 35; i < 70; i++) values.add(p(i + 1, i / 30, i % 30, "water", "legendary", false, false));
        var input = snapshot(defaultNames(10), values);
        var plan = PcCollectionPlanner.plan(input, Set.of());
        assertEquals(List.of(1, 6), group(plan, "SPECIAL").boxes());
        assertEquals(List.of(2, 7), group(plan, "SHINIES").boxes());
        assertEquals(35, group(plan, "SPECIAL").count());
        assertEquals(35, group(plan, "SHINIES").count());
        assertEquals(ANCHORS, plan.result().boxNames().subList(0, 6));
        assertEquals(List.of("SPECIAL 2/2", "SHINIES 2/2", "SPARE 1", "SPARE 2"), plan.result().boxNames().subList(6, 10));
        assertEquals(ids(input), ids(plan.result()));
        assertEquals(70, plan.result().pokemon().size());
        assertEquals(plan.result(), PcCollectionPlanner.plan(plan.result(), Set.of()).result());
    }

    @Test void shinyAndRadiantOverrideSpecialAndUnknownRarity() {
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(6), List.of(
            p(1, 0, 0, "fire", "legendary,mythical", true, false),
            p(2, 0, 1, "fire", "", false, true))), Set.of());
        assertEquals(2, group(plan, "SHINIES").count());
        assertEquals(0, group(plan, "SPECIAL").count());
        assertTrue(plan.result().pokemon().stream().allMatch(p -> p.box() == 2));
    }

    @Test void recognizesAllIntrinsicSpecialTokensButNotSubstrings() {
        var values = new ArrayList<PcOrganizerPokemon>();
        var rarities = List.of("legendary", "MYTHICAL", "ordinary,ultra_beast", "paradox", "restricted", "not_legendary");
        for (int i = 0; i < rarities.size(); i++) values.add(p(i + 1, 0, i, "fire", rarities.get(i), false, false));
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(7), values), Set.of());
        assertEquals(5, group(plan, "SPECIAL").count());
        assertEquals(1, group(plan, "FIRE").count());
    }

    @Test void namedManualZonesAndExplicitPinsPreserveExactSlotsAndMetadata() {
        var names = anchored(8); names.set(6, "My collection");
        var values = List.of(p(1, 0, 29, "fire", "legendary", true, false),
            p(2, 3, 17, "", "", false, false), p(3, 4, 8, "water", "mythical", false, false),
            p(4, 5, 19, "fire", "restricted", false, false), p(5, 6, 22, "", "", false, true),
            p(6, 7, 9, "fire", "ordinary", false, false));
        var plan = PcCollectionPlanner.plan(snapshot(names, values), Set.of(6));
        assertEquals(Set.of(0, 3, 4, 5, 6), plan.protectedBoxes());
        for (var original : values.subList(0, 5)) assertTrue(plan.result().pokemon().stream().anyMatch(p -> p.uuid().equals(original.uuid())));
        assertEquals(4, byId(plan, 3).box());
        assertEquals(0, byId(plan, 3).slot());
        assertEquals(5, byId(plan, 4).box());
        assertEquals(0, byId(plan, 4).slot());
        assertEquals("My collection", plan.result().boxNames().get(6));
        assertEquals(1, group(plan, "MAIN").count());
        assertEquals(0, group(plan, "SPECIAL").count());
        assertEquals(0, group(plan, "SHINIES").count());
        assertEquals(6, plan.groups().stream().mapToInt(PcCollectionPlanner.Group::count).sum());
        assertEquals(7, byId(plan, 6).box());
    }

    @Test void sortsTradeAndBreedingContentsInPlaceWithoutMovingPokemonAcrossZones() {
        var input = snapshot(anchored(6), List.of(
            named(1, 4, 0, "zubat"), named(2, 4, 1, "abra"),
            named(3, 5, 0, "weedle"), named(4, 5, 1, "caterpie")));

        var plan = PcCollectionPlanner.plan(input, Set.of());

        assertEquals("abra", at(plan, 4, 0).species());
        assertEquals("zubat", at(plan, 4, 1).species());
        assertEquals("caterpie", at(plan, 5, 0).species());
        assertEquals("weedle", at(plan, 5, 1).species());
        assertTrue(plan.result().pokemon().stream().allMatch(p -> p.box() == 4 || p.box() == 5));
    }

    @Test void explicitlyPinnedTradeZoneKeepsItsExactSlots() {
        var input = snapshot(anchored(6), List.of(
            named(1, 4, 0, "zubat"), named(2, 4, 1, "abra")));

        var plan = PcCollectionPlanner.plan(input, Set.of(4));

        assertEquals(input.pokemon(), plan.result().pokemon());
    }

    private static PcOrganizerPokemon at(PcCollectionPlanner.Plan plan, int box, int slot) {
        return plan.result().pokemon().stream().filter(p -> p.box() == box && p.slot() == slot).findFirst().orElseThrow();
    }

    @Test void defaultAnchorOccupantsAreCandidatesRatherThanFrozenByIndex() {
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int box : List.of(0, 3, 4, 5)) values.add(p(box + 1, box, 29, "water", "ordinary", false, false));
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(7), values), Set.of());
        assertTrue(plan.result().pokemon().stream().allMatch(p -> p.box() == 6));
        assertEquals(4, group(plan, "WATER").count());
    }

    @Test void explicitlyPinnedAnchorCannotBeRenamed() {
        var error = assertThrows(IllegalArgumentException.class, () -> PcCollectionPlanner.plan(
            snapshot(defaultNames(7), List.of()), Set.of(0)));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("pin"));
    }

    @Test void pinnedSpecialAnchorIsNotAvailableAsCandidateCapacity() {
        var original = p(1, 1, 28, "fire", "", false, false);
        var plan = PcCollectionPlanner.plan(snapshot(anchored(7), List.of(original,
            p(2, 6, 0, "fire", "legendary", false, false))), Set.of(1));
        assertTrue(plan.result().pokemon().contains(original));
        assertEquals(6, byId(plan, 2).box());
        assertEquals(2, group(plan, "SPECIAL").count());
        assertEquals("SPECIAL", plan.result().boxNames().get(1));
        assertEquals("SPECIAL 2/2", plan.result().boxNames().get(6));
    }

    @Test void rejectsTooFewAnchorsAndInsufficientGroupCapacity() {
        var small = assertThrows(IllegalArgumentException.class, () -> PcCollectionPlanner.plan(snapshot(defaultNames(5), List.of()), Set.of()));
        assertTrue(small.getMessage().contains("6"));
        var full = assertThrows(IllegalArgumentException.class, () -> PcCollectionPlanner.plan(snapshot(defaultNames(6),
            List.of(p(1, 0, 0, "fire", "ordinary", false, false))), Set.of()));
        assertTrue(full.getMessage().toLowerCase(Locale.ROOT).contains("capacity"));
    }

    @Test void unknownRarityBlocksOnlyUnprotectedNonshinyCandidates() {
        var error = assertThrows(IllegalArgumentException.class, () -> PcCollectionPlanner.plan(snapshot(defaultNames(7),
            List.of(p(1, 0, 0, "fire", "", false, false))), Set.of()));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("rarity"));
    }

    @Test void usesPrimaryTypeAndCanonicalOrderAndDoesNotGuessUnknownTypes() {
        var types = List.of(" Water / Fire", "FIRE/WATER", "mystery/fire", "", "/water", "normal");
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < types.size(); i++) values.add(p(i + 1, 0, i, types.get(i), "ordinary", false, false));
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(10), values), Set.of());
        assertEquals(List.of("NORMAL", "FIRE", "WATER", "UNCLASSIFIED"), plan.result().boxNames().subList(6, 10));
        assertEquals(8, byId(plan, 1).box());
        assertEquals(7, byId(plan, 2).box());
        assertEquals(3, group(plan, "UNCLASSIFIED").count());
    }

    @Test void reusesExistingPositionsBeforeAllocatingMissingGroupsAndReclaimsSurplus() {
        var names = anchored(10);
        names.set(6, "WATER 1/3"); names.set(7, "WATER 2/3"); names.set(8, "WATER 3/3"); names.set(9, "FIRE");
        var plan = PcCollectionPlanner.plan(snapshot(names, List.of(
            p(1, 6, 3, "water", "ordinary", false, false),
            p(2, 7, 4, "fire", "ordinary", false, false),
            p(3, 8, 5, "grass", "ordinary", false, false))), Set.of());
        assertEquals(List.of(6), group(plan, "WATER").boxes());
        assertEquals(List.of(9), group(plan, "FIRE").boxes());
        assertEquals(List.of(7), group(plan, "GRASS").boxes());
        assertEquals("SPARE 1", plan.result().boxNames().get(8));
        assertEquals(plan, PcCollectionPlanner.plan(plan.result(), Set.of()));
    }

    @Test void staleSurplusDoesNotStarveAnotherGroupsMinimum() {
        var names = anchored(8); names.set(6, "FIRE 1/2"); names.set(7, "FIRE 2/2");
        var plan = PcCollectionPlanner.plan(snapshot(names, List.of(p(1, 6, 1, "fire", "ordinary", false, false),
            p(2, 7, 1, "water", "ordinary", false, false))), Set.of());
        assertEquals(List.of("FIRE", "WATER"), plan.result().boxNames().subList(6, 8));
    }

    @Test void reusesShinyAndSpecialOverflowEvenWhenEarlierBoxesAreFree() {
        var names = anchored(10); names.set(8, "SHINIES 2/2"); names.set(9, "SPECIAL 2/2");
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < 62; i++) values.add(p(i + 1, 6 + i / 30, i % 30, "fire", "legendary", i < 31, false));
        var plan = PcCollectionPlanner.plan(snapshot(names, values), Set.of());
        assertEquals(List.of(1, 9), group(plan, "SPECIAL").boxes());
        assertEquals(List.of(2, 8), group(plan, "SHINIES").boxes());
        assertEquals(plan, PcCollectionPlanner.plan(plan.result(), Set.of()));
    }

    @Test void packsTypeOverflowWithBoundedNames() {
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < 31; i++) values.add(p(i + 1, i / 30, i % 30, "water", "ordinary", false, false));
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(8), values), Set.of());
        assertEquals(List.of("WATER 1/2", "WATER 2/2"), plan.result().boxNames().subList(6, 8));
        assertTrue(plan.result().boxNames().stream().allMatch(n -> n.length() <= 19));
    }

    @Test void sortsSpeciesThenFormThenDescendingIvThenUuidIndependentlyOfSourceOrder() {
        var values = List.of(detailed(5, "zubat", "", 186), detailed(4, "abra", "z", 186),
            detailed(3, "abra", "a", 100), detailed(2, "abra", "a", 186), detailed(1, "abra", "a", 186));
        var plan = PcCollectionPlanner.plan(snapshot(defaultNames(7), values), Set.of());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), plan.result().pokemon().stream()
            .sorted(Comparator.comparingInt(PcOrganizerPokemon::slot)).map(p -> p.uuid().getLeastSignificantBits()).toList());
        var reversed = new ArrayList<>(values); Collections.reverse(reversed);
        assertEquals(plan, PcCollectionPlanner.plan(snapshot(defaultNames(7), reversed), Set.of()));
        assertEquals(4, plan.result().currentBox());
        assertEquals(STORE, plan.result().storeUuid());
    }

    @Test void resultCollectionsAreDefensivelyCopiedAndImmutable() {
        var boxes = new ArrayList<>(List.of(0)); var pins = new HashSet<>(Set.of(0));
        var group = new PcCollectionPlanner.Group("MAIN", "MAIN", boxes, 0);
        var groups = new ArrayList<>(List.of(group));
        var plan = new PcCollectionPlanner.Plan(snapshot(anchored(6), List.of()), pins, groups);
        boxes.clear(); pins.clear(); groups.clear();
        assertEquals(List.of(0), group.boxes()); assertEquals(Set.of(0), plan.protectedBoxes()); assertEquals(1, plan.groups().size());
        assertThrows(UnsupportedOperationException.class, () -> group.boxes().add(2));
        assertThrows(UnsupportedOperationException.class, () -> plan.protectedBoxes().add(2));
        assertThrows(UnsupportedOperationException.class, () -> plan.groups().clear());
    }

    @Test void rejectsPinsOutsideSnapshot() {
        for (int pin : List.of(-1, 6)) assertThrows(IllegalArgumentException.class,
            () -> PcCollectionPlanner.plan(snapshot(anchored(6), List.of()), Set.of(pin)));
    }

    @Test void emptyPinnedTypeBoxNeverSuppliesCapacityOrChangesItsName() {
        var names = anchored(8); names.set(6, "Water 9/9");
        var input = snapshot(names, List.of(p(1, 7, 29, "water", "ordinary", false, false)));
        var plan = PcCollectionPlanner.plan(input, Set.of(6));
        assertEquals("Water 9/9", plan.result().boxNames().get(6));
        assertEquals(7, byId(plan, 1).box());
        assertEquals(0, byId(plan, 1).slot());
        assertEquals(List.of(6, 7), group(plan, "WATER").boxes());
        assertEquals(1, group(plan, "WATER").count());
        assertEquals(plan, PcCollectionPlanner.plan(plan.result(), Set.of(6)));
        assertEquals(names, input.boxNames());
        assertEquals(29, input.pokemon().getFirst().slot());
    }

    @Test void overflowCannotUseEmptyManualAnchorsOrPinnedBoxes() {
        var values = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < 31; i++) values.add(p(i + 1, 1 + i / 30, i % 30, "fire", "ordinary", true, false));
        var error = assertThrows(IllegalArgumentException.class,
            () -> PcCollectionPlanner.plan(snapshot(anchored(7), values), Set.of(6)));
        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("capacity"));
    }

    private static PcOrganizerPokemon detailed(int id, String species, String form, int iv) {
        return new PcOrganizerPokemon(new UUID(0, id), 0, id, species, "psychic", form, false, false, "ordinary", "", "", 1, iv, 0);
    }
    private static PcOrganizerPokemon p(int id, int box, int slot, String type, String rarity, boolean shiny, boolean radiant) {
        return new PcOrganizerPokemon(new UUID(0, id), box, slot, "species", type, "", shiny, radiant, rarity, "ability", "item", 50, 100, 0);
    }
    private static PcOrganizerPokemon named(int id, int box, int slot, String species) {
        return new PcOrganizerPokemon(new UUID(0, id), box, slot, species, "fire", "", false, false,
            "ordinary", "", "", 1, 100, 0);
    }
    private static PcOrganizerSnapshot snapshot(List<String> names, List<PcOrganizerPokemon> pokemon) {
        return new PcOrganizerSnapshot(STORE, names.size(), names, pokemon, 4);
    }
    private static ArrayList<String> defaultNames(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count).mapToObj(i -> "Box " + i).toList());
    }
    private static ArrayList<String> anchored(int count) {
        var names = defaultNames(count); for (int i = 0; i < 6; i++) names.set(i, ANCHORS.get(i)); return names;
    }
    private static PcCollectionPlanner.Group group(PcCollectionPlanner.Plan plan, String key) {
        return plan.groups().stream().filter(g -> g.key().equals(key)).findFirst().orElseThrow();
    }
    private static PcOrganizerPokemon byId(PcCollectionPlanner.Plan plan, int id) {
        return plan.result().pokemon().stream().filter(p -> p.uuid().equals(new UUID(0, id))).findFirst().orElseThrow();
    }
    private static Set<UUID> ids(PcOrganizerSnapshot snapshot) {
        var ids = new HashSet<UUID>(); snapshot.pokemon().forEach(p -> ids.add(p.uuid())); return ids;
    }
}

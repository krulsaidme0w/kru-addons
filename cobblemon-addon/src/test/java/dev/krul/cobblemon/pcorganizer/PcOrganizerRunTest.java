package dev.krul.cobblemon.pcorganizer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.pcorganizer.PcOrganizerPreparationTest.*;

class PcOrganizerRunTest {
    @Test void collectionSortsInsideTradeRoleBoxAndCompletesPreview() {
        var names = List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING");
        var before = snapshot(names, List.of(
            pokemon(4, 0, "zubat", "flying"), pokemon(4, 1, "abra", "psychic")));
        var preview = PcOrganizerPreparation.prepareCollection(before, Set.of());
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);

        PcOrganizerRun.Tick result = null;
        for (int i = 0; i < 100; i++) {
            result = run.tick(server.world, i);
            if (result.finished()) break;
        }

        assertNotNull(result);
        assertTrue(result.finished());
        assertFalse(result.blocked(), result.message());
        assertTrue(PcOrganizerPreparation.sameStorage(preview.result(), server.world));
        assertEquals("abra", server.world.pokemon().stream().filter(p -> p.box() == 4 && p.slot() == 0)
            .findFirst().orElseThrow().species());
    }

    @Test void collectionOverflowRunsToExactPreviewAndSecondRunDoesNothing() {
        var pokemon = new ArrayList<PcOrganizerPokemon>();
        for (int i = 0; i < 67; i++) pokemon.add(PcCollectionPreparationTest.pokemon(i / 30, i % 30, i < 33, i < 65 ? "legendary" : "ordinary"));
        var names = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "Box " + i).toList();
        var before = snapshot(names, pokemon);
        var preview = PcOrganizerPreparation.prepareCollection(before, Set.of());
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);
        PcOrganizerRun.Tick tick = null;
        for (int i = 0; i < 500; i++) {
            int requests = server.requests;
            tick = run.tick(server.world, i);
            assertTrue(server.requests - requests <= 1);
            if (tick.finished()) break;
        }
        assertNotNull(tick);
        assertTrue(tick.finished());
        assertFalse(tick.blocked(), tick.message());
        assertTrue(PcOrganizerPreparation.sameStorage(preview.result(), server.world));
        var again = PcOrganizerPreparation.prepareCollection(server.world, Set.of());
        assertTrue(again.layout().moves().isEmpty());
        assertTrue(again.sort().moves().isEmpty());
        assertTrue(again.renames().isEmpty());
    }
    @Test void renameRequestWaitsForActualNameAndNeverRepeats() {
        var before = snapshot(List.of("Box 1"), List.of());
        var target = new PcOrganizerSnapshot(before.storeUuid(), 1, List.of("WATER"), List.of());
        var preview = new PcOrganizerPreparation(before, before, target,
            new PcOrganizerBoxMovePlanner.Plan(List.of(0), List.of(), List.of()), settings(before),
            new PcOrganizerPlan(List.of(), 0, 0, Set.of()));
        int[] requests = {0};
        var port = new PcOrganizerLayoutCoordinator.MutationPort() {
            public void move(PcOrganizerPlan.Move move) { fail("unexpected move"); }
            public void rename(UUID id, int box, String name) { requests[0]++; assertEquals("WATER", name); }
        };
        var run = new PcOrganizerRun(preview, port::move, port);
        for (int i = 0; i < 10; i++) assertFalse(run.tick(before, i).finished());
        assertEquals(1, requests[0]);
        assertFalse(run.tick(target, 10).finished());
        assertTrue(run.tick(target, 11).finished());
        assertEquals(1, requests[0]);
    }
    @Test void generatedNamesAreAppliedOnlyAfterExactPositionCompletion() {
        var a = pokemon(0, 0, "water", "water");
        var before = snapshot(List.of("Box 1", "Box 2"), List.of(a));
        var result = new PcOrganizerSnapshot(before.storeUuid(), 2, List.of("MAIN", "WATER"),
            List.of(PcOrganizerPreparation.position(a, 1, 0)));
        var preview = new PcOrganizerPreparation(before, before, result,
            new PcOrganizerBoxMovePlanner.Plan(List.of(0, 1), List.of(), List.of()),
            settings(before), new PcOrganizerPlan(List.of(), 1, 0, Set.of()));
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);
        PcOrganizerRun.Tick tick = null;
        for (int i = 0; i < 30; i++) {
            int previous = server.requests;
            tick = run.tick(server.world, i);
            assertTrue(server.requests - previous <= 1);
            if (!server.world.boxNames().equals(before.boxNames()))
                assertEquals(1, server.world.pokemon().getFirst().box());
            if (tick.finished()) break;
        }
        assertNotNull(tick);
        assertTrue(tick.finished());
        assertFalse(tick.blocked(), tick.message());
        assertEquals(result.boxNames(), server.world.boxNames());
        assertEquals(3, server.requests);
    }
    @Test void externalMoveBetweenLayoutAndSortIsNotUndone() {
        var water = pokemon(0, 0, "water", "water");
        var before = snapshot(List.of("Box 1"), List.of(water, pokemon(0, 2, "fire", "fire")));
        var settings = settings(before);
        settings = new PcOrganizerSettings(settings.runMode(),
            new PcOrganizerFilter("fire", "", "", false, false, "", "", "", ""),
            settings.primary(), settings.secondary(), settings.tertiary(), settings.boxPolicy());
        var preview = PcOrganizerPreparation.prepare(before, settings, true, Set.of());
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);
        assertFalse(run.tick(server.world, 0).finished());
        server.world = new PcOrganizerSnapshot(before.storeUuid(), 1, before.boxNames(),
            List.of(PcOrganizerPreparation.position(water, 0, 1), before.pokemon().get(1)));
        assertTrue(run.tick(server.world, 1).blocked());
        assertEquals(0, server.requests);
    }
    @Test void duplicateSpeciesKeepTheExactPreviewOrderAfterSwaps() {
        var before = snapshot(List.of("Box 1"), List.of(pokemon(0, 0, "water", "water"),
            pokemon(0, 1, "water", "water"), pokemon(0, 2, "fire", "fire")));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);
        PcOrganizerRun.Tick result = null;
        for (int i = 0; i < 40; i++) { result = run.tick(server.world, i); if (result.finished()) break; }
        assertNotNull(result);
        assertTrue(result.finished());
        assertFalse(result.blocked(), result.message());
        assertEquals(new HashSet<>(preview.result().pokemon()), new HashSet<>(server.world.pokemon()));
    }
    @Test void oneApplyExecutesLayoutThenSortAndMatchesPreview() {
        var before = snapshot(List.of("Box 1", "MAIN", "Box 3"),
            List.of(pokemon(0, 0, "a", "water"), pokemon(1, 7, "main", "fire"), pokemon(2, 0, "z", "fire")));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        var server = new Server(before);
        var run = new PcOrganizerRun(preview, server, server);
        PcOrganizerRun.Tick result = null;
        for (int i = 0; i < 100; i++) {
            int requests = server.requests;
            result = run.tick(server.world, i);
            assertTrue(server.requests - requests <= 1);
            if (result.finished()) break;
        }
        assertNotNull(result);
        assertTrue(result.finished());
        assertFalse(result.blocked(), result.message());
        assertEquals(preview.result().boxNames(), server.world.boxNames());
        assertEquals(new HashSet<>(preview.result().pokemon()), new HashSet<>(server.world.pokemon()));
        assertTrue(server.requests > 0);
    }

    @Test void changedPcAfterPreviewSendsNothing() {
        var before = snapshot(List.of("Box 1", "MAIN"), List.of(pokemon(1, 0, "main", "fire")));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        var server = new Server(snapshot(before.boxNames(), before.pokemon()));
        var result = new PcOrganizerRun(preview, server, server).tick(server.world, 0);
        assertTrue(result.blocked());
        assertEquals(0, server.requests);
    }

    private static final class Server implements PcOrganizerCoordinator.MutationPort, PcOrganizerLayoutCoordinator.MutationPort {
        PcOrganizerSnapshot world;
        int requests;
        Server(PcOrganizerSnapshot world) { this.world = world; }
        public void move(PcOrganizerPlan.Move move) {
            requests++;
            var source = world.pokemon().stream().filter(p -> p.box() == move.fromBox() && p.slot() == move.fromSlot()).findFirst().orElseThrow();
            assertEquals(move.pokemon().uuid(), source.uuid());
            var pokemon = world.pokemon().stream().map(p -> {
                if (p.uuid().equals(source.uuid())) return PcOrganizerPreparation.position(p, move.toBox(), move.toSlot());
                if (p.box() == move.toBox() && p.slot() == move.toSlot()) return PcOrganizerPreparation.position(p, move.fromBox(), move.fromSlot());
                return p;
            }).toList();
            world = new PcOrganizerSnapshot(world.storeUuid(), world.boxCount(), world.boxNames(), pokemon);
        }
        public void rename(UUID store, int box, String name) {
            requests++;
            assertEquals(world.storeUuid(), store);
            var names = new ArrayList<>(world.boxNames()); names.set(box, name);
            world = new PcOrganizerSnapshot(store, world.boxCount(), names, world.pokemon());
        }
    }
}

package dev.krul.builder.project;

import dev.krul.builder.context.ScanSession;
import dev.krul.builder.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.krul.builder.project.PlanValidatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectSessionTest {
    private final UUID world = UUID.randomUUID();
    private final Bounds bounds = new Bounds(new Cell(100, 64, -20), new Cell(101, 64, -20));
    private WorldSnapshot snapshot(long revision) {
        return new WorldSnapshot(world, revision, bounds, Map.of(bounds.min(), AIR, bounds.max(), AIR), Set.of());
    }
    private BuildPlan plan(long revision, long context) {
        return new BuildPlan(PROJECT, revision, context,
            List.of(stage("base", Set.of(), Map.of(new Cell(0, 0, 0), STONE))));
    }
    private ProjectSession session() { return new ProjectSession(PROJECT, snapshot(7), s -> true); }

    @Test void proposalOnlyChangesDraftAndAcceptanceRequiresNewScan() {
        var session = session(); var plan = plan(1, 7);
        assertTrue(session.accepted().isEmpty());
        assertTrue(session.propose(plan).isEmpty());
        assertTrue(session.accepted().isEmpty());
        assertEquals(plan, session.draft().orElseThrow());
        assertFalse(session.accept(1, snapshot(7)).isEmpty());
        assertFalse(session.accept(2, snapshot(8)).isEmpty());
        assertTrue(session.accept(1, snapshot(8)).isEmpty());
        assertEquals(plan, session.accepted().orElseThrow());
        assertTrue(session.draft().isEmpty());
    }

    @Test void actualScanSessionRescanCanAcceptUnchangedContent() {
        var scan = new ScanSession(); scan.bind(new Object(), new Object(), new Object());
        scan.setFirst(bounds.min()); scan.setSecond(bounds.max());
        scan.requestScan(c -> Optional.of(AIR)); scan.tick();
        var baseline = scan.snapshot().orElseThrow();
        var session = new ProjectSession(PROJECT, baseline, s -> true);
        session.propose(plan(1, baseline.revision()));
        scan.requestScan(c -> Optional.of(AIR)); scan.tick();
        assertTrue(session.accept(1, scan.snapshot().orElseThrow()).isEmpty());
        assertEquals(plan(1, baseline.revision()), session.accepted().orElseThrow());
    }

    @Test void changesAnywhereInBaselineRequireRenewedReviewWithoutRebase() {
        var session = session(); var plan = plan(1, 7); session.propose(plan);
        var changed = new WorldSnapshot(world, 8, bounds,
            Map.of(bounds.min(), AIR, bounds.max(), STONE), Set.of());
        assertTrue(session.accept(1, changed).stream().anyMatch(e -> e.contains("REVIEW_REQUIRED")));
        assertEquals(plan, session.draft().orElseThrow());
        assertTrue(session.accepted().isEmpty());
        // A retry cannot silently adopt the changed baseline.
        assertFalse(session.accept(1, new WorldSnapshot(world, 9, bounds, changed.known(), Set.of())).isEmpty());
        assertTrue(session.beginRequest(changed).isEmpty());
        var reviewed = plan(2, 8); assertTrue(session.propose(reviewed).isEmpty());
        assertTrue(session.accept(2, new WorldSnapshot(world, 9, bounds, changed.known(), Set.of())).isEmpty());
    }

    @Test void staleWrongProjectAndCancelledResponsesCannotReplaceAcceptedPlan() {
        var session = session(); var accepted = plan(1, 7);
        session.propose(accepted); session.accept(1, snapshot(8));
        assertTrue(session.beginRequest(snapshot(9)).isEmpty());
        assertFalse(session.propose(plan(2, 7)).isEmpty());
        assertFalse(session.propose(new BuildPlan(UUID.randomUUID(), 2, 9, accepted.stages())).isEmpty());
        assertFalse(session.propose(plan(1, 9)).isEmpty());
        var draft = plan(2, 9); assertTrue(session.propose(draft).isEmpty());
        assertEquals(accepted, session.accepted().orElseThrow());
        var newer = plan(3, 9); session.propose(newer);
        assertEquals(newer, session.draft().orElseThrow());
        assertFalse(session.propose(draft).isEmpty());
        var invalid = new BuildPlan(PROJECT, 4, 9, List.of(stage("bad", Set.of(), Map.of(new Cell(99, 0, 0), STONE))));
        assertFalse(session.propose(invalid).isEmpty());
        assertEquals(newer, session.draft().orElseThrow());
        session.cancelRequest();
        assertTrue(session.draft().isEmpty());
        assertFalse(session.propose(plan(4, 9)).isEmpty());
        assertFalse(session.accept(3, snapshot(10)).isEmpty());
        assertEquals(accepted, session.accepted().orElseThrow());
        assertFalse(session.beginRequest(snapshot(9)).isEmpty());
        assertTrue(session.beginRequest(snapshot(10)).isEmpty());
        assertFalse(session.propose(plan(4, 9)).isEmpty());
    }

    @Test void sessionBoundsIncompleteAndUnknownEvidenceFailClosed() {
        var session = session(); session.propose(plan(1, 7));
        for (var bad : List.of(
            new WorldSnapshot(UUID.randomUUID(), 8, bounds, snapshot(8).known(), Set.of()),
            new WorldSnapshot(world, 8, bounds, Map.of(bounds.min(), AIR), Set.of()),
            new WorldSnapshot(world, 8, bounds, Map.of(bounds.min(), AIR), Set.of(bounds.max())),
            new WorldSnapshot(world, 8, new Bounds(bounds.min(), bounds.min()), Map.of(bounds.min(), AIR), Set.of()))) {
            assertFalse(session.accept(1, bad).isEmpty());
            assertFalse(session.beginRequest(bad).isEmpty());
            assertTrue(session.accepted().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> new ProjectSession(PROJECT,
            new WorldSnapshot(world, 7, bounds, Map.of(), Set.of()), s -> true));
    }

    @Test void progressUsesFullStatesAbsoluteSnapshotAndDeduplicatesTargets() {
        var stairs = new StateKey("minecraft:oak_stairs", Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "false"));
        var wet = new StateKey(stairs.id(), Map.of("facing", "north", "half", "bottom", "shape", "straight", "waterlogged", "true"));
        var targets = Map.of(new Cell(0, 0, 0), STONE, new Cell(1, 0, 0), STONE,
            new Cell(2, 0, 0), stairs, new Cell(3, 0, 0), AIR, new Cell(0, 1, 0), STONE);
        var plan = PlanValidatorTest.plan(stage("a", Set.of(), targets), stage("b", Set.of("a"), Map.of(new Cell(0, 0, 0), STONE)));
        var actual = Map.of(new Cell(100, 64, -20), STONE, new Cell(101, 64, -20), AIR,
            new Cell(102, 64, -20), wet, new Cell(103, 64, -20), STONE);
        var progress = Progress.measure(plan, new WorldSnapshot(world, 8, BOUNDS, actual, Set.of(new Cell(100, 65, -20))));
        assertEquals(new Progress(1, 1, 2, 1), progress);
        assertFalse(progress.complete());
        assertFalse(new Progress(4, 0, 0, 1).complete());
        assertEquals(progress, Progress.measure(plan, new WorldSnapshot(world, 9, BOUNDS, actual, Set.of())));
        assertTrue(new Progress(5, 0, 0, 0).complete());
    }

    @Test void sessionProgressRejectsOtherWorldAndSelection() {
        var session = session(); session.propose(plan(1, 7)); session.accept(1, snapshot(8));
        var other = new WorldSnapshot(UUID.randomUUID(), 9, bounds,
            Map.of(bounds.min(), STONE, bounds.max(), AIR), Set.of());
        assertEquals(new Progress(0, 0, 0, 1), session.progress(other));
        assertEquals(new Progress(0, 1, 0, 0), session.progress(snapshot(9)));
    }
}

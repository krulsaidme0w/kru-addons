package dev.krul.builder.agent;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.BuildStage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanReviewTrackerTest {
    private static final UUID PROJECT = UUID.randomUUID();
    @Test void finalPlanIsMarkedReviewedOnlyWhenItMatchesTheInspectedRevision() {
        var tracker = new PlanReviewTracker(); tracker.reset();
        BuildPlan draft = plan(1);
        assertTrue(tracker.beginCall()); assertTrue(tracker.canInspect(draft)); tracker.complete(draft);
        assertTrue(tracker.review(draft).succeeded());
        assertFalse(tracker.review(plan(2)).succeeded());
        assertTrue(tracker.review(plan(2)).failure().contains("differs"));
    }

    @Test void permitsOnlyOneHigherRevisionAfterTheDraftAndRejectsAThirdInspection() {
        var tracker = new PlanReviewTracker(); tracker.reset();
        BuildPlan draft = plan(1), revised = plan(2);
        assertTrue(tracker.beginCall()); assertTrue(tracker.canInspect(draft)); tracker.complete(draft);
        assertTrue(tracker.beginCall()); assertTrue(tracker.canInspect(revised)); tracker.complete(revised);
        assertTrue(tracker.review(revised).succeeded());
        assertFalse(tracker.beginCall());
        assertFalse(tracker.review(revised).succeeded());
        assertTrue(tracker.review(revised).failure().contains("limit"));
    }

    @Test void failedPreviewCannotBeReportedAsSuccessfulAndInvalidFirstDraftCanBeFixedOnce() {
        var tracker = new PlanReviewTracker(); tracker.reset();
        assertTrue(tracker.beginCall()); tracker.fail("unsupported block appearance");
        assertFalse(tracker.review(plan(1)).succeeded());
        assertTrue(tracker.beginCall());
        BuildPlan fixed = plan(2); assertTrue(tracker.canInspect(fixed)); tracker.complete(fixed);
        assertTrue(tracker.review(fixed).succeeded());
    }

    private static BuildPlan plan(long revision) {
        return new BuildPlan(PROJECT, revision, 4, List.of(new BuildStage("base", "Base", Set.of(),
            Map.of(new Cell(0, 0, 0), new StateKey("minecraft:stone", Map.of())))));
    }
}

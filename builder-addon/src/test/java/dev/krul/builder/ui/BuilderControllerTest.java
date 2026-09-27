package dev.krul.builder.ui;

import dev.krul.builder.agent.*;
import dev.krul.builder.context.ScanSession;
import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import dev.krul.builder.visual.AttachmentPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class BuilderControllerTest {
    private static final UUID PROJECT = UUID.fromString("17b45314-4130-4293-98b5-98326d525e13");
    private static final StateKey AIR = new StateKey("minecraft:air", Map.of());
    private static final StateKey STONE = new StateKey("minecraft:stone", Map.of());
    @TempDir Path temp;

    @Test void sendPreparesFreshContextButRequiresExplicitConsent() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Build a path");
        assertEquals(BuilderUiState.Phase.PREPARING, fixture.controller.state().phase());
        assertEquals(0, fixture.agent.starts);
        fixture.capture.complete(fixture.capturePath());
        assertTrue(fixture.controller.state().prepared().isPresent());
        assertEquals(0, fixture.agent.starts);
        fixture.controller.confirmSend();
        assertEquals(1, fixture.agent.starts);
        assertEquals("Build a path", fixture.agent.request.text());
        fixture.close();
    }

    @Test void confirmationUsesFreshSnapshotAndOnlyCurrentlyAttachedImages() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Describe this area");
        fixture.capture.complete(fixture.capturePath());
        var attachment = fixture.controller.state().prepared().orElseThrow().attachments().getFirst();
        fixture.controller.removeAttachment(attachment.id());
        fixture.controller.confirmSend();
        assertTrue(fixture.agent.request.images().isEmpty());
        assertEquals(12, fixture.agent.request.context().revision());
        fixture.close();
    }

    @Test void confirmationUsesTheNewRevisionWhenFreshWorldContentIsUnchanged() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.scans.add(fixture.snapshot(12));
        fixture.scans.add(fixture.snapshot(13));
        fixture.controller.send("Describe this area");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        assertEquals(13, fixture.agent.request.contextRevision());
        assertEquals(13, fixture.agent.request.context().revision());
        fixture.close();
    }

    @Test void confirmationRequiresAnewReviewWhenWorldContentChanged() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.scans.add(fixture.snapshot(12));
        fixture.scans.add(new WorldSnapshot(fixture.world, 13, fixture.bounds,
            Map.of(fixture.bounds.min(), STONE), Set.of()));
        fixture.controller.send("Describe this area");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        assertEquals(0, fixture.agent.starts);
        assertTrue(fixture.controller.state().prepared().isEmpty());
        assertTrue(fixture.controller.state().status().contains("context changed"));
        fixture.close();
    }

    @Test void realScanSessionResetCanPrepareAndConfirmUsingItsActualSessionIdentity() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        var scans = new ScanSession();
        Object world = new Object(), connection = new Object(), player = new Object();
        scans.bind(world, connection, player);
        scans.setFirst(new Cell(4, 70, 9)); scans.setSecond(new Cell(4, 70, 9));
        scans.requestScan(ignored -> Optional.of(AIR)); scans.tick();
        fixture.controller.observeSnapshot(scans.snapshot().orElseThrow());

        fixture.controller.worldChanged();
        scans.reset(); scans.bind(world, connection, player);
        scans.setFirst(new Cell(6, 70, 9)); scans.setSecond(new Cell(6, 70, 9));
        scans.requestScan(ignored -> Optional.of(AIR)); scans.tick();
        WorldSnapshot selected = scans.snapshot().orElseThrow();
        fixture.session = new ProjectSession(PROJECT, selected, ignored -> true);
        fixture.snapshotSource = () -> {
            scans.requestScan(ignored -> Optional.of(AIR)); scans.tick();
            return CompletableFuture.completedFuture(scans.snapshot().orElseThrow());
        };

        fixture.controller.send("Build on this selection");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();

        assertEquals(1, fixture.agent.starts);
        assertEquals(selected.session(), fixture.agent.request.session());
        fixture.close();
    }

    @Test void oldTurnEventsCannotChangeControllerStateAfterPlanAcceptance() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.scans.add(fixture.snapshot(12));
        fixture.scans.add(fixture.snapshot(13));
        fixture.scans.add(fixture.snapshot(14));
        fixture.controller.send("Build this"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal(1, 13)));
        fixture.controller.accept(1);
        BuilderUiState accepted = fixture.controller.state();
        assertEquals(fixture.proposal(1, 13), accepted.accepted().orElseThrow());
        assertTrue(accepted.draft().isEmpty());

        fixture.agent.deliver(new AgentEvent.Status(AgentClient.Connection.READY, AgentClient.Login.AUTHENTICATED,
            "late status", null, null));
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal(2, 13)));
        fixture.agent.deliver(new AgentEvent.Completed("late completion"));

        BuilderUiState afterLateEvents = fixture.controller.state();
        assertEquals(accepted.phase(), afterLateEvents.phase());
        assertEquals(accepted.status(), afterLateEvents.status());
        assertEquals(accepted.accepted(), afterLateEvents.accepted());
        assertEquals(accepted.draft(), afterLateEvents.draft());
        fixture.close();
    }

    @Test void doubleSendDoesNotStartOverlappingProviderTurns() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("one");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.controller.send("two");
        assertEquals(1, fixture.agent.starts);
        fixture.close();
    }

    @Test void cancelDiscardsLateProposal() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("one"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        UUID oldSession = fixture.agent.request.session();
        fixture.controller.cancel();
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal(2, 12)));
        assertTrue(fixture.controller.state().draft().isEmpty());
        assertEquals(oldSession, fixture.agent.request.session());
        fixture.close();
    }

    @Test void worldChangeClearsLiveSnapshotAndDraftAndRejectsOldProposal() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("one"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.controller.worldChanged();
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal()));
        assertTrue(fixture.controller.state().draft().isEmpty());
        assertTrue(fixture.controller.state().snapshot().isEmpty());
    }

    @Test void providerFailureKeepsPreviouslyAcceptedPlan() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.session.propose(fixture.proposal());
        fixture.session.accept(1, fixture.snapshot(11));
        fixture.controller.send("improve it"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Failed("provider unavailable"));
        assertEquals(BuilderUiState.Phase.FAILED, fixture.controller.state().phase());
        assertEquals(fixture.accepted, fixture.controller.state().accepted().orElseThrow());
        fixture.close();
    }

    @Test void loginIsBlockedUntilSessionConsentIsGranted() {
        var fixture = new Fixture();

        fixture.controller.login();

        assertEquals(0, fixture.agent.logins);
        assertFalse(fixture.controller.state().cloudConsent());
        fixture.controller.setCloudConsent(true);
        fixture.controller.login();
        assertEquals(1, fixture.agent.logins);
        fixture.close();
    }

    @Test void requestPreparationIsBlockedUntilSessionConsentIsGranted() {
        var fixture = new Fixture();

        fixture.controller.send("Build a path");

        assertTrue(fixture.controller.state().status().contains("consent"));
        assertTrue(fixture.controller.state().prepared().isEmpty());
        assertEquals(0, fixture.agent.logins);
        assertEquals(0, fixture.agent.starts);
        fixture.close();
    }

    @Test void revokingConsentWhileFreshConfirmationIsPendingPreventsTransmission() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        CompletableFuture<WorldSnapshot> freshConfirmation = new CompletableFuture<>();
        fixture.controller.send("Build a path");
        fixture.capture.complete(fixture.capturePath());
        fixture.snapshotSource = () -> freshConfirmation;

        fixture.controller.confirmSend();
        fixture.controller.setCloudConsent(false);
        freshConfirmation.complete(fixture.latest);

        assertEquals(0, fixture.agent.starts);
        assertFalse(fixture.controller.state().cloudConsent());
        fixture.close();
    }

    @Test void confirmedRequestIncludesCopiedReferenceAndPreservesOriginal() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        Path original = fixture.capturePath();
        fixture.controller.addReference(original);
        Path imported = fixture.controller.state().attachments().getFirst().path();

        fixture.controller.send("Use this reference");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();

        assertTrue(fixture.agent.request.images().contains(imported));
        assertNotEquals(original, imported);
        assertTrue(java.nio.file.Files.exists(original));
        fixture.close();
    }

    @Test void removedReferenceIsAbsentFromConfirmedRequest() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.addReference(fixture.capturePath());
        UUID reference = fixture.controller.state().attachments().getFirst().id();
        fixture.controller.removeAttachment(reference);

        fixture.controller.send("Do not use removed image");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();

        assertEquals(1, fixture.agent.request.images().size());
        fixture.close();
    }

    @Test void invalidReferenceIsRejectedWithoutProviderRequest() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        Path invalid = java.nio.file.Files.writeString(temp.resolve("not-a-png.txt"), "not an image");

        fixture.controller.addReference(invalid);

        assertTrue(fixture.controller.state().attachments().isEmpty());
        assertTrue(fixture.controller.state().status().contains("rejected"));
        assertEquals(0, fixture.agent.starts);
        assertTrue(java.nio.file.Files.exists(invalid));
        fixture.close();
    }

    @Test void nextPreparationReplacesOnlyAutomaticScreenshotAndKeepsReferences() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.addReference(fixture.capturePath());
        UUID referenceId = fixture.controller.state().attachments().getFirst().id();

        fixture.controller.send("First review");
        fixture.capture.complete(fixture.capturePath());
        UUID firstScreenshot = fixture.controller.state().prepared().orElseThrow().attachments().stream()
            .map(AttachmentPolicy.Attachment::id).filter(id -> !id.equals(referenceId)).findFirst().orElseThrow();
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Completed("done"));

        fixture.scans.add(fixture.snapshot(13));
        fixture.capture = new CompletableFuture<>();
        fixture.controller.send("Second review");
        fixture.capture.complete(fixture.capturePath());

        BuilderUiState secondPrepared = fixture.controller.state();
        assertTrue(secondPrepared.prepared().isPresent(), secondPrepared.status());
        List<UUID> ids = secondPrepared.prepared().orElseThrow().attachments().stream()
            .map(AttachmentPolicy.Attachment::id).toList();
        assertTrue(ids.contains(referenceId));
        assertFalse(ids.contains(firstScreenshot));
        assertEquals(2, ids.size());
        fixture.close();
    }

    private final class Fixture {
        final UUID world = UUID.randomUUID();
        final Bounds bounds = new Bounds(new Cell(0, 0, 0), new Cell(0, 0, 0));
        final WorldSnapshot latest = snapshot(12);
        ProjectSession session = new ProjectSession(PROJECT, snapshot(10), ignored -> true);
        final Deque<WorldSnapshot> scans = new ArrayDeque<>();
        Supplier<CompletableFuture<WorldSnapshot>> snapshotSource = this::fresh;
        final BuildPlan accepted = proposal();
        final FakeAgent agent = new FakeAgent();
        CompletableFuture<Path> capture = new CompletableFuture<>();
        final AttachmentPolicy attachments;
        final BuilderController controller;
        Fixture() {
            try { attachments = new AttachmentPolicy(temp.resolve(UUID.randomUUID().toString())); }
            catch (Exception e) { throw new RuntimeException(e); }
            controller = new BuilderController(PROJECT, "codex", () -> snapshotSource.get(), () -> session, agent,
                () -> capture, attachments, new ProjectStore(temp.resolve("projects")), Runnable::run,
                Runnable::run, ignored -> {});
        }
        WorldSnapshot snapshot(long revision) { return new WorldSnapshot(world, revision, bounds, Map.of(bounds.min(), AIR), Set.of()); }
        CompletableFuture<WorldSnapshot> fresh() { return CompletableFuture.completedFuture(scans.isEmpty() ? latest : scans.removeFirst()); }
        BuildPlan proposal() { return proposal(1, 10); }
        BuildPlan proposal(long revision, long context) { return new BuildPlan(PROJECT, revision, context,
            List.of(new BuildStage("base", "Base", Set.of(), Map.of(new Cell(0, 0, 0), STONE)))); }
        Path capturePath() {
            try {
                Path path = temp.resolve(UUID.randomUUID() + ".png");
                ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", path.toFile());
                return path;
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        void close() { controller.close(); attachments.close(); }
    }

    private static final class FakeAgent implements AgentClient {
        int starts, logins; AgentRequest request; Consumer<AgentEvent> events;
        public void start(AgentRequest request, Consumer<AgentEvent> events) { starts++; this.request = request; this.events = events; }
        public void cancel() {}
        public Status status() { return new Status(Connection.READY, Login.AUTHENTICATED, "Ready"); }
        public void login(Consumer<AgentEvent> events) { logins++; }
        public void close() {}
        void deliver(AgentEvent event) { events.accept(event); }
    }
}

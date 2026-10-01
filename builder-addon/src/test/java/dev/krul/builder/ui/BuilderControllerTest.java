package dev.krul.builder.ui;

import dev.krul.builder.agent.*;
import dev.krul.builder.archive.ConversationArchiveStore;
import dev.krul.builder.archive.ConversationPlan;
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

    @Test void submitWithoutExplicitScreenshotDoesNotCaptureOrAttachAnImage() {
        var fixture = new Fixture();
        fixture.controller.submit("Build a path");
        assertEquals(0, fixture.captureCalls);
        assertEquals(1, fixture.agent.starts);
        assertTrue(fixture.agent.request.images().isEmpty());
        fixture.close();
    }

    @Test void prepareAndConfirmWithoutScreenshotSendsNoImage() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Text-only request");
        assertEquals(0, fixture.captureCalls);
        assertTrue(fixture.controller.state().prepared().orElseThrow().attachments().isEmpty());
        fixture.controller.confirmSend();
        assertTrue(fixture.agent.request.images().isEmpty());
        fixture.close();
    }

    @Test void explicitScreenshotIsAttachedAndSentWhenSubmitted() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.captureScreenshot();
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.submit("Build a path");
        assertEquals(1, fixture.agent.starts);
        assertEquals("Build a path", fixture.agent.request.text());
        assertEquals(1, fixture.agent.request.images().size());
        fixture.close();
    }

    @Test void connectDoesNotSendBuildRequest() throws Exception {
        var fixture = new Fixture();
        fixture.controller.connect();
        assertEquals(1, fixture.agent.logins);
        assertEquals(0, fixture.agent.starts);
        assertTrue(fixture.controller.state().prepared().isEmpty());
        fixture.close();
    }

    @Test void singleSubmitDoesNotSendChangedWorldContent() throws Exception {
        var fixture = new Fixture();
        fixture.scans.add(fixture.snapshot(12));
        fixture.scans.add(new WorldSnapshot(fixture.world, 13, fixture.bounds,
            Map.of(fixture.bounds.min(), STONE), Set.of()));
        fixture.controller.submit("Build a path");
        assertEquals(0, fixture.agent.starts);
        assertEquals(BuilderUiState.Phase.FAILED, fixture.controller.state().phase());
        fixture.close();
    }

    @Test void cancelledSingleSubmitCannotSendAfterCaptureCompletes() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.captureScreenshot();
        fixture.controller.cancel();
        fixture.capture.complete(fixture.capturePath());
        assertEquals(0, fixture.agent.starts);
        fixture.close();
    }

    @Test void singleSubmitCreatesSessionThroughFreshScan() throws Exception {
        var fixture = new Fixture();
        fixture.session = null;
        fixture.snapshotSource = () -> {
            if (fixture.session == null) fixture.session = new ProjectSession(PROJECT, fixture.latest, ignored -> true);
            return CompletableFuture.completedFuture(fixture.latest);
        };
        fixture.controller.submit("Build a path");
        assertEquals(1, fixture.agent.starts);
        fixture.close();
    }

    @Test void sendPreparesFreshContextButRequiresExplicitConsent() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Build a path");
        assertEquals(BuilderUiState.Phase.READY, fixture.controller.state().phase());
        assertEquals(0, fixture.agent.starts);
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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
        fixture.controller.send("Build this"); fixture.takeScreenshot();
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
        fixture.takeScreenshot();
        fixture.controller.confirmSend();
        fixture.controller.send("two");
        assertEquals(1, fixture.agent.starts);
        fixture.close();
    }

    @Test void cancelDiscardsLateProposal() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("one"); fixture.takeScreenshot();
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
        fixture.controller.send("one"); fixture.takeScreenshot();
        fixture.controller.confirmSend();
        fixture.controller.worldChanged();
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal()));
        assertTrue(fixture.controller.state().draft().isEmpty());
        assertTrue(fixture.controller.state().snapshot().isEmpty());
        assertTrue(fixture.controller.state().conversation().stream().anyMatch(message -> message.text().equals("one")));
    }

    @Test void providerFailureKeepsPreviouslyAcceptedPlan() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.session.propose(fixture.proposal());
        fixture.session.accept(1, fixture.snapshot(11));
        fixture.controller.send("improve it"); fixture.takeScreenshot();
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Failed("provider unavailable"));
        assertEquals(BuilderUiState.Phase.FAILED, fixture.controller.state().phase());
        assertEquals("provider unavailable", fixture.controller.state().status());
        assertEquals("codex", fixture.controller.state().conversation().getLast().role());
        assertTrue(fixture.controller.state().conversation().getLast().text().contains("provider unavailable"), fixture.controller.state().conversation().getLast().text());
        assertEquals(dev.krul.builder.archive.BuilderDiagnostic.Category.MODEL_TOOL,
            fixture.controller.state().conversation().getLast().diagnostic().orElseThrow().category());
        assertEquals(fixture.accepted, fixture.controller.state().accepted().orElseThrow());
        fixture.close();
    }

    @Test void userRequestIsArchivedBeforeProviderDispatchAndSurvivesControllerReopen() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.agent.beforeStart = () -> {
            try {
                var archive = fixture.archiveStore.load(PROJECT).orElseThrow();
                return archive.entries().stream().anyMatch(entry -> entry.kind() == dev.krul.builder.archive.ConversationEntry.Kind.USER
                    && entry.text().equals("Keep this request"));
            } catch (Exception failure) { throw new RuntimeException(failure); }
        };

        fixture.controller.submit("Keep this request");

        assertTrue(fixture.agent.requestWasArchivedAtStart, "the request must be durable before Codex starts");
        assertEquals("Keep this request", fixture.archiveStore.load(PROJECT).orElseThrow().entries().getFirst().text());
        fixture.close();

        var reopened = new Fixture(fixture.archiveStore, Runnable::run);
        assertEquals("Keep this request", reopened.controller.state().conversation().getFirst().text());
        reopened.close();
    }

    @Test void createsAndSelectsIndependentConversationsWithoutErasingHistory() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.submit("First chat request");
        fixture.agent.deliver(new AgentEvent.Completed("First chat response"));
        UUID first = fixture.controller.state().selectedConversation();
        fixture.controller.newConversation();
        UUID second = fixture.controller.state().selectedConversation();
        assertNotEquals(first, second);
        assertTrue(fixture.controller.state().conversation().isEmpty());
        assertTrue(fixture.archiveStore.load(first).orElseThrow().entries().stream()
            .anyMatch(entry -> entry.text().equals("First chat response")));
        fixture.controller.selectConversation(first);
        assertEquals(first, fixture.controller.state().selectedConversation());
        assertEquals("First chat request", fixture.controller.state().conversation().getFirst().text());
        assertEquals(first, fixture.archiveStore.selected().orElseThrow());
        fixture.close();
    }

    @Test void switchingConversationDoesNotCarryImageAttachmentsAcrossChats() {
        var fixture = new Fixture();
        fixture.controller.addReference(fixture.capturePath());
        assertEquals(1, fixture.controller.state().attachments().size());
        fixture.controller.createConversation();
        assertTrue(fixture.controller.state().attachments().isEmpty());
        fixture.close();
    }

    @Test void localScanFailureIsAnActionablePersistentChatDiagnostic() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.snapshotSource = () -> CompletableFuture.failedFuture(new IllegalStateException("region is unloaded"));
        fixture.controller.submit("Build here");
        var failure = fixture.controller.state().conversation().getLast();
        assertEquals(dev.krul.builder.archive.BuilderDiagnostic.Category.SCAN_CONTEXT,
            failure.diagnostic().orElseThrow().category());
        assertTrue(failure.text().contains("region is unloaded"));
        assertTrue(failure.text().contains("scan it again"));
        assertTrue(fixture.archiveStore.load(PROJECT).orElseThrow().entries().stream()
            .anyMatch(entry -> entry.diagnostic().map(d -> d.category() == dev.krul.builder.archive.BuilderDiagnostic.Category.SCAN_CONTEXT).orElse(false)));
        fixture.close();
    }

    @Test void archivePreservesTheInspectedPlanRevisionAndTruthfulReviewState() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.submit("Create a small pavilion");
        var plan = fixture.proposal(1, 12);
        fixture.agent.deliver(new AgentEvent.Proposal(plan, true, null));
        fixture.agent.deliver(new AgentEvent.Completed("Plan visually inspected"));
        var archived = fixture.archiveStore.load(PROJECT).orElseThrow().plans().getFirst();
        assertEquals(ConversationPlan.Lifecycle.PROPOSED, archived.lifecycle());
        assertTrue(archived.visualReviewed());
        assertTrue(archived.visualReviewFailure().isEmpty());
        fixture.close();
    }

    @Test void planEventsStayBetweenTheirRequestAndLaterConversationTurns() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.submit("First request");
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal(1, 12), true, null));
        fixture.agent.deliver(new AgentEvent.Completed("First response"));
        fixture.scans.add(fixture.snapshot(13));
        fixture.scans.add(fixture.snapshot(14));
        fixture.controller.submit("Later request");
        fixture.agent.deliver(new AgentEvent.Completed("Later response"));
        var entries = fixture.archiveStore.load(PROJECT).orElseThrow().entries();
        int first = indexOf(entries, "First request"), plan = indexContaining(entries, "Plan r1"), response = indexOf(entries, "First response");
        int later = indexOf(entries, "Later request");
        assertTrue(first < plan && plan < response && response < later, entries.toString());
        fixture.close();
    }

    private static int indexOf(List<dev.krul.builder.archive.ConversationEntry> entries, String text) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).text().equals(text)) return i;
        return -1;
    }
    private static int indexContaining(List<dev.krul.builder.archive.ConversationEntry> entries, String text) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).text().contains(text)) return i;
        return -1;
    }

    @Test void liveProgressAndDiscussionAnswerRemainVisibleWithoutAPlan() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Describe this build"); fixture.takeScreenshot(); fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.TextDelta("Looking up mod blocks…"));
        assertEquals("Looking up mod blocks…", fixture.controller.state().status());
        fixture.agent.deliver(new AgentEvent.Completed("May I replace the terrain inside the selected area?"));
        assertEquals("Response received · no build plan", fixture.controller.state().status());
        assertEquals("May I replace the terrain inside the selected area?", fixture.controller.state().conversation().getLast().text());
        fixture.close();
    }

    @Test void clarificationCanBeContinuedAndBothTurnsStayInTheConversation() {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("Build a lake house"); fixture.takeScreenshot(); fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Completed("May I excavate below the platform?"));
        fixture.scans.add(fixture.snapshot(13));
        fixture.controller.submit("go");
        assertEquals(2, fixture.agent.starts, fixture.controller.state().phase() + " / " + fixture.controller.state().status());
        assertEquals("go", fixture.agent.request.text());
        assertEquals(2, fixture.controller.state().conversation().stream().filter(m -> m.role().equals("you")).count());
        fixture.agent.deliver(new AgentEvent.Completed("Planning the requested excavation now."));
        assertEquals("Planning the requested excavation now.", fixture.controller.state().conversation().getLast().text());
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
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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
        fixture.takeScreenshot();
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

    @Test void nextPreparationKeepsExplicitScreenshotAndReferences() throws Exception {
        var fixture = new Fixture();
        fixture.controller.setCloudConsent(true);
        fixture.controller.addReference(fixture.capturePath());
        UUID referenceId = fixture.controller.state().attachments().getFirst().id();

        fixture.controller.send("First review");
        fixture.takeScreenshot();
        UUID firstScreenshot = fixture.controller.state().prepared().orElseThrow().attachments().stream()
            .map(AttachmentPolicy.Attachment::id).filter(id -> !id.equals(referenceId)).findFirst().orElseThrow();
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Completed("done"));

        fixture.scans.add(fixture.snapshot(13));
        fixture.capture = new CompletableFuture<>();
        fixture.controller.send("Second review");

        BuilderUiState secondPrepared = fixture.controller.state();
        assertTrue(secondPrepared.prepared().isPresent(), secondPrepared.status());
        List<UUID> ids = secondPrepared.prepared().orElseThrow().attachments().stream()
            .map(AttachmentPolicy.Attachment::id).toList();
        assertTrue(ids.contains(referenceId));
        assertTrue(ids.contains(firstScreenshot));
        assertEquals(2, ids.size());
        fixture.close();
    }

    @Test void queuedSendCannotStartAfterRevocationOrCancellation() {
        for (boolean revoke : List.of(true, false)) {
            var io = new QueuedExecutor();
            var fixture = new Fixture(io);
            fixture.controller.setCloudConsent(true);
            fixture.controller.send("queued request");
            fixture.takeScreenshot();
            io.drain();
            fixture.controller.confirmSend();
            assertEquals(0, fixture.agent.starts);
            if (revoke) {
                fixture.controller.setCloudConsent(false);
                fixture.controller.setCloudConsent(true);
            } else fixture.controller.cancel();
            io.drain();
            assertEquals(0, fixture.agent.starts);
            fixture.close();
            io.drain();
        }
    }

    @Test void queuedSendStartsNormallyWhenStillAuthorized() {
        var io = new QueuedExecutor();
        var fixture = new Fixture(io);
        fixture.controller.setCloudConsent(true);
        fixture.controller.send("queued request");
        fixture.takeScreenshot();
        io.drain();
        fixture.controller.confirmSend();
        assertEquals(0, fixture.agent.starts);
        io.drain();
        assertEquals(1, fixture.agent.starts);
        assertEquals("queued request", fixture.agent.request.text());
        fixture.close();
        io.drain();
    }

    @Test void queuedLoginAndReconnectAreInvalidatedButFreshLoginStillWorks() {
        for (boolean revoke : List.of(true, false)) {
            var io = new QueuedExecutor();
            var fixture = new Fixture(io);
            fixture.controller.setCloudConsent(true);
            fixture.controller.login();
            fixture.controller.reconnect();
            if (revoke) {
                fixture.controller.setCloudConsent(false);
                fixture.controller.setCloudConsent(true);
            } else fixture.controller.cancel();
            io.drain();
            assertEquals(0, fixture.agent.logins);
            fixture.controller.reconnect();
            io.drain();
            assertEquals(1, fixture.agent.logins);
            fixture.close();
            io.drain();
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.addLast(task); }
        void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
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
        int captureCalls;
        final AttachmentPolicy attachments;
        final ConversationArchiveStore archiveStore;
        final BuilderController controller;
        Fixture() { this(Runnable::run); }
        Fixture(Executor ioExecutor) { this(new ConversationArchiveStore(temp.resolve("conversations")), ioExecutor); }
        Fixture(ConversationArchiveStore archiveStore, Executor ioExecutor) {
            this.archiveStore = archiveStore;
            try { attachments = new AttachmentPolicy(temp.resolve(UUID.randomUUID().toString())); }
            catch (Exception e) { throw new RuntimeException(e); }
            controller = new BuilderController(PROJECT, "codex", () -> snapshotSource.get(), () -> session, agent,
                () -> { captureCalls++; return capture; }, attachments, archiveStore, Runnable::run,
                ioExecutor, ignored -> {}, ignored -> {});
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
        void takeScreenshot() {
            controller.captureScreenshot();
            capture.complete(capturePath());
            capture = new CompletableFuture<>();
        }
        void close() { controller.close(); attachments.close(); }
    }

    private static final class FakeAgent implements AgentClient {
        int starts, logins; AgentRequest request; Consumer<AgentEvent> events;
        java.util.function.BooleanSupplier beforeStart = () -> false;
        boolean requestWasArchivedAtStart;
        public void start(AgentRequest request, Consumer<AgentEvent> events) { starts++; requestWasArchivedAtStart = beforeStart.getAsBoolean(); this.request = request; this.events = events; }
        public void cancel() {}
        public Status status() { return new Status(Connection.READY, Login.AUTHENTICATED, "Ready"); }
        public void login(Consumer<AgentEvent> events) { logins++; }
        public void close() {}
        void deliver(AgentEvent event) { events.accept(event); }
    }
}

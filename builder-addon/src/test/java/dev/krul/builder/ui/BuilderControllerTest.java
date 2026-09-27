package dev.krul.builder.ui;

import dev.krul.builder.agent.*;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class BuilderControllerTest {
    private static final UUID PROJECT = UUID.fromString("17b45314-4130-4293-98b5-98326d525e13");
    private static final StateKey AIR = new StateKey("minecraft:air", Map.of());
    private static final StateKey STONE = new StateKey("minecraft:stone", Map.of());
    @TempDir Path temp;

    @Test void sendPreparesFreshContextButRequiresExplicitConsent() throws Exception {
        var fixture = new Fixture();
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

    @Test void doubleSendDoesNotStartOverlappingProviderTurns() {
        var fixture = new Fixture();
        fixture.controller.send("one");
        fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.controller.send("two");
        assertEquals(1, fixture.agent.starts);
        fixture.close();
    }

    @Test void cancelDiscardsLateProposal() {
        var fixture = new Fixture();
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
        fixture.controller.send("one"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.controller.worldChanged(UUID.randomUUID());
        fixture.agent.deliver(new AgentEvent.Proposal(fixture.proposal()));
        assertTrue(fixture.controller.state().draft().isEmpty());
        assertTrue(fixture.controller.state().snapshot().isEmpty());
    }

    @Test void providerFailureKeepsPreviouslyAcceptedPlan() throws Exception {
        var fixture = new Fixture();
        fixture.session.propose(fixture.proposal());
        fixture.session.accept(1, fixture.snapshot(11));
        fixture.controller.send("improve it"); fixture.capture.complete(fixture.capturePath());
        fixture.controller.confirmSend();
        fixture.agent.deliver(new AgentEvent.Failed("provider unavailable"));
        assertEquals(BuilderUiState.Phase.FAILED, fixture.controller.state().phase());
        assertEquals(fixture.accepted, fixture.controller.state().accepted().orElseThrow());
        fixture.close();
    }

    private final class Fixture {
        final UUID world = UUID.randomUUID();
        final Bounds bounds = new Bounds(new Cell(0, 0, 0), new Cell(0, 0, 0));
        final WorldSnapshot latest = snapshot(12);
        final ProjectSession session = new ProjectSession(PROJECT, snapshot(10), ignored -> true);
        final Deque<WorldSnapshot> scans = new ArrayDeque<>();
        final BuildPlan accepted = proposal();
        final FakeAgent agent = new FakeAgent();
        final CompletableFuture<Path> capture = new CompletableFuture<>();
        final AttachmentPolicy attachments;
        final BuilderController controller;
        Fixture() {
            try { attachments = new AttachmentPolicy(temp.resolve(UUID.randomUUID().toString())); }
            catch (Exception e) { throw new RuntimeException(e); }
            controller = new BuilderController(PROJECT, "codex", this::fresh, () -> session, agent,
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
        int starts; AgentRequest request; Consumer<AgentEvent> events;
        public void start(AgentRequest request, Consumer<AgentEvent> events) { starts++; this.request = request; this.events = events; }
        public void cancel() {}
        public Status status() { return new Status(Connection.READY, Login.AUTHENTICATED, "Ready"); }
        public void login(Consumer<AgentEvent> events) {}
        public void close() {}
        void deliver(AgentEvent event) { events.accept(event); }
    }
}

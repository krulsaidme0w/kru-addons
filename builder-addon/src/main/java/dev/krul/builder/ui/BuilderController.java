package dev.krul.builder.ui;

import dev.krul.builder.agent.*;
import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.*;
import dev.krul.builder.visual.AttachmentPolicy;

import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.*;

/** Client-thread owner for Builder UI transitions and asynchronous callback generations. */
public final class BuilderController implements AutoCloseable {
    @FunctionalInterface public interface SnapshotSource { CompletableFuture<WorldSnapshot> fresh(); }
    private final UUID project;
    private final String executable;
    private final SnapshotSource snapshots;
    private final Supplier<ProjectSession> sessions;
    private AgentClient agent;
    private final Supplier<CompletableFuture<Path>> capture;
    private final AttachmentPolicy attachments;
    private final ProjectStore store;
    private final Executor clientExecutor, ioExecutor;
    private final Consumer<BuildPlan> reopenProject;
    private final List<BuilderUiState.Message> conversation = new ArrayList<>();
    private final Map<UUID, BuilderUiState.Thumbnail> thumbnails = new HashMap<>();
    private final Set<UUID> removedAttachments = new HashSet<>();
    private BuilderUiState.Phase phase = BuilderUiState.Phase.DISCONNECTED;
    private String status = "Connect and sign in to begin";
    private Optional<WorldSnapshot> snapshot = Optional.empty();
    private BuilderUiState.Prepared prepared;
    private WorldSnapshot preparedSnapshot;
    private CompletableFuture<?> pendingCapture;
    private AttachmentPolicy.RequestLease requestLease;
    private long generation;
    private PendingStart pendingStart;
    private UUID world;
    private UUID automaticScreenshot;
    private boolean closed, collapsed, settingsExpanded, nonAtomicSave;
    private volatile boolean cloudConsent;
    private String selectedStage;
    private Integer selectedLayer;
    private List<String> cleanupWarnings = List.of();
    private java.net.URI authorizationUrl;
    private String userCode;

    public BuilderController(UUID project, String executable, SnapshotSource snapshots,
                             Supplier<ProjectSession> sessions, AgentClient agent,
                             Supplier<CompletableFuture<Path>> capture, AttachmentPolicy attachments,
                             ProjectStore store, Executor clientExecutor, Executor ioExecutor,
                             Consumer<BuildPlan> reopenProject) {
        this.project = Objects.requireNonNull(project);
        this.executable = Objects.requireNonNull(executable);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.sessions = Objects.requireNonNull(sessions);
        this.agent = Objects.requireNonNull(agent);
        this.capture = Objects.requireNonNull(capture);
        this.attachments = Objects.requireNonNull(attachments);
        this.store = Objects.requireNonNull(store);
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.reopenProject = Objects.requireNonNull(reopenProject);
        syncConnection();
    }

    public BuilderUiState state() {
        ProjectSession session = sessions.get();
        Optional<BuildPlan> accepted = session == null ? Optional.empty() : session.accepted();
        Optional<BuildPlan> draft = session == null ? Optional.empty() : session.draft();
        Progress progress = snapshot.isPresent() && session != null ? session.progress(snapshot.get()) : new Progress(0, 0, 0, 0);
        var preparedView = Optional.ofNullable(prepared);
        var current = new BuilderUiState(phase, agent.status(), status, conversation, snapshot,
            visibleAttachments(), thumbnails, preparedView, draft, accepted, progress, collapsed, settingsExpanded,
            selectedStage, selectedLayer,
            executable,
            authorizationUrl, userCode, nonAtomicSave, cleanupWarnings, cloudConsent);
        return current;
    }

    /** Prepare local context and capture; sending only occurs after confirmSend(). */
    public void send(String text) {
        ensureOpen();
        if (!cloudConsent) { status = "Review the cloud data notice and grant session consent before connecting or sending"; return; }
        if (phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING
                || prepared != null || phase == BuilderUiState.Phase.READY && activeTurn) return;
        String message = Objects.requireNonNull(text).strip();
        if (message.isEmpty()) { status = "Write a request before preparing"; return; }
        if (agent.status().connection() != AgentClient.Connection.READY
                || agent.status().login() != AgentClient.Login.AUTHENTICATED) {
            syncConnection(); status = "Connect and sign in before preparing a request"; return;
        }
        ProjectSession session = sessions.get();
        if (session == null) { status = "Select an area and complete a fresh scan first"; return; }
        removeAutomaticScreenshot();
        prepared = null;
        long run = advanceGeneration();
        phase = BuilderUiState.Phase.PREPARING;
        status = "Refreshing a bounded world snapshot locally — nothing has been sent";
        try {
            snapshots.fresh().whenComplete((context, failure) -> clientExecutor.execute(() -> {
                if (!current(run)) return;
                if (failure != null || context == null) { phase = BuilderUiState.Phase.FAILED; status = failure == null ? "Fresh scan unavailable" : safeMessage(failure); return; }
                if (world != null && !world.equals(context.session())) { phase = BuilderUiState.Phase.FAILED; status = "World changed; scan the current selection again"; return; }
                List<String> errors = sessions.get().beginRequest(context);
                if (!errors.isEmpty()) { status = String.join("; ", errors); phase = BuilderUiState.Phase.FAILED; return; }
                snapshot = Optional.of(context); world = context.session();
                status = "Preparing local screenshot — nothing has been sent";
                try {
                    CompletableFuture<Path> captureFuture = capture.get(); pendingCapture = captureFuture;
                    captureFuture.whenComplete((path, captureFailure) -> clientExecutor.execute(() -> {
                        if (!current(run)) { if (path != null) ioExecutor.execute(() -> attachments.releaseCapture(path)); return; }
                        pendingCapture = null;
                        if (captureFailure != null) { phase = BuilderUiState.Phase.FAILED; status = safeMessage(captureFailure); return; }
                        try {
                            CompletableFuture.supplyAsync(() -> attachments.add(path), ioExecutor).whenComplete((attachment, addFailure) -> {
                                attachments.releaseCapture(path);
                                clientExecutor.execute(() -> {
                                    if (!current(run)) {
                                        if (attachment != null) ioExecutor.execute(() -> attachments.remove(attachment.id()));
                                        return;
                                    }
                                    if (addFailure != null) { phase = BuilderUiState.Phase.FAILED; status = safeMessage(addFailure); return; }
                                    automaticScreenshot = attachment.id();
                                    prepared = new BuilderUiState.Prepared(message, context.revision(), context.session(), visibleAttachments());
                                    preparedSnapshot = context;
                                    phase = BuilderUiState.Phase.READY;
                                    status = "Review the screenshot and attachments, then confirm to send to Codex";
                                    decodeThumbnail(attachment, run);
                                });
                            });
                        } catch (RuntimeException e) {
                            attachments.releaseCapture(path); phase = BuilderUiState.Phase.FAILED; status = safeMessage(e);
                        }
                    }));
                } catch (RuntimeException captureFailure) { phase = BuilderUiState.Phase.FAILED; status = safeMessage(captureFailure); }
            }));
        } catch (RuntimeException failure) { phase = BuilderUiState.Phase.FAILED; status = safeMessage(failure); }
    }

    private boolean activeTurn;
    private long turnAcceptedRevision = -1;

    /** Explicit privacy confirmation. Uses exactly the reviewed context and current visible attachment set. */
    public void confirmSend() {
        ensureOpen();
        if (!cloudConsent) { status = "Cloud consent is required before sending"; return; }
        if (prepared == null || activeTurn) return;
        if (visibleAttachments().stream().anyMatch(a -> !thumbnails.containsKey(a.id()))) {
            status = "Preparing the screenshot preview locally"; return;
        }
        BuilderUiState.Prepared review = prepared;
        WorldSnapshot reviewedSnapshot = preparedSnapshot;
        long check = advanceGeneration();
        phase = BuilderUiState.Phase.PREPARING;
        status = "Rechecking the reviewed snapshot before transmission";
        snapshots.fresh().whenComplete((now, failure) -> clientExecutor.execute(() -> {
            if (!current(check) || !cloudConsent) return;
            if (failure != null || now == null) { phase = BuilderUiState.Phase.FAILED; status = "Could not verify the reviewed snapshot"; return; }
            if (reviewedSnapshot == null || !sameWorldContent(reviewedSnapshot, now)) {
                prepared = null; preparedSnapshot = null; phase = BuilderUiState.Phase.FAILED;
                status = "World context changed; prepare again and review the refreshed screenshot";
                return;
            }
            ProjectSession activeSession = sessions.get();
            if (activeSession == null) { prepared = null; preparedSnapshot = null; phase = BuilderUiState.Phase.FAILED; status = "Project session ended; select and scan again"; return; }
            if (now.revision() > review.contextRevision()) {
                List<String> errors = activeSession.beginRequest(now);
                if (!errors.isEmpty()) { prepared = null; preparedSnapshot = null; phase = BuilderUiState.Phase.FAILED; status = String.join("; ", errors); return; }
                snapshot = Optional.of(now);
            }
            transmit(new BuilderUiState.Prepared(review.text(), now.revision(), now.session(), visibleAttachments()), now);
        }));
    }

    private void transmit(BuilderUiState.Prepared review, WorldSnapshot now) {
        if (!cloudConsent) { phase = BuilderUiState.Phase.CANCELLED; status = "Cloud consent revoked; request was not sent"; return; }
        ProjectSession session = sessions.get();
        Optional<BuildPlan> accepted = session == null ? Optional.empty() : session.accepted();
        List<Path> paths = visibleAttachments().stream().map(AttachmentPolicy.Attachment::path).toList();
        AgentRequest request = new AgentRequest(project, review.session(), review.contextRevision(), review.text(),
            now, paths, accepted);
        requestLease = attachments.retainForRequest();
        turnFinished = new CompletableFuture<>();
        long run = advanceGeneration();
        PendingStart dispatch = new PendingStart();
        pendingStart = dispatch;
        activeTurn = true; turnAcceptedRevision = accepted.map(BuildPlan::revision).orElse(-1L);
        prepared = null; preparedSnapshot = null; phase = BuilderUiState.Phase.WAITING;
        status = "Sending reviewed request to Codex";
        conversation.add(new BuilderUiState.Message("you", request.text()));
        try {
            AgentClient requestAgent = agent;
            ioExecutor.execute(() -> {
                try { dispatch.run(() -> requestAgent.start(request, event -> clientExecutor.execute(() -> onAgentEvent(run, event)))); }
                catch (RuntimeException failure) { clientExecutor.execute(() -> onAgentEvent(run, new AgentEvent.Failed(safeMessage(failure)))); }
            });
            requestLease.releaseWhen(turnFinished);
        } catch (RuntimeException failure) {
            activeTurn = false; phase = BuilderUiState.Phase.FAILED; status = safeMessage(failure);
            requestLease.close(); requestLease = null;
        }
    }

    private CompletableFuture<Void> turnFinished = new CompletableFuture<>();

    private void onAgentEvent(long run, AgentEvent event) {
        if (!current(run)) return;
        if (activeTurn && acceptedRevision() != turnAcceptedRevision) {
            invalidateTurn("Accepted plan changed while Codex was responding; response discarded");
            return;
        }
        if (event instanceof AgentEvent.Status statusEvent) {
            authorizationUrl = statusEvent.authorizationUrl(); userCode = statusEvent.userCode();
            status = statusEvent.message();
            if (!activeTurn) {
                if (statusEvent.connection() == AgentClient.Connection.READY && statusEvent.login() == AgentClient.Login.AUTHENTICATED)
                    phase = BuilderUiState.Phase.READY;
                else if (statusEvent.connection() == AgentClient.Connection.DISCONNECTED || statusEvent.connection() == AgentClient.Connection.INCOMPATIBLE)
                    phase = BuilderUiState.Phase.DISCONNECTED;
                else phase = BuilderUiState.Phase.WAITING;
            }
            return;
        }
        if (event instanceof AgentEvent.Proposal proposal) {
            List<String> errors = sessions.get().propose(proposal.plan());
            if (errors.isEmpty()) status = "Proposal ready to inspect";
            else status = "Proposal rejected: " + String.join("; ", errors);
        } else if (event instanceof AgentEvent.TextDelta delta) {
            status = "Codex is responding";
        } else if (event instanceof AgentEvent.Completed completed) {
            conversation.add(new BuilderUiState.Message("codex", completed.message()));
            finishTurn(BuilderUiState.Phase.READY, "Response received");
        } else if (event instanceof AgentEvent.Failed failed) {
            finishTurn(BuilderUiState.Phase.FAILED, failed.message());
        }
    }

    private void finishTurn(BuilderUiState.Phase next, String message) {
        activeTurn = false; phase = next; status = message; turnFinished.complete(null); advanceGeneration();
    }

    public void cancel() {
        if (closed) return;
        advanceGeneration();
        if (pendingCapture != null) pendingCapture.cancel(true);
        pendingCapture = null; prepared = null; preparedSnapshot = null;
        if (activeTurn) { AgentClient cancelAgent = agent; ioExecutor.execute(cancelAgent::cancel); activeTurn = false; turnFinished.complete(null); }
        ProjectSession session = sessions.get(); if (session != null) session.cancelRequest();
        phase = BuilderUiState.Phase.CANCELLED; status = "Request cancelled; accepted plan retained";
    }

    public void accept(long revision) {
        if (closed) return;
        long run = generation;
        status = "Refreshing snapshot before acceptance";
        snapshots.fresh().whenComplete((fresh, failure) -> clientExecutor.execute(() -> {
            if (closed || run != generation) return;
            if (failure != null || fresh == null) { status = "Could not verify a fresh world snapshot"; return; }
            ProjectSession session = sessions.get();
            List<String> errors = session == null ? List.of("No active project session") : session.accept(revision, fresh);
            if (!errors.isEmpty()) { status = String.join("; ", errors); return; }
            snapshot = Optional.of(fresh);
            invalidateTurn("Accepted plan changed; previous provider response discarded");
            long saveRun = generation;
            BuildPlan accepted = session.accepted().orElseThrow();
            CompletableFuture.runAsync(() -> {
                try {
                    ProjectStore.SaveResult result = store.save(accepted);
                    clientExecutor.execute(() -> {
                        if (closed || saveRun != generation) return;
                        nonAtomicSave = result.nonAtomicFallback();
                        cleanupWarnings = result.cleanupWarnings().stream().map(IOException::getMessage).toList();
                        status = cleanupWarnings.isEmpty() ? "Accepted plan saved" : "Plan saved with cleanup warnings";
                    });
                } catch (IOException e) {
                    clientExecutor.execute(() -> { if (!closed && saveRun == generation) status = "Plan accepted in this session; save failed: " + safeMessage(e); });
                }
            }, ioExecutor);
        }));
    }

    public void reject() {
        if (closed) return;
        advanceGeneration();
        if (activeTurn) { AgentClient cancelAgent = agent; ioExecutor.execute(cancelAgent::cancel); activeTurn = false; turnFinished.complete(null); }
        ProjectSession session = sessions.get(); if (session != null) session.cancelRequest();
        prepared = null; preparedSnapshot = null;
        status = "Draft rejected; accepted plan kept";
    }

    public void selectionChanged() { invalidateContext(false, "Selection changed; refresh context and screenshot"); }
    public void worldChanged() { invalidateContext(true, "World changed; select and scan this world to continue"); }

    private void invalidateContext(boolean resetWorld, String reason) {
        if (closed) return;
        advanceGeneration();
        if (resetWorld) { world = null; conversation.clear(); }
        snapshot = Optional.empty(); prepared = null; preparedSnapshot = null; thumbnails.clear();
        attachments.draft().forEach(attachment -> { removedAttachments.add(attachment.id()); ioExecutor.execute(() -> attachments.remove(attachment.id())); });
        if (pendingCapture != null) pendingCapture.cancel(true);
        pendingCapture = null;
        if (activeTurn) { AgentClient cancelAgent = agent; ioExecutor.execute(cancelAgent::cancel); activeTurn = false; turnFinished.complete(null); }
        ProjectSession session = sessions.get(); if (session != null) session.cancelRequest();
        phase = BuilderUiState.Phase.DISCONNECTED; status = reason;
    }

    public void observeSnapshot(WorldSnapshot current) {
        if (closed || current == null || prepared != null) return;
        if (world != null && !world.equals(current.session())) return;
        if (snapshot.isEmpty() || current.revision() >= snapshot.orElseThrow().revision()) snapshot = Optional.of(current);
    }

    public void login() {
        if (closed) return;
        if (!cloudConsent) { status = "Grant session consent before connecting to Codex"; return; }
        long run = advanceGeneration(); phase = BuilderUiState.Phase.WAITING; status = "Opening official Codex sign-in";
        PendingStart dispatch = new PendingStart();
        pendingStart = dispatch;
        AgentClient loginAgent = agent;
        ioExecutor.execute(() -> dispatch.run(() ->
            loginAgent.login(event -> clientExecutor.execute(() -> onAgentEvent(run, event)))));
    }

    public void reconnect() { if (!closed) login(); }
    public void setCloudConsent(boolean consent) {
        if (closed || cloudConsent == consent) return;
        cloudConsent = consent;
        if (!consent) {
            advanceGeneration();
            if (phase == BuilderUiState.Phase.WAITING && !activeTurn) {
                AgentClient pendingAgent = agent;
                ioExecutor.execute(pendingAgent::cancel);
            }
            if (activeTurn) {
                AgentClient cancelAgent = agent;
                ioExecutor.execute(cancelAgent::cancel);
                activeTurn = false;
                turnFinished.complete(null);
            }
            phase = agent.status().connection() == AgentClient.Connection.READY
                && agent.status().login() == AgentClient.Login.AUTHENTICATED
                ? BuilderUiState.Phase.READY : BuilderUiState.Phase.DISCONNECTED;
            status = "Cloud consent revoked; pending transmission cancelled";
        } else status = "Session consent granted; connect or prepare when ready";
    }
    public void replaceAgent(AgentClient replacement) {
        ensureOpen();
        advanceGeneration();
        AgentClient old = agent;
        if (activeTurn) { ioExecutor.execute(old::cancel); turnFinished.complete(null); }
        activeTurn = false; prepared = null;
        if (requestLease != null) { AttachmentPolicy.RequestLease lease = requestLease; ioExecutor.execute(lease::close); requestLease = null; }
        ioExecutor.execute(old::close); agent = Objects.requireNonNull(replacement);
        syncConnection(); status = "Executable updated; reconnect and sign in to continue";
    }
    public void removeAttachment(UUID id) {
        if (closed) return;
        if (Objects.equals(automaticScreenshot, id)) automaticScreenshot = null;
        removedAttachments.add(id); thumbnails.remove(id);
        if (prepared != null) prepared = new BuilderUiState.Prepared(prepared.text(), prepared.contextRevision(), prepared.session(), visibleAttachments());
        ioExecutor.execute(() -> attachments.remove(id));
    }
    /** Imports one explicitly chosen PNG as an addon-owned copy; the original is read-only input. */
    public void addReference(Path source) {
        ensureOpen();
        if (prepared != null || activeTurn || phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING) {
            status = "Add reference images before preparing or while no request is active"; return;
        }
        Objects.requireNonNull(source, "source");
        long run = generation;
        try {
            CompletableFuture.supplyAsync(() -> attachments.add(source), ioExecutor).whenComplete((attachment, failure) ->
                clientExecutor.execute(() -> {
                    if (!current(run) || prepared != null || activeTurn
                            || phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING) {
                        if (attachment != null) ioExecutor.execute(() -> attachments.remove(attachment.id()));
                        return;
                    }
                    if (failure != null) { status = "Reference image rejected: " + safeMessage(failure); return; }
                    status = "Reference image copied locally; it will be sent only after review and confirmation";
                    decodeThumbnail(attachment, run, false);
                }));
        } catch (RuntimeException failure) { status = "Reference image rejected: " + safeMessage(failure); }
    }
    private void removeAutomaticScreenshot() {
        if (automaticScreenshot == null) return;
        UUID previous = automaticScreenshot;
        automaticScreenshot = null;
        removeAttachment(previous);
    }
    public void toggleCollapsed() { collapsed = !collapsed; }
    public void toggleSettings() { settingsExpanded = !settingsExpanded; }
    public void selectStage(int offset) {
        BuildPlan plan = sessions.get() == null ? null : sessions.get().draft().orElseGet(() -> sessions.get().accepted().orElse(null));
        if (plan == null || plan.stages().isEmpty()) { selectedStage = null; return; }
        int current = 0;
        for (int i = 0; i < plan.stages().size(); i++) if (plan.stages().get(i).id().equals(selectedStage)) current = i;
        selectedStage = plan.stages().get(Math.floorMod(current + offset, plan.stages().size())).id();
    }
    public void selectLayer(int offset) {
        ProjectSession session = sessions.get();
        BuildPlan plan = session == null ? null : session.draft().orElseGet(() -> session.accepted().orElse(null));
        if (plan == null) return;
        List<Integer> layers = plan.stages().stream().flatMap(stage -> stage.targets().keySet().stream())
            .map(cell -> cell.y()).distinct().sorted().toList();
        if (layers.isEmpty()) return;
        int current = selectedLayer == null ? (offset < 0 ? 0 : -1) : layers.indexOf(selectedLayer);
        selectedLayer = layers.get(Math.floorMod(current + offset, layers.size()));
    }
    public void showAllLayers() { selectedLayer = null; }

    public void reopenProject() {
        if (closed) return;
        status = "Loading saved project";
        UUID expected = world;
        CompletableFuture.runAsync(() -> {
            try {
                Optional<BuildPlan> loaded = store.load(project);
                clientExecutor.execute(() -> {
                    if (!closed && Objects.equals(expected, world)) {
                        if (loaded.isPresent()) reopenProject.accept(loaded.orElseThrow());
                        else status = "No saved plan exists for this project";
                    }
                });
            } catch (IOException e) { clientExecutor.execute(() -> { if (!closed && Objects.equals(expected, world)) status = "Could not reopen saved project: " + safeMessage(e); }); }
        }, ioExecutor);
    }

    private void syncConnection() {
        AgentClient.Status connection = agent.status();
        if (connection.connection() == AgentClient.Connection.READY && connection.login() == AgentClient.Login.AUTHENTICATED) {
            if (!activeTurn && prepared == null) phase = BuilderUiState.Phase.READY;
            status = "Codex ready";
        } else { phase = BuilderUiState.Phase.DISCONNECTED; status = connection.detail(); }
    }
    private void decodeThumbnail(AttachmentPolicy.Attachment attachment, long run) {
        decodeThumbnail(attachment, run, true);
    }
    private void decodeThumbnail(AttachmentPolicy.Attachment attachment, long run, boolean requireCurrentRun) {
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage source = ImageIO.read(attachment.path().toFile());
                if (source == null) throw new IOException("Image preview could not be decoded");
                double scale = Math.min(1d, 144d / Math.max(source.getWidth(), source.getHeight()));
                int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try { graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); graphics.drawImage(source, 0, 0, width, height, null); }
                finally { graphics.dispose(); }
                byte[] rgba = new byte[width * height * 4];
                for (int y = 0, i = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y); rgba[i++] = (byte) (argb >>> 16); rgba[i++] = (byte) (argb >>> 8);
                    rgba[i++] = (byte) argb; rgba[i++] = (byte) (argb >>> 24);
                }
                var thumbnail = new BuilderUiState.Thumbnail(width, height, rgba);
                clientExecutor.execute(() -> {
                    boolean stillVisible = !closed && !removedAttachments.contains(attachment.id())
                        && attachments.draft().stream().anyMatch(item -> item.id().equals(attachment.id()));
                    if (stillVisible && (!requireCurrentRun || current(run))) thumbnails.put(attachment.id(), thumbnail);
                });
            } catch (IOException failure) {
                clientExecutor.execute(() -> {
                    if (current(run) && !removedAttachments.contains(attachment.id())) {
                        prepared = null; preparedSnapshot = null; phase = BuilderUiState.Phase.FAILED;
                        status = "Attachment preview unavailable: " + safeMessage(failure);
                    }
                });
            }
        }, ioExecutor);
    }
    private List<AttachmentPolicy.Attachment> visibleAttachments() {
        return attachments.draft().stream().filter(a -> !removedAttachments.contains(a.id())).toList();
    }
    private long acceptedRevision() {
        ProjectSession session = sessions.get();
        return session == null ? -1L : session.accepted().map(BuildPlan::revision).orElse(-1L);
    }
    private void invalidateTurn(String message) {
        advanceGeneration();
        if (activeTurn) { AgentClient staleAgent = agent; ioExecutor.execute(staleAgent::cancel); activeTurn = false; turnFinished.complete(null); }
        phase = agent.status().connection() == AgentClient.Connection.READY
            && agent.status().login() == AgentClient.Login.AUTHENTICATED ? BuilderUiState.Phase.READY : BuilderUiState.Phase.DISCONNECTED;
        status = message;
    }
    private static boolean sameWorldContent(WorldSnapshot first, WorldSnapshot second) {
        return first.session().equals(second.session()) && first.bounds().equals(second.bounds())
            && first.known().equals(second.known()) && first.unknown().equals(second.unknown());
    }
    public void showNotice(String message) { if (!closed) status = Objects.requireNonNull(message); }
    private boolean current(long run) { return !closed && run == generation; }
    private long advanceGeneration() {
        if (pendingStart != null) { pendingStart.cancel(); pendingStart = null; }
        return ++generation;
    }

    /** Serializes cancellation with the agent invocation, including the check-to-call boundary. */
    private static final class PendingStart {
        private boolean consumed;
        synchronized void cancel() { consumed = true; }
        synchronized void run(Runnable action) {
            if (consumed) return;
            consumed = true;
            action.run();
        }
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("Builder controller is closed"); }
    private static String safeMessage(Throwable failure) {
        Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage(); return message == null || message.isBlank() ? "Operation failed" : message;
    }
    @Override public void close() {
        if (closed) return;
        closed = true; advanceGeneration();
        if (pendingCapture != null) pendingCapture.cancel(true);
        pendingCapture = null;
        AgentClient closing = agent;
        if (activeTurn) { ioExecutor.execute(closing::cancel); turnFinished.complete(null); }
        activeTurn = false;
        if (requestLease != null) { AttachmentPolicy.RequestLease lease = requestLease; ioExecutor.execute(lease::close); requestLease = null; }
        ioExecutor.execute(closing::close);
    }
}

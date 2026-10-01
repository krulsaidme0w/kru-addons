package dev.krul.builder.ui;

import dev.krul.builder.agent.*;
import dev.krul.builder.archive.BuilderDiagnosticMapper;
import dev.krul.builder.archive.ConversationArchive;
import dev.krul.builder.archive.ConversationArchiveStore;
import dev.krul.builder.archive.ConversationEntry;
import dev.krul.builder.archive.ConversationPlan;
import dev.krul.builder.archive.ConversationSummary;
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
    private UUID project;
    private final String executable;
    private final SnapshotSource snapshots;
    private final Supplier<ProjectSession> sessions;
    private AgentClient agent;
    private final Supplier<CompletableFuture<Path>> capture;
    private final AttachmentPolicy attachments;
    private final ConversationArchiveStore archiveStore;
    private final Executor clientExecutor, ioExecutor;
    private final Consumer<BuildPlan> reopenProject;
    private final Consumer<UUID> conversationSelected;
    private final List<BuilderUiState.Message> conversation = new ArrayList<>();
    private List<ConversationSummary> conversationSummaries = List.of();
    private List<ConversationPlan> planHistory = List.of();
    private ConversationArchive currentArchive;
    private String archiveError = "";
    private boolean archiveReady, archiveBusy;
    private long archiveLoadGeneration;
    private CompletableFuture<Void> archiveWriteTail = CompletableFuture.completedFuture(null);
    private final Map<UUID, BuilderUiState.Thumbnail> thumbnails = new HashMap<>();
    private final Set<UUID> removedAttachments = new HashSet<>();
    private BuilderUiState.Phase phase = BuilderUiState.Phase.DISCONNECTED;
    private String status = "Connect and sign in to begin";
    private Optional<WorldSnapshot> snapshot = Optional.empty();
    private BuilderUiState.Prepared prepared;
    private WorldSnapshot preparedSnapshot;
    private CompletableFuture<?> pendingCapture;
    private boolean screenshotBusy;
    private AttachmentPolicy.RequestLease requestLease;
    private long generation;
    private long submitGeneration = -1;
    private PendingStart pendingStart;
    private UUID world;
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
                             ConversationArchiveStore archiveStore, Executor clientExecutor, Executor ioExecutor,
                             Consumer<UUID> conversationSelected, Consumer<BuildPlan> reopenProject) {
        this.project = Objects.requireNonNull(project);
        this.executable = Objects.requireNonNull(executable);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.sessions = Objects.requireNonNull(sessions);
        this.agent = Objects.requireNonNull(agent);
        this.capture = Objects.requireNonNull(capture);
        this.attachments = Objects.requireNonNull(attachments);
        this.archiveStore = Objects.requireNonNull(archiveStore);
        this.clientExecutor = Objects.requireNonNull(clientExecutor);
        this.ioExecutor = Objects.requireNonNull(ioExecutor);
        this.conversationSelected = Objects.requireNonNull(conversationSelected);
        this.reopenProject = Objects.requireNonNull(reopenProject);
        syncConnection();
        loadInitialArchive();
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
            authorizationUrl, userCode, nonAtomicSave, cleanupWarnings, cloudConsent,
            conversationSummaries, currentArchive == null ? null : currentArchive.id(), planHistory, archiveReady, archiveError);
        return current;
    }

    private void loadInitialArchive() {
        long run = ++archiveLoadGeneration;
        archiveBusy = true;
        status = "Loading saved conversations";
        ioExecutor.execute(() -> {
            ConversationArchive selected = null;
            ConversationArchiveStore.ListResult listed;
            String failureText = null;
            try {
                listed = archiveStore.list();
                ConversationArchiveStore.ListResult initialListing = listed;
                Optional<UUID> selectedId;
                try { selectedId = archiveStore.selected(); }
                catch (IOException badIndex) { selectedId = Optional.empty(); }
                UUID chosen = selectedId.filter(id -> initialListing.conversations().stream().anyMatch(item -> item.id().equals(id)))
                    .orElse(initialListing.conversations().isEmpty() ? null : initialListing.conversations().getFirst().id());
                if (chosen == null) {
                    UUID initial = initialListing.issues().stream().anyMatch(issue -> issue.conversationId().filter(project::equals).isPresent())
                        ? UUID.randomUUID() : project;
                    long now = System.currentTimeMillis();
                    selected = new ConversationArchive(initial, "New chat", now, now, List.of(), List.of());
                    archiveStore.save(selected);
                    archiveStore.select(initial);
                } else {
                    selected = archiveStore.load(chosen).orElseThrow(() -> new IOException("Selected conversation disappeared"));
                    if (selectedId.filter(chosen::equals).isEmpty()) archiveStore.select(chosen);
                }
                listed = archiveStore.list();
            } catch (Exception failure) { failureText = safeMessage(failure); }
            ConversationArchive loaded = selected;
            ConversationArchiveStore.ListResult result = listedOrEmpty();
            String computedError = failureText;
            if (computedError == null && !result.issues().isEmpty()) computedError = result.issues().getFirst().message();
            final String error = computedError;
            clientExecutor.execute(() -> {
                if (closed || run != archiveLoadGeneration) return;
                archiveBusy = false;
                if (loaded == null) {
                    archiveReady = false; archiveError = error == null ? "Conversation archive unavailable" : error;
                    status = archiveError;
                    return;
                }
                installArchive(loaded, result, error);
            });
        });
    }

    private ConversationArchiveStore.ListResult listedOrEmpty() {
        try { return archiveStore.list(); }
        catch (IOException ignored) { return new ConversationArchiveStore.ListResult(List.of(), List.of()); }
    }

    private void installArchive(ConversationArchive archive, ConversationArchiveStore.ListResult listed, String error) {
        project = archive.id();
        currentArchive = archive;
        planHistory = archive.plans();
        conversation.clear();
        for (ConversationEntry entry : archive.entries()) {
            String role = switch (entry.kind()) {
                case USER -> "you";
                case AGENT -> "codex";
                case FAILURE -> "codex";
                case SYSTEM -> "builder";
            };
            conversation.add(new BuilderUiState.Message(role, entry.text(), entry.diagnostic()));
        }
        for (ConversationPlan plan : archive.plans()) {
            int timelineIndex = -1;
            for (int i = 0; i < conversation.size(); i++) {
                var message = conversation.get(i);
                if (message.role().equals("builder") && archive.entries().get(i).planRevision()
                    .filter(revision -> revision == plan.plan().revision()).isPresent()) { timelineIndex = i; break; }
            }
            if (timelineIndex >= 0) {
                int cells = plan.plan().stages().stream().mapToInt(stage -> stage.targets().size()).sum();
                String review = plan.visualReviewed() ? "visual review complete"
                    : "visual review unavailable: " + plan.visualReviewFailure().orElse("not performed");
                String summary = "Plan r" + plan.plan().revision() + " " + plan.lifecycle().name().toLowerCase(Locale.ROOT)
                    + " · " + plan.plan().stages().size() + " stages · " + cells + " cells · " + review
                    + " · archived plan; revalidate before use";
                var old = conversation.get(timelineIndex);
                conversation.set(timelineIndex, new BuilderUiState.Message(old.role(), summary));
            }
        }
        conversationSummaries = listed.conversations();
        archiveReady = true;
        archiveError = error == null ? "" : error;
        conversationSelected.accept(project);
        status = archiveError.isEmpty() ? "Conversation ready" : "Conversation ready · archive warning";
    }

    /** Prepare local context and capture; sending only occurs after confirmSend(). */
    public void send(String text) {
        prepare(text, false);
    }

    /** One explicit user action authorizes capture and transmission for this request only. */
    public void submit(String text) {
        ensureOpen();
        setCloudConsent(true);
        prepare(text, true);
    }

    public void connect() {
        setCloudConsent(true);
        login();
    }

    /** Captures the current rendered world only after an explicit button press. */
    public void captureScreenshot() {
        ensureOpen();
        if (!cloudConsent) { status = "Grant session consent before preparing a screenshot"; return; }
        if (screenshotBusy || activeTurn || phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING) {
            status = "Wait for the current Builder operation before taking a screenshot"; return;
        }
        screenshotBusy = true;
        long run = generation;
        status = "Capturing screenshot locally — nothing has been sent";
        try {
            CompletableFuture<Path> future = capture.get();
            pendingCapture = future;
            future.whenComplete((path, failure) -> clientExecutor.execute(() -> {
                if (!current(run)) {
                    if (path != null) ioExecutor.execute(() -> attachments.releaseCapture(path));
                    screenshotBusy = false;
                    return;
                }
                pendingCapture = null;
                if (failure != null) { screenshotBusy = false; status = "Screenshot failed: " + safeMessage(failure); return; }
                try {
                    CompletableFuture.supplyAsync(() -> attachments.add(path), ioExecutor).whenComplete((attachment, addFailure) -> {
                        attachments.releaseCapture(path);
                        clientExecutor.execute(() -> {
                            screenshotBusy = false;
                            if (!current(run)) {
                                if (attachment != null) ioExecutor.execute(() -> attachments.remove(attachment.id()));
                                return;
                            }
                            if (addFailure != null) { status = "Screenshot rejected: " + safeMessage(addFailure); return; }
                            if (prepared != null) prepared = new BuilderUiState.Prepared(prepared.text(), prepared.contextRevision(),
                                prepared.session(), visibleAttachments());
                            status = "Screenshot attached locally; it will be sent only after review and confirmation";
                            decodeThumbnail(attachment, run, false);
                        });
                    });
                } catch (RuntimeException addFailure) {
                    attachments.releaseCapture(path); screenshotBusy = false; status = "Screenshot rejected: " + safeMessage(addFailure);
                }
            }));
        } catch (RuntimeException failure) {
            pendingCapture = null; screenshotBusy = false; status = "Screenshot failed: " + safeMessage(failure);
        }
    }

    private void prepare(String text, boolean submit) {
        ensureOpen();
        if (!cloudConsent) { status = "Review the cloud data notice and grant session consent before connecting or sending"; return; }
        if (phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING
                || prepared != null || phase == BuilderUiState.Phase.READY && activeTurn) return;
        if (screenshotBusy) { status = "Wait for the screenshot to finish before preparing the request"; return; }
        String message = Objects.requireNonNull(text).strip();
        if (message.isEmpty()) { status = "Write a request before preparing"; return; }
        if (agent.status().connection() != AgentClient.Connection.READY
                || agent.status().login() != AgentClient.Login.AUTHENTICATED) {
            syncConnection(); status = "Connect and sign in before preparing a request"; return;
        }
        prepared = null;
        long run = advanceGeneration();
        submitGeneration = submit ? run : -1;
        phase = BuilderUiState.Phase.PREPARING;
        status = "Refreshing a bounded world snapshot locally — nothing has been sent";
        boolean existingSession = sessions.get() != null;
        try {
            snapshots.fresh().whenComplete((context, failure) -> clientExecutor.execute(() -> {
                if (!current(run)) return;
                if (failure != null || context == null) { failLocally(dev.krul.builder.archive.BuilderDiagnostic.Category.SCAN_CONTEXT,
                    "scan selection", failure == null ? "Fresh scan unavailable" : safeMessage(failure), "Reselect a loaded area and scan it again."); return; }
                if (world != null && !world.equals(context.session())) { failLocally(dev.krul.builder.archive.BuilderDiagnostic.Category.SCAN_CONTEXT,
                    "verify world", "World changed; scan the current selection again", "Select and scan the current world before retrying."); return; }
                ProjectSession session = sessions.get();
                if (session == null) { failLocally(dev.krul.builder.archive.BuilderDiagnostic.Category.SCAN_CONTEXT,
                    "prepare request", "No active project session", "Set area bounds in Settings and complete a fresh scan."); return; }
                List<String> errors = existingSession ? session.beginRequest(context) : List.of();
                if (!errors.isEmpty()) { status = String.join("; ", errors); phase = BuilderUiState.Phase.FAILED; return; }
                snapshot = Optional.of(context); world = context.session();
                prepared = new BuilderUiState.Prepared(message, context.revision(), context.session(), visibleAttachments());
                preparedSnapshot = context;
                phase = BuilderUiState.Phase.READY;
                status = submit ? "Preparing request…" : "Review the request and attachments, then confirm to send to Codex";
                if (submit && visibleAttachments().stream().allMatch(a -> thumbnails.containsKey(a.id()))) confirmSend();
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
        if (!archiveReady || archiveBusy || currentArchive == null) { phase = BuilderUiState.Phase.FAILED; status = "Conversation archive is not ready"; return; }
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
        appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.USER,
            System.currentTimeMillis(), request.text(), Optional.empty()));
        long dispatchGeneration = run;
        try {
            AgentClient requestAgent = agent;
            persistArchive(() -> {
                try { ioExecutor.execute(() -> {
                    try { dispatch.run(() -> requestAgent.start(request, event -> clientExecutor.execute(() -> onAgentEvent(dispatchGeneration, event)))); }
                    catch (RuntimeException failure) { clientExecutor.execute(() -> onAgentEvent(dispatchGeneration, new AgentEvent.Failed(safeMessage(failure)))); }
                }); }
                catch (RuntimeException failure) { onAgentEvent(dispatchGeneration, new AgentEvent.Failed("Conversation save failed: " + safeMessage(failure))); }
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
            if (errors.isEmpty()) {
                status = "Proposal ready to inspect";
                ConversationPlan.Lifecycle lifecycle = planHistory.isEmpty()
                    ? ConversationPlan.Lifecycle.PROPOSED : ConversationPlan.Lifecycle.REVISED;
                var metadata = new ConversationPlan(proposal.plan(), lifecycle,
                    snapshot.map(WorldSnapshot::session).orElse(project),
                    proposal.plan().contextRevision(), snapshot.map(WorldSnapshot::bounds), List.of(), proposal.visualReviewed(),
                    Optional.ofNullable(proposal.reviewFailure()));
                planHistory = appendPlan(metadata);
                int targets = proposal.plan().stages().stream().mapToInt(stage -> stage.targets().size()).sum();
                String review = proposal.visualReviewed() ? "schematic inspected" : "visual inspection unavailable: "
                    + Objects.requireNonNullElse(proposal.reviewFailure(), "agent skipped the preview");
                appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.SYSTEM,
                    System.currentTimeMillis(), "Plan r" + proposal.plan().revision() + " "
                        + lifecycle.name().toLowerCase(Locale.ROOT) + " · " + proposal.plan().stages().size()
                        + " stages · " + targets + " cells · " + review, Optional.empty(),
                    Optional.of(proposal.plan().revision())));
                persistArchive(() -> {});
            } else {
                status = "Proposal rejected: " + String.join("; ", errors);
                var diagnostic = new dev.krul.builder.archive.BuilderDiagnostic(
                    dev.krul.builder.archive.BuilderDiagnostic.Category.INVALID_PROPOSAL, "Plan validation",
                    "Proposal did not pass local validation", String.join("; ", errors),
                    "Review the listed cells and request a corrected plan; the world was not changed.",
                    System.currentTimeMillis(), 1);
                appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.FAILURE,
                    diagnostic.occurredAtEpochMillis(), BuilderUiPresentation.diagnosticText(diagnostic), Optional.of(diagnostic)));
                persistArchive(() -> {});
            }
        } else if (event instanceof AgentEvent.TextDelta delta) {
            status = delta.text();
        } else if (event instanceof AgentEvent.Completed completed) {
            appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.AGENT,
                System.currentTimeMillis(), completed.message(), Optional.empty()));
            persistArchive(() -> {});
            ProjectSession session = sessions.get();
            finishTurn(BuilderUiState.Phase.READY, session != null && session.draft().isPresent()
                ? "Response received · plan ready to inspect" : "Response received · no build plan");
        } else if (event instanceof AgentEvent.Failed failed) {
            var diagnostic = BuilderDiagnosticMapper.fromAgentFailure(failed.message(), System.currentTimeMillis());
            appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.FAILURE,
                System.currentTimeMillis(), BuilderUiPresentation.diagnosticText(diagnostic), Optional.of(diagnostic)));
            persistArchive(() -> {});
            finishTurn(BuilderUiState.Phase.FAILED, diagnostic.cause());
        }
    }

    private void finishTurn(BuilderUiState.Phase next, String message) {
        activeTurn = false; phase = next; status = message; turnFinished.complete(null); advanceGeneration();
    }

    private void appendEntry(ConversationEntry entry) {
        conversation.add(new BuilderUiState.Message(switch (entry.kind()) {
            case USER -> "you"; case AGENT, FAILURE -> "codex"; case SYSTEM -> "builder";
        }, entry.text(), entry.diagnostic()));
        if (currentArchive != null) {
            var entries = new ArrayList<>(currentArchive.entries()); entries.add(entry);
            long now = Math.max(System.currentTimeMillis(), currentArchive.createdAtEpochMillis());
            String title = currentArchive.title();
            if (entry.kind() == ConversationEntry.Kind.USER && title.equals("New chat")) {
                String firstLine = entry.text().lines().findFirst().orElse(entry.text()).strip();
                title = firstLine.length() > 48 ? firstLine.substring(0, 47) + "…" : firstLine;
                if (title.isBlank()) title = "New chat";
            }
            currentArchive = new ConversationArchive(currentArchive.id(), title,
                currentArchive.createdAtEpochMillis(), now, entries, planHistory);
        }
    }

    private void failLocally(dev.krul.builder.archive.BuilderDiagnostic.Category category, String operation,
                             String cause, String nextAction) {
        phase = BuilderUiState.Phase.FAILED;
        status = cause;
        var diagnostic = BuilderDiagnosticMapper.fromFailure(category, operation,
            new IllegalStateException(cause), nextAction, System.currentTimeMillis());
        appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.FAILURE,
            diagnostic.occurredAtEpochMillis(), BuilderUiPresentation.diagnosticText(diagnostic), Optional.of(diagnostic)));
        persistArchive(() -> {});
    }

    private List<ConversationPlan> appendPlan(ConversationPlan plan) {
        var plans = new ArrayList<>(planHistory);
        plans.removeIf(old -> old.plan().revision() == plan.plan().revision());
        plans.add(plan); planHistory = List.copyOf(plans);
        if (currentArchive != null) {
            long now = Math.max(System.currentTimeMillis(), currentArchive.createdAtEpochMillis());
            currentArchive = new ConversationArchive(currentArchive.id(), currentArchive.title(), currentArchive.createdAtEpochMillis(),
                now, currentArchive.entries(), planHistory);
        }
        return planHistory;
    }

    private void persistArchive(Runnable afterSave) {
        if (currentArchive == null) { archiveError = "No active conversation to save"; return; }
        persistArchiveSnapshot(currentArchive, afterSave, () -> {});
    }

    private void persistArchiveSnapshot(ConversationArchive snapshotToSave, Runnable afterSave, Runnable onFailure) {
        archiveWriteTail = archiveWriteTail.handle((ignored, previousFailure) -> null)
            .thenRunAsync(() -> {
                try { archiveStore.save(snapshotToSave); archiveStore.select(snapshotToSave.id()); }
                catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            }, ioExecutor);
        archiveWriteTail.whenComplete((ignored, failure) -> clientExecutor.execute(() -> {
            if (closed || currentArchive == null || !currentArchive.id().equals(snapshotToSave.id())) return;
            if (failure != null) {
                archiveError = "Conversation save failed: " + safeMessage(failure);
                status = archiveError;
                if (activeTurn) {
                    var diagnostic = new dev.krul.builder.archive.BuilderDiagnostic(
                        dev.krul.builder.archive.BuilderDiagnostic.Category.LOCAL_STORAGE, "save conversation",
                        "Conversation was not saved", safeMessage(failure), "Check local storage permissions and retry.",
                        System.currentTimeMillis(), 1);
                    appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.FAILURE,
                        System.currentTimeMillis(), BuilderUiPresentation.diagnosticText(diagnostic), Optional.of(diagnostic)));
                    activeTurn = false; phase = BuilderUiState.Phase.FAILED; turnFinished.complete(null);
                    if (requestLease != null) { requestLease.close(); requestLease = null; }
                    advanceGeneration();
                }
                onFailure.run();
                return;
            }
            archiveError = "";
            conversationSummaries = replaceSummary(conversationSummaries, ConversationSummary.from(snapshotToSave));
            afterSave.run();
        }));
    }

    private static List<ConversationSummary> replaceSummary(List<ConversationSummary> values, ConversationSummary replacement) {
        var updated = new ArrayList<>(values);
        updated.removeIf(item -> item.id().equals(replacement.id()));
        updated.add(replacement);
        updated.sort(Comparator.comparingLong(ConversationSummary::updatedAtEpochMillis).reversed()
            .thenComparing(item -> item.id().toString()));
        return List.copyOf(updated);
    }

    public void cancel() {
        if (closed) return;
        advanceGeneration();
        if (pendingCapture != null) pendingCapture.cancel(true);
        pendingCapture = null; prepared = null; preparedSnapshot = null;
        screenshotBusy = false;
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
            BuildPlan accepted = session.accepted().orElseThrow();
            Optional<ConversationPlan> previous = planHistory.stream().filter(item -> item.plan().revision() == accepted.revision()).findFirst();
            ConversationPlan saved = previous.map(item -> new ConversationPlan(accepted, ConversationPlan.Lifecycle.SAVED,
                item.contextSession(), accepted.contextRevision(), item.sourceBounds(), item.diagnostics(),
                item.visualReviewed(), item.visualReviewFailure())).orElseGet(() -> new ConversationPlan(accepted,
                ConversationPlan.Lifecycle.SAVED, fresh.session(), accepted.contextRevision(), Optional.of(fresh.bounds()),
                List.of(), false, Optional.empty()));
            var savedPlans = new ArrayList<>(planHistory);
            savedPlans.removeIf(item -> item.plan().revision() == accepted.revision());
            savedPlans.add(saved);
            long now = Math.max(System.currentTimeMillis(), currentArchive.createdAtEpochMillis());
            var savedArchive = new ConversationArchive(currentArchive.id(), currentArchive.title(),
                currentArchive.createdAtEpochMillis(), now, currentArchive.entries(), savedPlans);
            persistArchiveSnapshot(savedArchive, () -> {
                currentArchive = savedArchive; planHistory = List.copyOf(savedPlans);
                status = "Accepted plan saved in this conversation";
                appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.SYSTEM,
                    System.currentTimeMillis(), "Plan r" + accepted.revision() + " accepted and saved for this conversation.",
                    Optional.empty(), Optional.of(accepted.revision())));
                persistArchive(() -> {});
            }, () -> status = "Plan accepted for this session, but conversation save failed");
        }));
    }

    public void reject() {
        if (closed) return;
        advanceGeneration();
        if (activeTurn) { AgentClient cancelAgent = agent; ioExecutor.execute(cancelAgent::cancel); activeTurn = false; turnFinished.complete(null); }
        ProjectSession session = sessions.get();
        Optional<BuildPlan> rejectedDraft = session == null ? Optional.empty() : session.draft();
        if (session != null) session.cancelRequest();
        if (rejectedDraft.isPresent()) {
            BuildPlan plan = rejectedDraft.orElseThrow();
            planHistory.stream().filter(item -> item.plan().revision() == plan.revision()).findFirst().ifPresent(item ->
                appendPlan(new ConversationPlan(item.plan(), ConversationPlan.Lifecycle.REJECTED, item.contextSession(),
                    item.contextRevision(), item.sourceBounds(), item.diagnostics(), item.visualReviewed(), item.visualReviewFailure())));
            appendEntry(new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.SYSTEM,
                System.currentTimeMillis(), "Plan r" + plan.revision() + " discarded; accepted plan retained.",
                Optional.empty(), Optional.of(plan.revision())));
            persistArchive(() -> {});
        }
        prepared = null; preparedSnapshot = null;
        status = "Draft rejected; accepted plan kept";
    }

    public void selectionChanged() { invalidateContext(false, "Selection changed; refresh context and screenshot"); }
    public void worldChanged() { invalidateContext(true, "World changed; select and scan this world to continue"); }

    private void invalidateContext(boolean resetWorld, String reason) {
        if (closed) return;
        advanceGeneration();
        if (resetWorld) world = null;
        snapshot = Optional.empty(); prepared = null; preparedSnapshot = null; thumbnails.clear();
        clearDraftAttachments();
        if (pendingCapture != null) pendingCapture.cancel(true);
        pendingCapture = null;
        screenshotBusy = false;
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
        status = "Loading saved plan";
        UUID expected = world;
        Optional<BuildPlan> loaded = planHistory.stream().filter(item -> item.lifecycle() == ConversationPlan.Lifecycle.SAVED)
            .max(Comparator.comparingLong(item -> item.plan().revision())).map(ConversationPlan::plan);
        if (!closed && Objects.equals(expected, world)) {
            if (loaded.isPresent()) reopenProject.accept(loaded.orElseThrow());
            else status = "No accepted plan in this conversation";
        }
    }

    public void newConversation() {
        ensureOpen();
        if (!archiveReady || archiveBusy) return;
        cancelTurnForConversationSwitch();
        long now = System.currentTimeMillis();
        var archive = new ConversationArchive(UUID.randomUUID(), "New chat", now, now, List.of(), List.of());
        archiveBusy = true;
        archiveError = "";
        archiveWriteTail.handle((ignored, failure) -> null).thenRunAsync(() -> {
            String failure = null;
            ConversationArchiveStore.ListResult listing = null;
            try { archiveStore.save(archive); archiveStore.select(archive.id()); listing = archiveStore.list(); }
            catch (IOException error) { failure = safeMessage(error); }
            String result = failure;
            ConversationArchiveStore.ListResult loadedListing = listing;
            clientExecutor.execute(() -> {
                archiveBusy = false;
                if (closed) return;
                if (result != null) { archiveError = "Could not create conversation: " + result; status = archiveError; return; }
                installArchive(archive, loadedListing, null);
                resetConversationContext();
            });
        }, ioExecutor);
    }

    public void createConversation() { newConversation(); }

    public void selectConversation(UUID id) {
        ensureOpen();
        if (id == null || archiveBusy || currentArchive != null && currentArchive.id().equals(id)) return;
        cancelTurnForConversationSwitch();
        archiveBusy = true;
        long request = ++archiveLoadGeneration;
        status = "Opening conversation";
        archiveWriteTail.handle((ignored, failure) -> null).thenRunAsync(() -> {
            ConversationArchive loaded;
            ConversationArchiveStore.ListResult listing;
            try {
                loaded = archiveStore.load(id).orElseThrow(() -> new IOException("Conversation no longer exists"));
                archiveStore.select(id);
                listing = archiveStore.list();
            } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            ConversationArchiveStore.ListResult loadedListing = listing;
            clientExecutor.execute(() -> {
                if (closed || request != archiveLoadGeneration) return;
                archiveBusy = false;
                installArchive(loaded, loadedListing, null);
                resetConversationContext();
            });
        }, ioExecutor).exceptionally(failure -> {
            clientExecutor.execute(() -> {
                if (closed || request != archiveLoadGeneration) return;
                archiveBusy = false; archiveError = "Could not open conversation: " + safeMessage(failure); status = archiveError;
            });
            return null;
        });
    }

    private void resetConversationContext() {
        advanceGeneration();
        if (activeTurn) { ioExecutor.execute(agent::cancel); activeTurn = false; turnFinished.complete(null); }
        prepared = null; preparedSnapshot = null; snapshot = Optional.empty(); world = null;
        phase = agent.status().connection() == AgentClient.Connection.READY && agent.status().login() == AgentClient.Login.AUTHENTICATED
            ? BuilderUiState.Phase.READY : BuilderUiState.Phase.DISCONNECTED;
        status = "Conversation opened";
    }

    private void cancelTurnForConversationSwitch() {
        advanceGeneration();
        if (activeTurn) { ioExecutor.execute(agent::cancel); activeTurn = false; turnFinished.complete(null); }
        prepared = null; preparedSnapshot = null;
        clearDraftAttachments();
        ProjectSession session = sessions.get(); if (session != null) session.cancelRequest();
    }

    private void clearDraftAttachments() {
        attachments.draft().forEach(attachment -> {
            removedAttachments.add(attachment.id()); thumbnails.remove(attachment.id());
            ioExecutor.execute(() -> attachments.remove(attachment.id()));
        });
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
                    if (submitGeneration == generation && prepared != null && cloudConsent
                            && visibleAttachments().stream().allMatch(a -> thumbnails.containsKey(a.id()))) confirmSend();
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
        screenshotBusy = false;
        AgentClient closing = agent;
        if (activeTurn) { ioExecutor.execute(closing::cancel); turnFinished.complete(null); }
        activeTurn = false;
        if (requestLease != null) { AttachmentPolicy.RequestLease lease = requestLease; ioExecutor.execute(lease::close); requestLease = null; }
        ioExecutor.execute(closing::close);
    }
}

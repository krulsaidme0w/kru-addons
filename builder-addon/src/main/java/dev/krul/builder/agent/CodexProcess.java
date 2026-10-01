package dev.krul.builder.agent;

import com.google.gson.*;
import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns one isolated Codex app-server process and never executes server tool requests. */
public final class CodexProcess implements AgentClient {
    private static final long STARTUP_MS = 15_000, TURN_MS = 180_000, LOGIN_MS = TimeUnit.MINUTES.toMillis(10);
    private final Path instanceDirectory;
    private final String executable;
    private final Launcher launcher;
    private final Predicate<StateKey> stateExists;
    private final BuilderDynamicTools dynamicTools;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "builder-codex-worker"));
    private final AtomicLong ids = new AtomicLong(1);
    private volatile Status status = new Status(Connection.DISCONNECTED, Login.UNKNOWN, "Not connected");
    private volatile Process process;
    private volatile BlockingQueue<FrameRead> frames;
    private final ArrayDeque<JsonObject> deferred = new ArrayDeque<>();
    private volatile String threadId, turnId;
    private volatile UUID project, session;
    private volatile boolean cancelled, closed;
    private volatile long generation;

    @FunctionalInterface interface Launcher {
        Process start(List<String> command, Path cwd, Map<String, String> environment) throws IOException;
    }
    private record FrameRead(JsonObject frame, IOException failure) {
        static FrameRead frame(JsonObject value) { return new FrameRead(value, null); }
        static FrameRead failure(IOException value) { return new FrameRead(null, value); }
    }
    public CodexProcess(Path instanceDirectory, Predicate<StateKey> stateExists) {
        this(instanceDirectory, "codex", stateExists);
    }
    public CodexProcess(Path instanceDirectory, String executable, Predicate<StateKey> stateExists) {
        this(instanceDirectory, stateExists, executable, CodexProcess::launch, null);
    }
    CodexProcess(Path instanceDirectory, Predicate<StateKey> stateExists, String executable, Launcher launcher) {
        this(instanceDirectory, stateExists, executable, launcher, null);
    }
    public CodexProcess(Path instanceDirectory, String executable, Predicate<StateKey> stateExists, BuilderDynamicTools dynamicTools) {
        this(instanceDirectory, stateExists, executable, CodexProcess::launch, Objects.requireNonNull(dynamicTools));
    }
    CodexProcess(Path instanceDirectory, Predicate<StateKey> stateExists, String executable, Launcher launcher, BuilderDynamicTools dynamicTools) {
        this.instanceDirectory = Objects.requireNonNull(instanceDirectory);
        this.stateExists = Objects.requireNonNull(stateExists);
        this.executable = Objects.requireNonNull(executable);
        this.launcher = Objects.requireNonNull(launcher);
        this.dynamicTools = dynamicTools;
    }
    private static Process launch(List<String> command, Path cwd, Map<String, String> environment) throws IOException {
        var builder = new ProcessBuilder(command).directory(cwd.toFile());
        builder.environment().clear(); builder.environment().putAll(environment); return builder.start();
    }
    private static Thread daemon(Runnable r, String n) { var t = new Thread(r, n); t.setDaemon(true); return t; }
    @Override public Status status() { return status; }

    @Override public void start(AgentRequest request, Consumer<AgentEvent> events) {
        Objects.requireNonNull(request); Objects.requireNonNull(events);
        if (closed) { emit(events, new AgentEvent.Failed("Codex client is closed")); return; }
        cancelled = false; long run = ++generation; set(Connection.STARTING, status.login(), "Checking isolated Codex connection");
        emit(events, new AgentEvent.Status(status.connection(), status.login(), status.detail(), null, null));
        worker.execute(() -> {
            try {
                if (dynamicTools != null) dynamicTools.beginTurn(request);
                ensureReady(events);
                if (cancelled || closed || run != generation) return;
                emit(events, new AgentEvent.Status(status.connection(), status.login(), status.detail(), null, null));
                if (status.login() != Login.AUTHENTICATED) {
                    set(Connection.READY, Login.REQUIRED, "Sign in to the Builder Codex profile");
                    emit(events, new AgentEvent.Failed("Builder Codex login is required")); return;
                }
                if (!request.images().isEmpty() || dynamicTools != null) checkImageModel(events);
                if (cancelled || closed || run != generation) return;
                if (threadId == null || !request.project().equals(project) || !request.session().equals(session))
                    startThread(request, events);
                if (cancelled || closed || run != generation) return;
                JsonObject response = rpc("turn/start", turnParams(request), STARTUP_MS, events);
                JsonObject turn = response.has("turn") && response.get("turn").isJsonObject() ? response.getAsJsonObject("turn") : response;
                turnId = str(turn, "id");
                emit(events, new AgentEvent.TextDelta("Codex is working…"));
                awaitTurn(request, events, run);
            } catch (CodexIsolation.IncompatibleException e) {
                set(Connection.INCOMPATIBLE, status.login(), e.getMessage());
                emit(events, new AgentEvent.Status(Connection.INCOMPATIBLE, status.login(), e.getMessage(), null, null));
                emit(events, new AgentEvent.Failed(e.getMessage()));
            } catch (Exception e) {
                if (!cancelled && !closed && run == generation) {
                    set(Connection.DISCONNECTED, status.login(), "Codex connection failed");
                emit(events, new AgentEvent.Failed(safe(e)));
                }
            } finally {
                if (dynamicTools != null) dynamicTools.endTurn();
            }
        });
    }

    private synchronized void ensureReady(Consumer<AgentEvent> events) throws Exception {
        if (process != null && process.isAlive()) {
            verifyPolicy(events);
            return;
        }
        var layout = CodexIsolation.prepare(instanceDirectory);
        var env = CodexIsolation.childEnvironment(System.getenv(), layout.home());
        Process version = launcher.start(List.of(executable, "--version"), layout.cwd(), env);
        if (!version.waitFor(STARTUP_MS, TimeUnit.MILLISECONDS)) { version.destroyForcibly(); throw new CodexIsolation.IncompatibleException("Codex version check timed out"); }
        String versionLine;
        try (InputStream stdout = version.getInputStream(); InputStream stderr = version.getErrorStream()) {
            versionLine = new String(stdout.readNBytes(256), StandardCharsets.UTF_8).trim();
        }
        if (version.exitValue() != 0) throw new CodexIsolation.IncompatibleException("Codex version check failed");
        CodexIsolation.validateVersion(versionLine);
        var command = new ArrayList<String>(); command.add(executable); command.addAll(CodexIsolation.arguments());
        process = launcher.start(command, layout.cwd(), env);
        frames = new ArrayBlockingQueue<>(128);
        Process owned = process; BlockingQueue<FrameRead> queue = frames;
        daemon(() -> readFrames(owned, queue), "builder-codex-stdout").start();
        daemon(() -> drainStderr(owned), "builder-codex-stderr").start();
        JsonObject client = new JsonObject(); client.addProperty("name", "kru-builder"); client.addProperty("title", "KRU Builder"); client.addProperty("version", "1");
        JsonObject capabilities = new JsonObject();
        // thread/start.runtimeWorkspaceRoots is part of Codex's opt-in experimental app-server API.
        capabilities.addProperty("experimentalApi", true);
        JsonObject init = new JsonObject(); init.add("clientInfo", client); init.add("capabilities", capabilities);
        rpc("initialize", init, STARTUP_MS, events);
        notifyServer("initialized", new JsonObject());
        verifyPolicy(events);
    }

    private void verifyPolicy(Consumer<AgentEvent> events) throws Exception {
        var layout = CodexIsolation.prepare(instanceDirectory);
        JsonObject readParams = new JsonObject(); readParams.addProperty("includeLayers", true); readParams.addProperty("cwd", layout.cwd().toString());
        JsonObject configRead = rpc("config/read", readParams, STARTUP_MS, events);
        CodexIsolation.validateEffectiveConfig(requiredObject(configRead, "config"));
        CodexIsolation.validateLayers(configRead);
        JsonObject account = rpc("account/read", new JsonObject(), STARTUP_MS, events);
        Login login = account.has("account") && !account.get("account").isJsonNull() ? Login.AUTHENTICATED : Login.REQUIRED;
        set(Connection.READY, login, login == Login.AUTHENTICATED ? "Isolated Codex is ready" : "Builder profile needs sign in");
    }

    private void startThread(AgentRequest request, Consumer<AgentEvent> events) throws Exception {
        var layout = CodexIsolation.prepare(instanceDirectory);
        JsonObject params = new JsonObject(); params.addProperty("cwd", layout.cwd().toString());
        params.addProperty("approvalPolicy", "never"); params.addProperty("sandbox", "read-only");
        params.addProperty("allowProviderModelFallback", false);
        params.add("environments", new JsonArray()); params.add("selectedCapabilityRoots", new JsonArray());
        params.add("runtimeWorkspaceRoots", new JsonArray());
        if (dynamicTools != null) params.add("dynamicTools", BuilderDynamicTools.definitions());
        // Builder's project UUID is local metadata, not a Codex cloud project ID.
        JsonObject config = new JsonObject(); config.add("mcp_servers", new JsonObject()); params.add("config", config);
        JsonObject response;
        try { response = rpc("thread/start", params, STARTUP_MS, events); }
        catch (IOException failure) {
            if (dynamicTools != null && failure.getMessage() != null && failure.getMessage().toLowerCase(Locale.ROOT).contains("dynamictool"))
                throw new CodexIsolation.IncompatibleException("Pinned Codex CLI does not support Builder dynamic tools", failure);
            throw failure;
        }
        JsonObject thread = requiredObject(response, "thread");
        String id = str(thread, "id");
        JsonObject sandbox = response.has("sandbox") && response.get("sandbox").isJsonObject()
            ? response.getAsJsonObject("sandbox") : new JsonObject();
        String expectedCwd = layout.cwd().toString();
        if (id == null || !"never".equals(str(response, "approvalPolicy"))
                || !"readOnly".equals(str(sandbox, "type"))
                || !"openai".equals(str(response, "modelProvider"))
                || !"openai".equals(str(thread, "modelProvider"))
                || !expectedCwd.equals(str(response, "cwd"))
                || !expectedCwd.equals(str(thread, "cwd"))
                || !emptyArrayOrDefault(response, "runtimeWorkspaceRoots") || !emptyArrayOrDefault(response, "instructionSources")
                || (sandbox.has("networkAccess") && sandbox.get("networkAccess").getAsBoolean()))
            throw new CodexIsolation.IncompatibleException("Codex thread did not confirm the approved policy");
        threadId = id; project = request.project(); session = request.session();
    }

    private JsonObject turnParams(AgentRequest request) throws IOException {
        JsonObject params = new JsonObject(); params.addProperty("threadId", threadId);
        params.addProperty("cwd", CodexIsolation.prepare(instanceDirectory).cwd().toString());
        params.add("runtimeWorkspaceRoots", new JsonArray());
        params.add("environments", new JsonArray()); params.add("input", input(request));
        params.addProperty("approvalPolicy", "never"); params.add("outputSchema", new PlanResponseCodec().outputSchema());
        return params;
    }

    private JsonArray input(AgentRequest request) throws IOException {
        JsonArray input = new JsonArray(); JsonObject text = new JsonObject(); text.addProperty("type", "text");
        text.addProperty("text", contextMessage(request)); input.add(text);
        long total = 0;
        for (Path path : request.images()) {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw new IOException("Selected image is unavailable");
            long size = Files.size(path); total = Math.addExact(total, size);
            if (size > 4_194_304 || total > 12_582_912) throw new IOException("Selected images exceed the size limit");
            String mime = Files.probeContentType(path);
            if (mime == null || !Set.of("image/png", "image/jpeg", "image/webp").contains(mime)) throw new IOException("Unsupported selected image type");
            JsonObject image = new JsonObject(); image.addProperty("type", "image");
            image.addProperty("url", "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path))); input.add(image);
        }
        return input;
    }

    static String contextMessage(AgentRequest request) throws IOException {
        WorldSnapshot snapshot = request.context();
        if ((long) snapshot.known().size() + snapshot.unknown().size() > PlanValidator.MAX_TARGETS)
            throw new IOException("World snapshot exceeds the context limit");
        JsonObject root = new JsonObject(); root.addProperty("project", request.project().toString());
        root.addProperty("session", request.session().toString()); root.addProperty("contextRevision", request.contextRevision());
        root.addProperty("message", request.text());
        JsonObject bounds = new JsonObject(); bounds.add("min", cell(snapshot.bounds().min())); bounds.add("max", cell(snapshot.bounds().max())); root.add("bounds", bounds);
        root.addProperty("knownCellCount", snapshot.known().size());
        root.addProperty("unknownCellCount", snapshot.unknown().size());
        root.add("voxelMap", JsonParser.parseString(WorldSnapshotGeometry.encode(snapshot)));
        root.add("acceptedPlan", request.acceptedPlan().map(CodexProcess::planJson).orElse(JsonNull.INSTANCE));
        String guidance = "Reply with the schema-constrained JSON object. Use only facts in this bounded snapshot; it is not a complete world scan. "
            + "The voxelMap palette maps short codes to exact block states; rows are ordered by ascending absolute Y then Z, and X cells run left-to-right from bounds.min.x. Runs are count*code; '?' is unknown and '.' was not captured. "
            + "Proposal coordinates are RELATIVE to bounds.min: world coordinate = bounds.min + proposal coordinate. Respect current occupied cells unless the user asks to replace/remove them. "
            + "Design for a coherent composition: establish a readable silhouette and proportions, use a restrained palette with clear structural/accent roles, keep circulation usable, and provide structural supports for roofs and overhangs. Preserve intentional existing features and ask before destructive terrain changes. "
            + "The plan schema contains full block states only: block-entity NBT, inventories, storage contents and machine configuration are unsupported. Do not propose containers or modded blocks that require such data; use a state-only alternative or return plan=null and explain the limitation. "
            + "Image attachments, when present, show visual appearance only; voxelMap is authoritative for occupancy and exact states, and one screenshot is not a view of every side. "
            + "Within each stage, assign every cell at most once: do not overlap block/fill operations or assign different states to the same cell. If unsure, return plan=null and explain. "
            + "Before finalizing a non-empty plan, call inspect_build_plan with the complete plan object. Review its isometric schematic and exact local inventory-model appearance cues, then make at most one critique-driven revision; if revised, inspect that final revision once. Never claim inspection succeeded if the tool fails or is unavailable. This tool is read-only and does not render or alter the live world. "
            + "When you need a modded block, use the local read-only list_block_mods/search_blocks tools, then describe_block for one exact ID and its inventory-model image. Search pages contain at most 20 results. Do not invent registry IDs or properties.\n";
        String encoded = guidance + root;
        if (request.text().getBytes(StandardCharsets.UTF_8).length > 65_536
                || encoded.getBytes(StandardCharsets.UTF_8).length > 8 * 1024 * 1024)
            throw new IOException("Builder request context exceeds the size limit");
        return encoded;
    }

    private static JsonObject cell(Cell c) { JsonObject o = new JsonObject(); o.addProperty("x", c.x()); o.addProperty("y", c.y()); o.addProperty("z", c.z()); return o; }
    private static JsonElement planJson(BuildPlan plan) {
        JsonObject result = new JsonObject(); result.addProperty("project", plan.project().toString());
        result.addProperty("revision", plan.revision()); result.addProperty("contextRevision", plan.contextRevision());
        JsonArray stages = new JsonArray();
        for (BuildStage stage : plan.stages()) {
            JsonObject item = new JsonObject(); item.addProperty("id", stage.id()); item.addProperty("title", stage.title());
            JsonArray after = new JsonArray(); stage.after().forEach(after::add); item.add("after", after);
            JsonArray targets = new JsonArray();
            stage.targets().forEach((at, state) -> {
                JsonObject target = new JsonObject(); target.add("at", cell(at));
                JsonObject encoded = new JsonObject(); encoded.addProperty("id", state.id());
                JsonObject properties = new JsonObject(); state.properties().forEach(properties::addProperty);
                encoded.add("properties", properties); target.add("state", encoded); targets.add(target);
            });
            item.add("targets", targets); stages.add(item);
        }
        result.add("stages", stages); return result;
    }

    private void awaitTurn(AgentRequest request, Consumer<AgentEvent> events, long run) throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TURN_MS); String output = null;
        Set<String> toolCallIds = new HashSet<>();
        long responseBytes = 0;
        while (System.nanoTime() < until && !closed && !cancelled) {
            JsonObject frame = nextDeferred();
            if (frame == null) frame = nextFrame(frames, 250);
            if (frame == null) continue;
            responseBytes += frame.toString().getBytes(StandardCharsets.UTF_8).length + 1L;
            if (responseBytes > PlanResponseCodec.MAX_RESPONSE_BYTES) throw new IOException("Codex response exceeds 16 MiB");
            if (frame.has("method") && frame.has("id")) {
                if ("item/tool/call".equals(str(frame, "method")) && dynamicTools != null) answerToolCall(frame, run, toolCallIds, events);
                else rejectServerRequest(frame);
                continue;
            }
            String method = str(frame, "method");
            if ("item/completed".equals(method) && frame.has("params") && frame.get("params").isJsonObject()) {
                JsonObject p = frame.getAsJsonObject("params"); JsonObject item = p.has("item") && p.get("item").isJsonObject() ? p.getAsJsonObject("item") : new JsonObject();
                if (threadId.equals(str(p, "threadId")) && Objects.equals(turnId, str(p, "turnId"))
                        && "agentMessage".equals(str(item, "type"))) {
                    output = str(item, "text");
                    emit(events, new AgentEvent.TextDelta("Checking response and any build plan…"));
                }
            } else if ("turn/completed".equals(method) && frame.has("params") && frame.get("params").isJsonObject()) {
                JsonObject p = frame.getAsJsonObject("params");
                JsonObject completedTurn = p.has("turn") && p.get("turn").isJsonObject() ? p.getAsJsonObject("turn") : new JsonObject();
                if (!threadId.equals(str(p, "threadId")) || !Objects.equals(turnId, str(completedTurn, "id"))) continue;
                if (!"completed".equals(str(completedTurn, "status"))) {
                    String turnStatus = str(completedTurn, "status");
                    emit(events, new AgentEvent.Failed("Codex turn ended with status: "
                        + (turnStatus == null ? "unknown" : turnStatus)));
                    return;
                }
                if (output == null) throw new IOException("Codex completed without a final structured message");
                final PlanResponseCodec.Response result;
                try {
                    result = new PlanResponseCodec().decode(output, request, stateExists);
                } catch (IOException invalidProposal) {
                    if (cancelled || closed || run != generation) return;
                    emit(events, new AgentEvent.Failed("Builder rejected the proposal: " + sanitize(invalidProposal.getMessage())));
                    return;
                }
                if (cancelled || closed || run != generation) return;
                if (result.plan().isPresent()) {
                    BuilderDynamicTools.Review review = dynamicTools == null
                        ? new BuilderDynamicTools.Review(false, false, "Plan preview tool is unavailable")
                        : dynamicTools.review(result.plan().orElseThrow());
                    emit(events, new AgentEvent.Proposal(result.plan().orElseThrow(), review.succeeded(), review.failure()));
                }
                emit(events, new AgentEvent.Completed(result.message())); return;
            }
        }
        if (!cancelled && !closed) throw new TimeoutException("Codex request timed out");
    }

    private void answerToolCall(JsonObject frame, long run, Set<String> seenCallIds, Consumer<AgentEvent> events) throws IOException {
        JsonObject params = frame.has("params") && frame.get("params").isJsonObject() ? frame.getAsJsonObject("params") : new JsonObject();
        JsonObject result;
        String method = str(params, "tool");
        String callId = str(params, "callId");
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments") : null;
        // In the pinned App Server schema item/tool/call has no itemId: callId is the
        // protocol correlation token for the active tool item, scoped by threadId+turnId.
        boolean matching = threadId != null && threadId.equals(str(params, "threadId"))
                && turnId != null && turnId.equals(str(params, "turnId")) && !cancelled && !closed && run == generation
                && callId != null && !callId.isBlank() && callId.length() <= 256
                && seenCallIds.size() < 32 && seenCallIds.add(callId);
        if (!matching) result = toolFailure("Tool call is stale or does not match the active Builder turn.");
        else {
            emit(events, new AgentEvent.TextDelta(toolProgress(method)));
            result = dynamicTools.dispatch(method, arguments, () -> !cancelled && !closed && run == generation
                    && threadId != null && threadId.equals(str(params, "threadId"))
                    && turnId != null && turnId.equals(str(params, "turnId")));
            if (result.has("success") && result.get("success").isJsonPrimitive()
                    && !result.get("success").getAsBoolean())
                emit(events, new AgentEvent.TextDelta("inspect_build_plan".equals(method)
                    ? "Plan inspection failed; the proposal will be marked unreviewed."
                    : "Block catalog lookup failed; Codex will report the limitation."));
        }
        JsonObject response = new JsonObject(); response.add("id", frame.get("id")); response.add("result", result); write(response + "\n");
    }
    private static String toolProgress(String name) {
        return switch (name) {
            case "list_block_mods", "search_blocks" -> "Searching the local block catalog…";
            case "describe_block" -> "Checking the block ID and appearance…";
            case "inspect_build_plan" -> "Validating the plan and rendering a local schematic…";
            default -> "Running a local Builder lookup…";
        };
    }
    private static JsonObject toolFailure(String message) {
        JsonObject item = new JsonObject(); item.addProperty("type", "inputText"); item.addProperty("text", message);
        JsonArray content = new JsonArray(); content.add(item); JsonObject result = new JsonObject(); result.addProperty("success", false); result.add("contentItems", content); return result;
    }

    private void checkImageModel(Consumer<AgentEvent> events) throws Exception {
        JsonObject response = rpc("model/list", new JsonObject(), STARTUP_MS, events);
        JsonArray models = response.has("data") && response.get("data").isJsonArray() ? response.getAsJsonArray("data") : new JsonArray();
        boolean supported = false;
        for (JsonElement entry : models) if (entry.isJsonObject()) {
            JsonObject model = entry.getAsJsonObject();
            if (model.has("isDefault") && model.get("isDefault").getAsBoolean() && model.has("inputModalities")
                    && model.getAsJsonArray("inputModalities").contains(new JsonPrimitive("image"))) supported = true;
        }
        if (!supported) throw new CodexIsolation.IncompatibleException("Default Codex model does not support image input");
    }

    private JsonObject rpc(String method, JsonObject params, long timeout, Consumer<AgentEvent> events) throws Exception {
        long id = ids.getAndIncrement(); write(CodexProtocol.encodeRequest(id, method, params));
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        var notifications = new ArrayList<JsonObject>();
        while (System.nanoTime() < until) {
            JsonObject frame = nextFrame(frames, 200); if (frame == null) continue;
            if (frame.has("method") && !frame.has("id")) { notifications.add(frame); continue; }
            if (frame.has("id") && !frame.has("method")) {
                if (numeric(frame.get("id")) == id) {
                    if (frame.has("error")) {
                        JsonObject error = frame.get("error").isJsonObject() ? frame.getAsJsonObject("error") : new JsonObject();
                        String message = str(error, "message");
                        throw new IOException("Codex rejected " + method + (message == null ? "" : ": " + sanitize(message)));
                    }
                    synchronized (deferred) {
                        if (deferred.size() + notifications.size() > 128) throw new IOException("Codex notification queue is full");
                        deferred.addAll(notifications);
                    }
                    return frame.has("result") && frame.get("result").isJsonObject() ? frame.getAsJsonObject("result") : new JsonObject();
                }
            } else if (frame.has("method") && frame.has("id")) rejectServerRequest(frame);
        }
        throw new TimeoutException("Codex startup request timed out");
    }

    private JsonObject nextDeferred() {
        synchronized (deferred) { return deferred.pollFirst(); }
    }

    private static JsonObject nextFrame(BlockingQueue<FrameRead> queue, long waitMs) throws Exception {
        FrameRead read = queue.poll(waitMs, TimeUnit.MILLISECONDS);
        if (read == null) return null;
        if (read.failure() != null) throw read.failure();
        return read.frame();
    }

    private void rejectServerRequest(JsonObject frame) throws IOException {
        JsonObject error = new JsonObject(); error.addProperty("code", -32601); error.addProperty("message", "Client request is not supported");
        JsonObject response = new JsonObject(); response.add("id", frame.get("id")); response.add("error", error); write(response + "\n");
    }
    private void notifyServer(String method, JsonObject params) throws IOException {
        JsonObject notification = new JsonObject(); notification.addProperty("method", method); notification.add("params", params); write(notification + "\n");
    }
    private synchronized void write(String value) throws IOException {
        if (process == null || !process.isAlive()) throw new IOException("Codex process is not running");
        process.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
    }
    private void readFrames(Process p, BlockingQueue<FrameRead> queue) {
        IOException failure;
        try (InputStream in = p.getInputStream()) {
            JsonObject frame;
            while ((frame = CodexProtocol.readFrame(in)) != null) {
                if (!queue.offer(FrameRead.frame(frame), 2, TimeUnit.SECONDS)) throw new IOException("Codex frame queue is full");
            }
            failure = new EOFException("Codex app-server closed its output");
        } catch (IOException e) { failure = e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); failure = new IOException("Codex frame reader interrupted", e); }
        queue.clear(); queue.offer(FrameRead.failure(failure));
        synchronized (this) {
            if (process == p) {
                process = null; threadId = null; turnId = null; project = null; session = null;
                synchronized (deferred) { deferred.clear(); }
                set(Connection.DISCONNECTED, status.login(), "Codex connection failed");
                p.destroy();
            }
        }
    }
    private static void drainStderr(Process p) {
        try (var in = p.getErrorStream()) { byte[] bytes = new byte[512]; int n; while ((n = in.read(bytes)) >= 0) { /* diagnostics are deliberately not forwarded */ } }
        catch (IOException ignored) { }
    }
    @Override public void cancel() {
        cancelled = true;
        if (process == null || !process.isAlive()) return;
        try {
            if (threadId != null && turnId != null) {
                JsonObject p = new JsonObject(); p.addProperty("threadId", threadId); p.addProperty("turnId", turnId);
                write(CodexProtocol.encodeRequest(ids.getAndIncrement(), "turn/interrupt", p));
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy();
            } else process.destroy();
        } catch (Exception ignored) { process.destroy(); }
    }
    @Override public void login(Consumer<AgentEvent> events) {
        if (closed) { emit(events, new AgentEvent.Failed("Codex client is closed")); return; }
        cancelled = false; long run = ++generation;
        set(Connection.STARTING, status.login(), "Checking isolated Codex connection");
        emit(events, new AgentEvent.Status(Connection.STARTING, status.login(), status.detail(), null, null));
        worker.execute(() -> {
            try {
                ensureReady(events);
                if (!current(run)) return;
                if (status.login() == Login.AUTHENTICATED) { emit(events, new AgentEvent.Status(Connection.READY, Login.AUTHENTICATED, "Already signed in", null, null)); return; }
                JsonObject p = new JsonObject(); p.addProperty("type", "chatgptDeviceCode");
                JsonObject result = rpc("account/login/start", p, STARTUP_MS, events);
                if (!current(run)) return;
                String loginId = str(result, "loginId");
                if (loginId == null || loginId.isBlank()) throw new IOException("Codex did not return a device login ID");
                String url = str(result, "verificationUrl"); String code = str(result, "userCode");
                set(Connection.READY, Login.PENDING, "Complete official Codex sign in");
                emit(events, new AgentEvent.Status(Connection.READY, Login.PENDING, status.detail(), url == null ? null : URI.create(url), code));
                JsonObject completion = awaitLoginCompletion(loginId, run);
                if (!current(run)) return;
                if (!completion.has("success") || !completion.get("success").isJsonPrimitive()
                        || !completion.getAsJsonPrimitive("success").isBoolean()
                        || !completion.get("success").getAsBoolean()) {
                    String error = str(completion, "error");
                    String detail = "Codex login failed" + (error == null || error.isBlank() ? "; restart sign in and try again" : ": " + sanitize(error));
                    set(Connection.READY, Login.REQUIRED, detail);
                    emit(events, new AgentEvent.Status(Connection.READY, Login.REQUIRED, detail, null, null));
                    emit(events, new AgentEvent.Failed(detail));
                    return;
                }

                verifyPolicy(events);
                if (!current(run)) return;
                if (status.login() != Login.AUTHENTICATED) {
                    String detail = "Codex reported login complete, but the isolated profile is still signed out; restart sign in";
                    set(Connection.READY, Login.REQUIRED, detail);
                    emit(events, new AgentEvent.Status(Connection.READY, Login.REQUIRED, detail, null, null));
                    emit(events, new AgentEvent.Failed(detail));
                    return;
                }
                emit(events, new AgentEvent.Status(Connection.READY, Login.AUTHENTICATED, "Isolated Codex is ready", null, null));
            } catch (LoginTimeoutException e) {
                if (!current(run)) return;
                String detail = "Codex sign in timed out; restart sign in to try again";
                set(Connection.READY, Login.REQUIRED, detail);
                emit(events, new AgentEvent.Status(Connection.READY, Login.REQUIRED, detail, null, null));
                emit(events, new AgentEvent.Failed(detail));
            } catch (CodexIsolation.IncompatibleException e) {
                if (!current(run)) return;
                set(Connection.INCOMPATIBLE, status.login(), safe(e));
                emit(events, new AgentEvent.Status(Connection.INCOMPATIBLE, status.login(), safe(e), null, null));
                emit(events, new AgentEvent.Failed(safe(e)));
            } catch (Exception e) {
                if (!current(run)) return;
                set(Connection.DISCONNECTED, status.login(), safe(e));
                emit(events, new AgentEvent.Status(Connection.DISCONNECTED, status.login(), safe(e), null, null));
                emit(events, new AgentEvent.Failed(safe(e)));
            }
        });
    }
    private JsonObject awaitLoginCompletion(String loginId, long run) throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOGIN_MS);
        while (current(run) && System.nanoTime() < until) {
            JsonObject frame = nextDeferred();
            if (frame == null) frame = nextFrame(frames, 200);
            if (frame == null) continue;
            if (frame.has("method") && frame.has("id")) { rejectServerRequest(frame); continue; }
            if (!"account/login/completed".equals(str(frame, "method")) || !frame.has("params") || !frame.get("params").isJsonObject()) continue;
            JsonObject params = frame.getAsJsonObject("params");
            if (loginId.equals(str(params, "loginId"))) return params;
        }
        if (!current(run)) throw new CancellationException("Codex sign in cancelled");
        throw new LoginTimeoutException();
    }
    private boolean current(long run) { return !cancelled && !closed && run == generation; }
    private static final class LoginTimeoutException extends TimeoutException { }
    private static long numeric(JsonElement e) { try { return e.getAsLong(); } catch (RuntimeException ex) { return Long.MIN_VALUE; } }
    private static boolean emptyArrayOrDefault(JsonObject object, String key) {
        if (!object.has(key)) return true;
        JsonElement value = object.get(key);
        return value.isJsonArray() && value.getAsJsonArray().isEmpty();
    }
    private static JsonObject requiredObject(JsonObject o, String k) throws IOException { if (!o.has(k) || !o.get(k).isJsonObject()) throw new IOException("Malformed Codex response"); return o.getAsJsonObject(k); }
    private static String str(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() && o.getAsJsonPrimitive(k).isString() ? o.get(k).getAsString() : null; }
    private synchronized void set(Connection c, Login l, String d) { status = new Status(c, l, d); }
    private static String safe(Exception e) { String m = e.getMessage(); return m == null || m.isBlank() ? "Codex transport failed" : sanitize(m); }
    private static String sanitize(String message) {
        return message.replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[redacted]")
                .replaceAll("(?i)(api[_-]?key|(?:access|refresh|id)?[_-]?token|authorization|client[_-]?secret|usercode)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]");
    }
    private static void emit(Consumer<AgentEvent> c, AgentEvent e) { try { c.accept(e); } catch (RuntimeException ignored) { } }
    @Override public synchronized void close() {
        closed = true; cancelled = true;
        if (process != null) { process.destroy(); try { if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); } }
        worker.shutdownNow(); set(Connection.DISCONNECTED, status.login(), "Closed");
    }
}

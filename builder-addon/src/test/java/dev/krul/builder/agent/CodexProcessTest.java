package dev.krul.builder.agent;

import com.google.gson.*;
import dev.krul.builder.model.*;
import dev.krul.builder.minecraft.MinecraftPlanRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CodexProcessTest {
    @TempDir Path temporary;

    @Test void contextIncludesExactGeometryAndConciseDesignQualityRubric() throws Exception {
        String context = CodexProcess.contextMessage(request());
        assertTrue(context.contains("bounds.min + proposal coordinate"));
        assertTrue(context.contains("unknown"));
        assertTrue(context.contains("not a complete world scan"));
        assertTrue(context.contains("silhouette"));
        assertTrue(context.contains("circulation"));
        assertTrue(context.contains("structural supports"));
        assertTrue(context.contains("restrained palette"));
        assertTrue(context.contains("structural supports"));
        assertTrue(context.contains("inspect_build_plan"));
        assertTrue(context.contains("at most one critique-driven revision"));
    }

    @Test void startupPinsPolicyBeforeReadingEffectiveConfiguration() throws Exception {
        var server = new FakeServer(false);
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var done = new CountDownLatch(1);
        try {
            client.start(request(), event -> {
                if (event instanceof AgentEvent.Completed || event instanceof AgentEvent.Failed) done.countDown();
            });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            Map<String, String> overrides = new HashMap<>();
            for (int i = 0; i + 1 < server.startupCommand.size(); i++) {
                if (server.startupCommand.get(i).equals("-c")) {
                    String[] entry = server.startupCommand.get(++i).split("=", 2);
                    overrides.put(entry[0], entry[1]);
                }
            }
            assertEquals("\"never\"", overrides.get("approval_policy"));
            assertEquals("\"read-only\"", overrides.get("sandbox_mode"));
        } finally { client.close(); }
    }

    @Test void deviceLoginWaitsForMatchingCompletionAndRevalidatesAccount() throws Exception {
        var server = new FakeServer(false);
        server.accountAuthenticated = false;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var authenticated = new CountDownLatch(1);
        try {
            client.login(event -> { events.add(event); if (event instanceof AgentEvent.Status s && s.login() == AgentClient.Login.AUTHENTICATED) authenticated.countDown(); });
            assertTrue(authenticated.await(5, TimeUnit.SECONDS), events.toString());
            assertEquals(2, server.accountReads);
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Status s && s.login() == AgentClient.Login.PENDING && "ABCD-1234".equals(s.userCode())));
            assertEquals(1, events.stream().filter(e -> e instanceof AgentEvent.Status s && s.login() == AgentClient.Login.AUTHENTICATED).count());
            assertEquals(0, events.stream().filter(AgentEvent.Failed.class::isInstance).count());
        } finally { client.close(); }
    }

    @Test void failedDeviceLoginReportsServerReasonAndReturnsToRequired() throws Exception {
        var server = new FakeServer(false);
        server.accountAuthenticated = false;
        server.loginCompletionSuccess = false;
        server.loginCompletionError = "Device authorization expired";
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var failed = new CountDownLatch(1);
        try {
            client.login(event -> { events.add(event); if (event instanceof AgentEvent.Failed) failed.countDown(); });
            assertTrue(failed.await(5, TimeUnit.SECONDS), events.toString());
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Failed f && f.message().contains("Device authorization expired")));
            assertEquals(AgentClient.Login.REQUIRED, client.status().login());
            assertEquals(AgentClient.Connection.READY, client.status().connection());
        } finally { client.close(); }
    }

    @Test void loginRpcFailureDisconnectsAndRedactsCredentialText() throws Exception {
        var server = new FakeServer(false);
        server.accountAuthenticated = false;
        server.rpcErrorMethod = "account/login/start";
        server.rpcErrorMessage = "Authorization: Bearer do-not-leak";
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var failed = new CountDownLatch(1);
        try {
            client.login(event -> { events.add(event); if (event instanceof AgentEvent.Failed) failed.countDown(); });
            assertTrue(failed.await(5, TimeUnit.SECONDS), events.toString());
            assertEquals(AgentClient.Connection.DISCONNECTED, client.status().connection());
            assertTrue(events.stream().filter(AgentEvent.Failed.class::isInstance).map(AgentEvent.Failed.class::cast)
                    .anyMatch(e -> e.message().contains("Codex rejected account/login/start") && e.message().contains("[redacted]")));
            assertFalse(events.toString().contains("do-not-leak"));
        } finally { client.close(); }
    }

    @Test void cancelledLoginRetryAuthenticatesAndIgnoresStaleCompletion() throws Exception {
        var server = new FakeServer(false);
        server.accountAuthenticated = false;
        server.delayLoginCompletion = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var firstEvents = new CopyOnWriteArrayList<AgentEvent>();
        var secondEvents = new CopyOnWriteArrayList<AgentEvent>();
        var authenticated = new CountDownLatch(1);
        try {
            client.login(firstEvents::add);
            assertTrue(awaitMethod(server, "account/login/start"));
            FakeProcess cancelledProcess = server.process;
            client.cancel();

            server.delayLoginCompletion = false;
            client.login(event -> { secondEvents.add(event); if (event instanceof AgentEvent.Status s && s.login() == AgentClient.Login.AUTHENTICATED) authenticated.countDown(); });
            assertTrue(authenticated.await(5, TimeUnit.SECONDS), secondEvents.toString());

            cancelledProcess.send(FakeServer.loginCompleted("login-1", true, null));
            Thread.sleep(100);
            assertFalse(firstEvents.stream().anyMatch(e -> e instanceof AgentEvent.Status s && s.login() == AgentClient.Login.AUTHENTICATED));
            assertEquals(1, secondEvents.stream().filter(e -> e instanceof AgentEvent.Status s && s.login() == AgentClient.Login.AUTHENTICATED).count());
        } finally { client.close(); }
    }

    private static boolean awaitMethod(FakeServer server, String method) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!server.methods.contains(method) && System.nanoTime() < deadline) Thread.sleep(10);
        return server.methods.contains(method);
    }

    @Test void refusesUnsafeEffectiveConfigBeforeCreatingThreadOrSendingTurn() throws Exception {
        var server = new FakeServer(true);
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Failed) done.countDown(); });
        assertTrue(done.await(5, TimeUnit.SECONDS), events + " methods=" + server.methods);
        assertTrue(server.methods.contains("config/read"));
        assertFalse(server.methods.contains("thread/start"));
        assertFalse(server.methods.contains("turn/start"));
        assertTrue(events.stream().anyMatch(AgentEvent.Failed.class::isInstance));
        client.close();
    }

    @Test void discussionCanCompleteWithoutExposingStructuredJsonAsChat() throws Exception {
        var server = new FakeServer(false);
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Completed) done.countDown(); });
        assertTrue(done.await(5, TimeUnit.SECONDS), events + " methods=" + server.methods);
        assertTrue(server.methods.contains("thread/start"));
        assertTrue(server.methods.contains("turn/start"));
        JsonObject threadParams = server.threadParams;
        assertEquals(Boolean.FALSE, threadParams.get("allowProviderModelFallback").getAsBoolean());
        assertEquals(server.appCwd.toString(), threadParams.get("cwd").getAsString());
        assertTrue(threadParams.getAsJsonArray("runtimeWorkspaceRoots").isEmpty());
        assertEquals(server.appCwd.toString(), server.turnParams.get("cwd").getAsString());
        assertTrue(server.turnParams.getAsJsonArray("runtimeWorkspaceRoots").isEmpty());
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Completed c && c.message().equals("A discussion answer")));
        assertFalse(events.stream().anyMatch(AgentEvent.Proposal.class::isInstance));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta d && d.text().contains("working")));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta d && d.text().contains("Checking response")));
        String prompt = server.turnParams.getAsJsonArray("input").get(0).getAsJsonObject().get("text").getAsString();
        assertTrue(prompt.contains("block-entity NBT"));
        assertTrue(prompt.contains("plan=null"));
        client.close();
    }

    @Test void registersAndAnswersOnlyCurrentDynamicToolCall() throws Exception {
        var server = new FakeServer(false); server.sendCatalogCall = true; server.sendStaleCatalogCall = true;
        var calls = new CopyOnWriteArrayList<String>();
        var tools = new BuilderDynamicTools((name, args, current) -> { calls.add(name); return textToolResult("local catalog page"); });
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch, tools);
        var done = new CountDownLatch(1);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        try {
            client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Completed) done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(4, server.threadParams.getAsJsonArray("dynamicTools").size());
            assertEquals(List.of("list_block_mods"), calls);
            assertEquals(Boolean.TRUE, server.toolReplies.get(881).getAsJsonObject("result").get("success").getAsBoolean());
            assertEquals(Boolean.FALSE, server.toolReplies.get(882).getAsJsonObject("result").get("success").getAsBoolean());
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta d && d.text().contains("block catalog")));
        } finally { client.close(); }
    }

    @Test void reportsLocalCatalogToolFailureWithoutExposingRawToolOutput() throws Exception {
        var server = new FakeServer(false); server.sendCatalogCall = true;
        var tools = new BuilderDynamicTools((name, args, current) -> { throw new IOException("local path secret"); });
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch, tools);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        try {
            client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Completed) done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS), events.toString());
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta d && d.text().contains("lookup failed")));
            assertFalse(events.toString().contains("local path secret"));
            assertEquals(Boolean.FALSE, server.toolReplies.get(881).getAsJsonObject("result").get("success").getAsBoolean());
        } finally { client.close(); }
    }

    @Test void reportsAnInterruptedTurnAsFailureInsteadOfACompletedResponse() throws Exception {
        var server = new FakeServer(false); server.turnStatus = "interrupted";
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        try {
            client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Failed) done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS), events.toString());
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Failed f && f.message().contains("interrupted")));
            assertFalse(events.stream().anyMatch(AgentEvent.Completed.class::isInstance));
        } finally { client.close(); }
    }

    @Test void invalidContainerProposalKeepsCodexConnectionReady() throws Exception {
        var server = new FakeServer(false);
        server.agentMessageText = "{\"message\":\"Here is the storage plan\",\"plan\":{}}";
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        try {
            client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Failed) done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS), events.toString());
            assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Failed f && f.message().contains("rejected the proposal")), events.toString());
            assertEquals(AgentClient.Connection.READY, client.status().connection());
            assertFalse(events.stream().anyMatch(AgentEvent.Proposal.class::isInstance));
        } finally { client.close(); }
    }

    private static JsonObject textToolResult(String value) {
        JsonObject item = new JsonObject(); item.addProperty("type", "inputText"); item.addProperty("text", value);
        JsonArray items = new JsonArray(); items.add(item); JsonObject result = new JsonObject(); result.addProperty("success", true); result.add("contentItems", items); return result;
    }

    @Test void reusesTheOwnedProcessAndThreadForTheSameProjectSession() throws Exception {
        var server = new FakeServer(false);
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        UUID project = UUID.randomUUID(), session = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            var done = new CountDownLatch(1);
            client.start(request(project, session), event -> { if (event instanceof AgentEvent.Completed) done.countDown(); });
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
        assertEquals(1, server.methods.stream().filter("thread/start"::equals).count());
        assertEquals(1, server.versionChecks);
        client.close();
    }

    @Test void ignoresWrongThreadAndTurnNotificationsAndRejectsServerToolRequests() throws Exception {
        var server = new FakeServer(false);
        server.foreignNotifications = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        var done = new CountDownLatch(1);
        client.start(request(), event -> { events.add(event); if (event instanceof AgentEvent.Completed) done.countDown(); });
        assertTrue(done.await(5, TimeUnit.SECONDS), events.toString());
        assertTrue(server.unsupportedRequestRejected);
        assertEquals(1, events.stream().filter(AgentEvent.Completed.class::isInstance).count());
        client.close();
    }

    @Test void cancellationSuppressesLateStructuredProposal() throws Exception {
        var server = new FakeServer(false);
        server.delayCompletion = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var events = new CopyOnWriteArrayList<AgentEvent>();
        client.start(request(), events::add);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!server.methods.contains("turn/start") && System.nanoTime() < deadline) Thread.sleep(10);
        Thread.sleep(50);
        client.cancel();
        server.sendLateProposal();
        Thread.sleep(150);
        assertFalse(events.stream().anyMatch(AgentEvent.Proposal.class::isInstance));
        client.close();
    }

    @Test void cancelledStartupCannotOpenThreadAndLateFramesCannotEnterNextRequest() throws Exception {
        var server = new FakeServer(false);
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        client.start(request(), event -> { });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!server.methods.contains("config/read") && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(server.methods.contains("config/read"));
        FakeProcess cancelledProcess = server.process;
        client.cancel();

        var completed = new CountDownLatch(1);
        client.start(request(), event -> { if (event instanceof AgentEvent.Completed) completed.countDown(); });
        cancelledProcess.send(FakeServer.response(new JsonPrimitive(2), FakeServer.configReadResult(false)));
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(1, server.methods.stream().filter("thread/start"::equals).count());
        assertEquals(2, server.versionChecks);
        client.close();
    }

    @Test void eofDuringStartupFailsPromptlyAndDoesNotReuseTheDeadProcess() throws Exception {
        var server = new FakeServer(false);
        server.failAfterMethod = "config/read";
        server.failureIsEof = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var firstFailed = new CountDownLatch(1);
        client.start(request(), event -> { if (event instanceof AgentEvent.Failed) firstFailed.countDown(); });
        assertTrue(firstFailed.await(2, TimeUnit.SECONDS));
        assertFalse(server.methods.contains("thread/start"));

        server.failAfterMethod = null;
        var completed = new CountDownLatch(1);
        client.start(request(), event -> { if (event instanceof AgentEvent.Completed) completed.countDown(); });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(2, server.versionChecks);
        client.close();
    }

    @Test void malformedFrameDuringTurnFailsPromptlyAndDoesNotReuseTheDeadProcess() throws Exception {
        var server = new FakeServer(false);
        server.failAfterMethod = "turn/start";
        server.delayCompletion = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var firstFailed = new CountDownLatch(1);
        var firstEvents = new CopyOnWriteArrayList<AgentEvent>();
        client.start(request(), event -> { firstEvents.add(event); if (event instanceof AgentEvent.Failed) firstFailed.countDown(); });
        assertTrue(firstFailed.await(2, TimeUnit.SECONDS), firstEvents.toString());

        server.failAfterMethod = null;
        server.delayCompletion = false;
        var completed = new CountDownLatch(1);
        client.start(request(), event -> { if (event instanceof AgentEvent.Completed) completed.countDown(); });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(2, server.versionChecks);
        client.close();
    }

    @Test void nonemptyEffectiveThreadRootsAreRejectedBeforeTurnStart() throws Exception {
        var server = new FakeServer(false);
        server.responseHasWorkspaceRoot = true;
        var client = new CodexProcess(temporary, key -> true, "codex", server::launch);
        var failed = new CountDownLatch(1);
        client.start(request(), event -> { if (event instanceof AgentEvent.Failed) failed.countDown(); });
        assertTrue(failed.await(5, TimeUnit.SECONDS));
        assertFalse(server.methods.contains("turn/start"));
        client.close();
    }

    private AgentRequest request() {
        return request(UUID.randomUUID(), UUID.randomUUID());
    }
    private AgentRequest request(UUID project, UUID session) {
        var bounds = new Bounds(new Cell(0, 0, 0), new Cell(0, 0, 0));
        var world = new WorldSnapshot(session, 1, bounds, Map.of(), Set.of(new Cell(0, 0, 0)));
        return new AgentRequest(project, session, 1, "Explain the selection", world, List.of());
    }

    private static final class FakeServer {
        final Set<String> methods = ConcurrentHashMap.newKeySet();
        final boolean unsafe;
        volatile int versionChecks;
        volatile List<String> startupCommand = List.of();
        volatile Path appCwd;
        volatile boolean foreignNotifications, delayCompletion, unsupportedRequestRejected;
        volatile boolean accountAuthenticated = true, loginCompletionSuccess = true, delayLoginCompletion;
        volatile String loginCompletionError;
        volatile String agentMessageText = "{\"message\":\"A discussion answer\",\"plan\":null}";
        volatile String rpcErrorMethod, rpcErrorMessage;
        volatile int accountReads, loginStarts;
        volatile boolean responseHasWorkspaceRoot;
        volatile boolean sendCatalogCall;
        volatile boolean sendStaleCatalogCall;
        final Map<Integer,JsonObject> toolReplies = new ConcurrentHashMap<>();
        volatile boolean omitDefaultThreadFields = true;
        volatile String failAfterMethod;
        volatile String turnStatus = "completed";
        volatile boolean failureIsEof;
        volatile JsonObject threadParams, turnParams;
        volatile FakeProcess process;
        FakeServer(boolean unsafe) { this.unsafe = unsafe; }
        Process launch(List<String> command, Path cwd, Map<String, String> env) throws IOException {
            if (command.contains("--version")) { versionChecks++; return FakeProcess.completed("codex-cli 0.153.4\n"); }
            startupCommand = List.copyOf(command);
            appCwd = cwd;
            process = new FakeProcess(line -> handle(line));
            return process;
        }
        private String handle(String line) {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            String method = request.has("method") ? request.get("method").getAsString() : "";
            if (method.isEmpty()) {
                if (request.has("error")) unsupportedRequestRejected = true;
                if (request.has("result") && request.has("id")) toolReplies.put(request.get("id").getAsInt(), request.deepCopy());
                return "";
            }
            methods.add(method);
            if (method.equals(rpcErrorMethod) && request.has("id")) return error(request.get("id"), rpcErrorMessage);
            if (method.equals(failAfterMethod) && process != null) {
                process.malformedAfterResponse = !failureIsEof;
                process.closeAfterResponse = failureIsEof;
            }
            if (!request.has("id")) return "";
            JsonElement id = request.get("id"); JsonObject result = new JsonObject();
            switch (method) {
                case "config/read" -> {
                    JsonObject policy = config(unsafe); result.add("config", policy); result.add("layers", new JsonArray());
                }
                case "account/read" -> {
                    accountReads++;
                    if (accountAuthenticated) { JsonObject account = new JsonObject(); account.addProperty("type", "chatgpt"); result.add("account", account); }
                }
                case "model/list" -> {
                    JsonObject model = new JsonObject(); model.addProperty("isDefault", true); JsonArray modalities = new JsonArray(); modalities.add("text"); modalities.add("image"); model.add("inputModalities", modalities);
                    JsonArray data = new JsonArray(); data.add(model); result.add("data", data);
                }
                case "account/login/start" -> {
                    int attempt = ++loginStarts;
                    result.addProperty("type", "chatgptDeviceCode"); result.addProperty("loginId", "login-" + attempt);
                    result.addProperty("verificationUrl", "https://example.test/device"); result.addProperty("userCode", "ABCD-1234");
                    if (!delayLoginCompletion && loginCompletionSuccess) accountAuthenticated = true;
                    String completion = delayLoginCompletion ? "" : loginCompleted("login-" + attempt, loginCompletionSuccess, loginCompletionError);
                    String foreign = loginCompleted("different-login", true, null);
                    return response(id, result) + foreign + completion;
                }
                case "thread/start" -> {
                    threadParams = request.getAsJsonObject("params").deepCopy();
                    JsonObject thread = new JsonObject(); thread.addProperty("id", "thread-1");
                    thread.addProperty("modelProvider", "openai"); thread.addProperty("cwd", appCwd.toString());
                    thread.addProperty("cliVersion", "0.153.4"); thread.addProperty("createdAt", 1);
                    thread.addProperty("ephemeral", false); thread.addProperty("preview", "Builder test");
                    thread.add("projectId", JsonNull.INSTANCE); thread.addProperty("sessionId", "session-1");
                    thread.addProperty("source", "appServer"); JsonObject threadStatus = new JsonObject();
                    threadStatus.addProperty("type", "idle"); thread.add("status", threadStatus);
                    thread.add("turns", new JsonArray()); thread.addProperty("updatedAt", 1);
                    result.add("thread", thread);
                    result.addProperty("approvalPolicy", "never"); result.addProperty("approvalsReviewer", "user");
                    JsonObject sandbox = new JsonObject(); sandbox.addProperty("type", "readOnly"); result.add("sandbox", sandbox);
                    result.addProperty("cwd", appCwd.toString()); result.addProperty("modelProvider", "openai");
                    result.addProperty("model", "test-model");
                    if (responseHasWorkspaceRoot) { JsonArray roots = new JsonArray(); roots.add(appCwd.toString()); result.add("runtimeWorkspaceRoots", roots); }
                    else if (!omitDefaultThreadFields) { result.add("runtimeWorkspaceRoots", new JsonArray()); result.add("instructionSources", new JsonArray()); }
                }
                case "turn/start" -> {
                    turnParams = request.getAsJsonObject("params").deepCopy();
                    JsonObject turn = new JsonObject(); turn.addProperty("id", "turn-1"); result.add("turn", turn);
                    if (delayCompletion) return response(id, result);
                    JsonObject item = new JsonObject(); item.addProperty("type", "agentMessage"); item.addProperty("text", agentMessageText);
                    JsonObject completed = new JsonObject(); completed.addProperty("threadId", "thread-1"); completed.addProperty("turnId", "turn-1"); completed.add("item", item);
                    JsonObject turnCompleted = new JsonObject(); turnCompleted.addProperty("id", "turn-1"); turnCompleted.addProperty("status", turnStatus);
                    JsonObject turnParams = new JsonObject(); turnParams.addProperty("threadId", "thread-1"); turnParams.add("turn", turnCompleted);
                    String foreign = "";
                    if (foreignNotifications) {
                        JsonObject wrongItem = completed.deepCopy(); wrongItem.addProperty("threadId", "other-thread");
                        JsonObject wrongTurn = new JsonObject(); wrongTurn.addProperty("id", "other-turn"); wrongTurn.addProperty("status", "completed");
                        JsonObject wrongTurnParams = new JsonObject(); wrongTurnParams.addProperty("threadId", "thread-1"); wrongTurnParams.add("turn", wrongTurn);
                        JsonObject tool = new JsonObject(); tool.addProperty("id", 990); tool.addProperty("method", "execCommand"); tool.add("params", new JsonObject());
                        foreign = tool + "\n" + notification("item/completed", wrongItem) + notification("turn/completed", wrongTurnParams);
                    }
                    String toolCall = "";
                    if (sendCatalogCall) {
                        JsonObject args = new JsonObject(); args.addProperty("page", 0);
                        JsonObject call = new JsonObject(); call.addProperty("callId", "tool-call-1"); call.addProperty("threadId", "thread-1"); call.addProperty("turnId", "turn-1"); call.addProperty("tool", "list_block_mods"); call.add("arguments", args);
                        JsonObject frame = new JsonObject(); frame.addProperty("id", 881); frame.addProperty("method", "item/tool/call"); frame.add("params", call); toolCall = frame + "\n";
                    }
                    if (sendStaleCatalogCall) {
                        JsonObject args = new JsonObject(); args.addProperty("page", 0);
                        JsonObject call = new JsonObject(); call.addProperty("callId", "tool-call-stale"); call.addProperty("threadId", "thread-1"); call.addProperty("turnId", "old-turn"); call.addProperty("tool", "list_block_mods"); call.add("arguments", args);
                        JsonObject frame = new JsonObject(); frame.addProperty("id", 882); frame.addProperty("method", "item/tool/call"); frame.add("params", call); toolCall += frame + "\n";
                    }
                    return response(id, result) + foreign + toolCall + notification("item/completed", completed) + notification("turn/completed", turnParams);
                }
                default -> { }
            }
            return response(id, result);
        }
        private static JsonObject config(boolean unsafe) {
            JsonObject result = new JsonObject(), features = new JsonObject();
            for (String k : List.of("hooks", "plugins", "apps", "multi_agent", "code_mode", "image_generation", "memories")) features.addProperty(k, unsafe && k.equals("plugins"));
            features.addProperty("code_mode_host", true);
            result.add("features", features); result.addProperty("web_search", "disabled");
            result.addProperty("approval_policy", "never"); result.addProperty("sandbox_mode", "read-only");
            result.add("notify", new JsonArray()); JsonObject skills = new JsonObject(); skills.addProperty("include_instructions", false);
            result.addProperty("project_doc_max_bytes", 0);
            JsonObject bundled = new JsonObject(); bundled.addProperty("enabled", false); skills.add("bundled", bundled); result.add("skills", skills);
            JsonObject orchestration = new JsonObject(), skillSettings = new JsonObject(); skillSettings.addProperty("enabled", false);
            orchestration.add("skills", skillSettings); result.add("orchestrator", orchestration); return result;
        }
        static JsonObject configReadResult(boolean unsafe) { JsonObject result = new JsonObject(); result.add("config", config(unsafe)); result.add("layers", new JsonArray()); return result; }
        static String response(JsonElement id, JsonObject result) { JsonObject frame = new JsonObject(); frame.add("id", id); frame.add("result", result); return frame + "\n"; }
        static String error(JsonElement id, String message) {
            JsonObject error = new JsonObject(); error.addProperty("code", -32000); error.addProperty("message", message);
            JsonObject frame = new JsonObject(); frame.add("id", id); frame.add("error", error); return frame + "\n";
        }
        private static String notification(String method, JsonObject params) { JsonObject frame = new JsonObject(); frame.addProperty("method", method); frame.add("params", params); return frame + "\n"; }
        static String loginCompleted(String loginId, boolean success, String error) {
            JsonObject params = new JsonObject(); params.addProperty("loginId", loginId); params.addProperty("success", success);
            if (error != null) params.addProperty("error", error);
            return notification("account/login/completed", params);
        }
        void sendLateProposal() throws IOException {
            if (process == null) return;
            JsonObject item = new JsonObject(); item.addProperty("type", "agentMessage");
            item.addProperty("text", "{\"message\":\"late\",\"plan\":{\"revision\":2,\"palette\":[{\"key\":\"stone\",\"state\":{\"id\":\"minecraft:stone\",\"properties\":[]}}],\"stages\":[{\"id\":\"late\",\"title\":\"Late\",\"after\":[],\"operations\":[{\"op\":\"block\",\"position\":[0,0,0],\"state\":\"stone\"}]}]}}");
            JsonObject completed = new JsonObject(); completed.addProperty("threadId", "thread-1"); completed.addProperty("turnId", "turn-1"); completed.add("item", item);
            JsonObject turn = new JsonObject(); turn.addProperty("id", "turn-1"); turn.addProperty("status", "completed");
            JsonObject params = new JsonObject(); params.addProperty("threadId", "thread-1"); params.add("turn", turn);
            process.send(notification("item/completed", completed) + notification("turn/completed", params));
        }
    }

    @FunctionalInterface private interface RequestHandler { String handle(String line); }
    private static final class FakeProcess extends Process {
        private final InputStream stdout;
        private final PipedOutputStream serverOut;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private final RequestHandler handler;
        private final boolean completed;
        private volatile boolean alive;
        volatile boolean malformedAfterResponse, closeAfterResponse;
        private FakeProcess(RequestHandler handler) throws IOException {
            this.handler = handler; this.completed = false; this.alive = true;
            var pipe = new PipedInputStream(); stdout = pipe; serverOut = new PipedOutputStream(pipe);
        }
        private FakeProcess(byte[] output) {
            this.handler = null; this.completed = true; this.alive = false; this.serverOut = null;
            this.stdout = new ByteArrayInputStream(output);
        }
        static FakeProcess completed(String text) { return new FakeProcess(text.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() {
            if (completed) return OutputStream.nullOutputStream();
            return new OutputStream() {
                private final ByteArrayOutputStream line = new ByteArrayOutputStream();
                @Override public synchronized void write(int b) throws IOException {
                    if (b == '\n') {
                        String response = handler.handle(line.toString(StandardCharsets.UTF_8)); line.reset();
                        if (!response.isEmpty()) {
                            serverOut.write(response.getBytes(StandardCharsets.UTF_8));
                            if (malformedAfterResponse) serverOut.write("{bad}\n".getBytes(StandardCharsets.UTF_8));
                            serverOut.flush();
                            if (closeAfterResponse) serverOut.close();
                        }
                    } else line.write(b);
                }
            };
        }
        void send(String frames) throws IOException {
            serverOut.write(frames.getBytes(StandardCharsets.UTF_8));
            serverOut.flush();
        }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return completed ? 0 : 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (completed) return true; Thread.sleep(Math.min(5, unit.toMillis(timeout))); return !alive;
        }
        @Override public int exitValue() { if (!completed) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { alive = false; }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public boolean isAlive() { return alive; }
    }
}

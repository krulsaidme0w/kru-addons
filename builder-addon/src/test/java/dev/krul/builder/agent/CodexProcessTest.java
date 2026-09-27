package dev.krul.builder.agent;

import com.google.gson.*;
import dev.krul.builder.model.*;
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
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Completed c && c.message().equals("A discussion answer")));
        assertFalse(events.stream().anyMatch(AgentEvent.Proposal.class::isInstance));
        assertFalse(events.stream().anyMatch(AgentEvent.TextDelta.class::isInstance));
        client.close();
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
        volatile Path appCwd;
        volatile boolean foreignNotifications, delayCompletion, unsupportedRequestRejected;
        volatile FakeProcess process;
        FakeServer(boolean unsafe) { this.unsafe = unsafe; }
        Process launch(List<String> command, Path cwd, Map<String, String> env) throws IOException {
            if (command.contains("--version")) { versionChecks++; return FakeProcess.completed("codex-cli 0.153.4\n"); }
            appCwd = cwd;
            process = new FakeProcess(line -> handle(line));
            return process;
        }
        private String handle(String line) {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            String method = request.has("method") ? request.get("method").getAsString() : "";
            if (method.isEmpty()) {
                if (request.has("error")) unsupportedRequestRejected = true;
                return "";
            }
            methods.add(method);
            if (!request.has("id")) return "";
            JsonElement id = request.get("id"); JsonObject result = new JsonObject();
            switch (method) {
                case "config/read" -> {
                    JsonObject policy = config(unsafe); result.add("config", policy); result.add("layers", new JsonArray());
                }
                case "account/read" -> {
                    JsonObject account = new JsonObject(); account.addProperty("type", "chatgpt"); result.add("account", account);
                }
                case "thread/start" -> {
                    JsonObject thread = new JsonObject(); thread.addProperty("id", "thread-1");
                    thread.addProperty("modelProvider", "openai"); thread.addProperty("cwd", appCwd.toString());
                    result.add("thread", thread);
                    result.addProperty("approvalPolicy", "never"); JsonObject sandbox = new JsonObject(); sandbox.addProperty("type", "readOnly"); result.add("sandbox", sandbox);
                    result.addProperty("cwd", appCwd.toString());
                }
                case "turn/start" -> {
                    JsonObject turn = new JsonObject(); turn.addProperty("id", "turn-1"); result.add("turn", turn);
                    if (delayCompletion) return response(id, result);
                    JsonObject item = new JsonObject(); item.addProperty("type", "agentMessage"); item.addProperty("text", "{\"message\":\"A discussion answer\",\"plan\":null}");
                    JsonObject completed = new JsonObject(); completed.addProperty("threadId", "thread-1"); completed.addProperty("turnId", "turn-1"); completed.add("item", item);
                    JsonObject turnCompleted = new JsonObject(); turnCompleted.addProperty("id", "turn-1"); turnCompleted.addProperty("status", "completed");
                    JsonObject turnParams = new JsonObject(); turnParams.addProperty("threadId", "thread-1"); turnParams.add("turn", turnCompleted);
                    String foreign = "";
                    if (foreignNotifications) {
                        JsonObject wrongItem = completed.deepCopy(); wrongItem.addProperty("threadId", "other-thread");
                        JsonObject wrongTurn = new JsonObject(); wrongTurn.addProperty("id", "other-turn"); wrongTurn.addProperty("status", "completed");
                        JsonObject wrongTurnParams = new JsonObject(); wrongTurnParams.addProperty("threadId", "thread-1"); wrongTurnParams.add("turn", wrongTurn);
                        JsonObject tool = new JsonObject(); tool.addProperty("id", 990); tool.addProperty("method", "execCommand"); tool.add("params", new JsonObject());
                        foreign = tool + "\n" + notification("item/completed", wrongItem) + notification("turn/completed", wrongTurnParams);
                    }
                    return response(id, result) + foreign + notification("item/completed", completed) + notification("turn/completed", turnParams);
                }
                default -> { }
            }
            return response(id, result);
        }
        private static JsonObject config(boolean unsafe) {
            JsonObject result = new JsonObject(), features = new JsonObject();
            for (String k : List.of("hooks", "plugins", "apps", "multi_agent", "code_mode", "code_mode_host", "image_generation", "memories")) features.addProperty(k, unsafe && k.equals("plugins"));
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
        private static String notification(String method, JsonObject params) { JsonObject frame = new JsonObject(); frame.addProperty("method", method); frame.add("params", params); return frame + "\n"; }
        void sendLateProposal() throws IOException {
            if (process == null) return;
            JsonObject item = new JsonObject(); item.addProperty("type", "agentMessage");
            item.addProperty("text", "{\"message\":\"late\",\"plan\":{\"revision\":2,\"palette\":[{\"key\":\"stone\",\"state\":{\"id\":\"minecraft:stone\",\"properties\":{}}}],\"stages\":[{\"id\":\"late\",\"title\":\"Late\",\"after\":[],\"operations\":[{\"op\":\"block\",\"position\":[0,0,0],\"state\":\"stone\"}]}]}}");
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
                            serverOut.flush();
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

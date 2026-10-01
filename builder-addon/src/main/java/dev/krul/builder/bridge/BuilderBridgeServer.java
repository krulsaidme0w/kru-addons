package dev.krul.builder.bridge;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;

/** Authenticated HTTP bridge bound exclusively to IPv4 loopback. */
public final class BuilderBridgeServer implements AutoCloseable {
    public static final int MAX_BODY_BYTES = BridgeProtocol.MAX_JSON_BYTES;
    public static final int DEFAULT_PORT = 47653;
    private static final Gson GSON = new Gson();
    private final BridgePairing pairing;
    private final int port;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "builder-bridge-http"); thread.setDaemon(true); return thread;
    });
    private HttpServer server;
    private volatile boolean closed;

    public BuilderBridgeServer(BridgePairing pairing) { this(pairing, DEFAULT_PORT); }
    public BuilderBridgeServer(BridgePairing pairing, int port) {
        this.pairing = Objects.requireNonNull(pairing);
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid bridge port");
        this.port = port;
    }

    public synchronized InetSocketAddress start(BridgeDataSource source) throws IOException {
        Objects.requireNonNull(source);
        if (server != null) return server.getAddress();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        server.setExecutor(workers);
        route("/v1/pair", exchange -> pair(exchange));
        route("/v1/pair/status", exchange -> pairStatus(exchange));
        route("/v1/status", exchange -> authenticated(exchange, source, BridgeProtocol.Method.STATUS));
        route("/v1/selection", exchange -> authenticated(exchange, source, BridgeProtocol.Method.READ_SELECTION));
        route("/v1/capture", exchange -> authenticated(exchange, source, BridgeProtocol.Method.CAPTURE_VIEW));
        server.start();
        return server.getAddress();
    }

    public BridgePairing pairing() { return pairing; }

    private void route(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (closed || !loopback(exchange)) { send(exchange, 403, "Forbidden"); return; }
            try { handler.handle(exchange); }
            catch (BodyTooLarge error) { send(exchange, 413, "Request too large"); }
            catch (Throwable error) { send(exchange, 500, "Bridge request failed"); }
        });
    }

    private void pair(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, "Method not allowed"); return; }
        if (readBody(exchange).length != 0) { send(exchange, 400, "Pair request must have an empty body"); return; }
        try {
            var challenge = pairing.begin();
            sendJson(exchange, 202, GSON.toJson(new PairReply("WAITING", challenge.code(), challenge.expiresAtMillis(), null)));
        } catch (IllegalStateException pending) { send(exchange, 409, "A pairing request is already pending"); }
    }

    private void pairStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, "Method not allowed"); return; }
        String code = query(exchange, "code");
        if (code == null || !code.matches("[0-9]{6}")) { send(exchange, 400, "Invalid challenge"); return; }
        String token = pairing.takeApprovedToken(code);
        BridgePairing.State state = pairing.state();
        String status = token != null ? "APPROVED" : state.name();
        sendJson(exchange, 200, GSON.toJson(new PairReply(status, code, 0, token)));
    }

    private void authenticated(HttpExchange exchange, BridgeDataSource source, BridgeProtocol.Method method) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, "Method not allowed"); return; }
        if (!pairing.authenticate(bearer(exchange))) { send(exchange, 401, "Pairing required"); return; }
        byte[] bytes = readBody(exchange);
        BridgeProtocol.Request input;
        try { input = bytes.length == 0 ? null : BridgeProtocol.decodeRequest(new String(bytes, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException invalid) { send(exchange, 400, "Invalid bridge request"); return; }
        if (input == null) { send(exchange, 400, "Request body required"); return; }
        if (input.method() != method) { send(exchange, 400, "Request method mismatch"); return; }
        CompletionStage<BridgeProtocol.Response> operation = switch (method) {
            case STATUS -> source.status(input);
            case READ_SELECTION -> source.readSelection(input);
            case CAPTURE_VIEW -> source.captureView(input);
            default -> throw new IllegalArgumentException("Unsupported method");
        };
        try {
            BridgeProtocol.Response response = operation.toCompletableFuture().get(3, TimeUnit.SECONDS);
            sendJson(exchange, response.ok() ? 200 : 409, BridgeProtocol.encodeResponse(response));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); send(exchange, 503, "Request interrupted");
        } catch (ExecutionException | TimeoutException failure) {
            send(exchange, 504, "Minecraft client did not respond in time");
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-length");
        if (contentLength != null) {
            try { if (Long.parseLong(contentLength) > MAX_BODY_BYTES) throw new BodyTooLarge(); }
            catch (NumberFormatException malformed) { throw new IOException("Invalid content length"); }
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw new BodyTooLarge();
        return body;
    }

    private static boolean loopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return address != null && address.isLoopbackAddress();
    }
    private static String bearer(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Authorization");
        return value != null && value.startsWith("Bearer ") ? value.substring(7) : null;
    }
    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) return pair[1];
        }
        return null;
    }
    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, body);
    }
    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    @Override public synchronized void close() {
        closed = true;
        if (server != null) { server.stop(0); server = null; }
        pairing.revoke(); workers.shutdownNow();
    }

    @FunctionalInterface private interface HttpHandler { void handle(HttpExchange exchange) throws Exception; }
    private record PairReply(String state, String code, long expiresAtMillis, String token) {}
    private static final class BodyTooLarge extends IOException { private static final long serialVersionUID = 1L; }
}

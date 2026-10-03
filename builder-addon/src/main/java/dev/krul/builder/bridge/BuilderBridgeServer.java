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

/** Unauthenticated local HTTP bridge bound exclusively to IPv4 loopback. */
public final class BuilderBridgeServer implements AutoCloseable {
    public static final int MAX_BODY_BYTES = BridgeProtocol.MAX_JSON_BYTES;
    public static final int DEFAULT_PORT = 47653;
    private static final Gson GSON = new Gson();
    private final int port;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "builder-bridge-http"); thread.setDaemon(true); return thread;
    });
    private HttpServer server;
    private volatile boolean closed;

    public BuilderBridgeServer() { this(DEFAULT_PORT); }
    public BuilderBridgeServer(int port) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid bridge port");
        this.port = port;
    }

    public synchronized InetSocketAddress start(BridgeDataSource source) throws IOException {
        Objects.requireNonNull(source);
        if (server != null) return server.getAddress();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        server.setExecutor(workers);
        route("/v1/status", exchange -> localRequest(exchange, source, BridgeProtocol.Method.STATUS));
        route("/v1/selection", exchange -> localRequest(exchange, source, BridgeProtocol.Method.READ_SELECTION));
        route("/v1/capture", exchange -> localRequest(exchange, source, BridgeProtocol.Method.CAPTURE_VIEW));
        route("/v1/catalog/mods", exchange -> localRequest(exchange, source, BridgeProtocol.Method.LIST_BLOCK_MODS));
        route("/v1/catalog/search", exchange -> localRequest(exchange, source, BridgeProtocol.Method.SEARCH_BLOCKS));
        route("/v1/catalog/describe", exchange -> localRequest(exchange, source, BridgeProtocol.Method.DESCRIBE_BLOCK));
        route("/v1/catalog/items/mods", exchange -> localRequest(exchange, source, BridgeProtocol.Method.LIST_ITEM_MODS));
        route("/v1/catalog/items/search", exchange -> localRequest(exchange, source, BridgeProtocol.Method.SEARCH_ITEMS));
        route("/v1/catalog/items/describe", exchange -> localRequest(exchange, source, BridgeProtocol.Method.DESCRIBE_ITEM));
        route("/v1/plans/inspect", exchange -> localRequest(exchange, source, BridgeProtocol.Method.INSPECT_PLAN));
        route("/v1/plans/publish", exchange -> localRequest(exchange, source, BridgeProtocol.Method.PUBLISH_PLAN));
        route("/v1/plans/status", exchange -> localRequest(exchange, source, BridgeProtocol.Method.PLAN_STATUS));
        server.start();
        return server.getAddress();
    }

    private void route(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (closed || !isLoopback(exchange.getRemoteAddress().getAddress())) { send(exchange, 403, "Forbidden"); return; }
            try { handler.handle(exchange); }
            catch (BodyTooLarge error) { send(exchange, 413, "Request too large"); }
            catch (Throwable error) { send(exchange, 500, "Bridge request failed"); }
        });
    }

    private void localRequest(HttpExchange exchange, BridgeDataSource source, BridgeProtocol.Method method) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { send(exchange, 405, "Method not allowed"); return; }
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
            case LIST_BLOCK_MODS -> source.listBlockMods(input);
            case SEARCH_BLOCKS -> source.searchBlocks(input);
            case DESCRIBE_BLOCK -> source.describeBlock(input);
            case LIST_ITEM_MODS -> source.listItemMods(input);
            case SEARCH_ITEMS -> source.searchItems(input);
            case DESCRIBE_ITEM -> source.describeItem(input);
            case INSPECT_PLAN -> source.inspectPlan(input);
            case PUBLISH_PLAN -> source.publishPlan(input);
            case PLAN_STATUS -> source.planStatus(input);
            default -> throw new IllegalArgumentException("Unsupported method");
        };
        try {
            BridgeProtocol.Response response = operation.toCompletableFuture().get(8, TimeUnit.SECONDS);
            sendJson(exchange, response.ok() ? 200 : 409, BridgeProtocol.encodeResponse(response));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); send(exchange, 503, "Request interrupted");
        } catch (ExecutionException | TimeoutException failure) {
            operation.toCompletableFuture().cancel(true);
            send(exchange, 504, "Minecraft client did not respond within the bounded request deadline");
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

    static boolean isLoopback(InetAddress address) {
        return address != null && address.isLoopbackAddress();
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
        workers.shutdownNow();
    }

    @FunctionalInterface private interface HttpHandler { void handle(HttpExchange exchange) throws Exception; }
    private static final class BodyTooLarge extends IOException { private static final long serialVersionUID = 1L; }
}

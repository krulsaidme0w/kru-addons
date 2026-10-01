package dev.krul.builder.mcp.viewer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.krul.builder.mcp.archive.PlanArchiveStore;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/** Read-only local archive viewer. The unguessable path token prevents ambient local-page access. */
public final class PlanViewerServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 47654;
    private static final int MAX_RESPONSE_BYTES = 96 * 1024 * 1024;
    private final PlanArchiveStore archive;
    private final int port;
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "builder-plan-viewer"); thread.setDaemon(true); return thread;
    });
    private HttpServer server;

    public PlanViewerServer(PlanArchiveStore archive) { this(archive, DEFAULT_PORT); }
    public PlanViewerServer(PlanArchiveStore archive, int port) {
        this.archive = Objects.requireNonNull(archive);
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid viewer port");
        this.port = port;
    }

    public synchronized String start() throws IOException {
        if (server == null) {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 8);
            server.setExecutor(workers); server.createContext("/v/", this::handle); server.start();
        }
        return url();
    }

    public synchronized String url() {
        int boundPort = server == null ? port : server.getAddress().getPort();
        if (boundPort == 0) throw new IllegalStateException("Viewer has not started");
        return "http://127.0.0.1:" + boundPort + "/v/" + token + "/";
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!isLoopback(exchange) || !validHost(exchange)) { send(exchange, 403, "text/plain; charset=utf-8", "Forbidden"); return; }
        if (!"GET".equals(exchange.getRequestMethod())) { send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed"); return; }
        String path = exchange.getRequestURI().getPath(); String prefix = "/v/" + token + "/";
        if (!path.startsWith(prefix)) { send(exchange, 404, "text/plain; charset=utf-8", "Not found"); return; }
        String resource = path.substring(prefix.length());
        try {
            if (resource.isEmpty()) { sendResource(exchange, "viewer/index.html", "text/html; charset=utf-8"); return; }
            if (resource.equals("app.js")) { sendResource(exchange, "viewer/app.js", "text/javascript; charset=utf-8"); return; }
            if (resource.equals("styles.css")) { sendResource(exchange, "viewer/styles.css", "text/css; charset=utf-8"); return; }
            if (resource.equals("api/plans")) {
                send(exchange, 200, "application/json; charset=utf-8", PlanArchiveStore.encodeSummaries(archive.list(PlanArchiveStore.MAX_RECORDS))); return;
            }
            if (resource.startsWith("api/plans/")) {
                String rawId = resource.substring("api/plans/".length()); UUID id;
                try { id = UUID.fromString(rawId); } catch (IllegalArgumentException invalid) { send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Invalid archive ID\"}"); return; }
                var record = archive.load(id);
                if (record.isEmpty()) { send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"Plan not found\"}"); return; }
                String json = PlanArchiveStore.encode(record.orElseThrow());
                if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) { send(exchange, 413, "application/json; charset=utf-8", "{\"error\":\"Plan archive too large to display\"}"); return; }
                send(exchange, 200, "application/json; charset=utf-8", json); return;
            }
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        } catch (IOException failure) { send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"Plan archive unavailable\"}"); }
    }

    private void sendResource(HttpExchange exchange, String name, String type) throws IOException {
        byte[] bytes;
        try (var input = PlanViewerServer.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) { send(exchange, 404, "text/plain; charset=utf-8", "Viewer assets are not packaged"); return; }
            bytes = input.readNBytes(1_048_577);
        }
        if (bytes.length > 1_048_576) { send(exchange, 500, "text/plain; charset=utf-8", "Viewer asset exceeds size limit"); return; }
        send(exchange, 200, type, bytes);
    }

    private static boolean isLoopback(HttpExchange exchange) {
        InetAddress address = exchange.getRemoteAddress().getAddress(); return address != null && address.isLoopbackAddress();
    }
    private boolean validHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host"); if (host == null) return false;
        int boundPort = server.getAddress().getPort();
        return host.equals("127.0.0.1:" + boundPort) || host.equals("localhost:" + boundPort);
    }
    private static void send(HttpExchange exchange, int status, String type, String text) throws IOException {
        send(exchange, status, type, text.getBytes(StandardCharsets.UTF_8));
    }
    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        var headers = exchange.getResponseHeaders(); headers.set("Content-Type", type); headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff"); headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Frame-Options", "SAMEORIGIN");
        headers.set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @Override public synchronized void close() {
        if (server != null) { server.stop(0); server = null; }
        workers.shutdownNow();
    }
}

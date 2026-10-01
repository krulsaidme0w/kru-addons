package dev.krul.builder.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Typed, bounded HTTP client for the paired in-game bridge. Tokens stay process-local. */
public final class BridgeClient {
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PAGE_SIZE = 1024;
    public static final int MAX_CATALOG_PAGE = 10_000;
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private final URI baseUri;
    private final HttpClient http;
    private final Duration timeout;
    private volatile String token;
    private volatile String pendingPairCode;

    public record Coordinate(int x, int y, int z) {}
    public record Bounds(Coordinate min, Coordinate max) {
        public Bounds { Objects.requireNonNull(min); Objects.requireNonNull(max); }
    }
    public record BridgeReply(boolean ok, long worldGeneration, long snapshotRevision, String message, String payload) {}
    public record CaptureReply(long worldGeneration, long snapshotRevision, byte[] png) {}
    public record BlockDescription(BridgeReply metadata, byte[] png) {}
    public record PairResult(String state, String message) {}
    private record WireResponse(int version, String id, boolean ok, long worldGeneration, long snapshotRevision,
                                String message, String payload, String imageBase64) {}
    private record PairWire(String state, String code, long expiresAtMillis, String token) {}

    public BridgeClient(URI baseUri, Duration timeout) {
        this.baseUri = validateBase(Objects.requireNonNull(baseUri)); this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
        this.http = HttpClient.newBuilder().connectTimeout(timeout).version(HttpClient.Version.HTTP_1_1).build();
    }

    public static BridgeClient localDefault() {
        String configured = System.getenv().getOrDefault("BUILDER_BRIDGE_URL", "http://127.0.0.1:47653");
        return new BridgeClient(URI.create(configured), Duration.ofSeconds(4));
    }

    public void setToken(String token) {
        if (token == null || token.isBlank() || token.length() > 256) throw new IllegalArgumentException("Invalid bridge token");
        this.token = token;
    }

    public BridgeReply status() { return call("/v1/status", "STATUS", null, 0, 0, 0, 0); }

    public BridgeReply listBlockMods(int page) {
        if (page < 0 || page > MAX_CATALOG_PAGE) throw new IllegalArgumentException("Invalid catalog page");
        return call("/v1/catalog/mods", "LIST_BLOCK_MODS", null, page, 20, 0, 0, null, null, null);
    }

    public BridgeReply searchBlocks(String query, String namespace, int page) {
        if (query == null || query.length() > 128 || (query.isBlank() && (namespace == null || namespace.isBlank()))
            || page < 0 || page > MAX_CATALOG_PAGE || (namespace != null && namespace.length() > 128))
            throw new IllegalArgumentException("Invalid block catalog search");
        return call("/v1/catalog/search", "SEARCH_BLOCKS", null, page, 20, 0, 0, query, namespace, null);
    }

    public BlockDescription describeBlock(String id) {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Expected an exact namespaced block ID");
        WireResponse response = wire(callRequest("/v1/catalog/describe", "DESCRIBE_BLOCK", null, 0, 0, 0, 0, null, null, id));
        if (!response.ok()) throw new BridgeException(response.message());
        if (response.imageBase64() == null) throw new BridgeException("No block preview returned");
        try {
            byte[] png = Base64.getDecoder().decode(response.imageBase64()); validatePng(png);
            return new BlockDescription(new BridgeReply(true, response.worldGeneration(), response.snapshotRevision(), response.message(), response.payload()), png);
        } catch (IllegalArgumentException invalid) { throw new BridgeException("Bridge returned an invalid block preview", invalid); }
    }

    public BridgeReply readSelection(Bounds bounds, int page, int pageSize, long worldGeneration, long snapshotRevision) {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE || worldGeneration < 1 || snapshotRevision < 1)
            throw new IllegalArgumentException("Invalid page or snapshot identity");
        return call("/v1/selection", "READ_SELECTION", bounds, page, pageSize, worldGeneration, snapshotRevision);
    }

    public CaptureReply captureView(long worldGeneration, long snapshotRevision) {
        WireResponse response = wire(callRequest("/v1/capture", "CAPTURE_VIEW", null, 0, 0, worldGeneration, snapshotRevision));
        if (!response.ok()) throw new BridgeException(response.message());
        if (response.imageBase64() == null) throw new BridgeException("Bridge returned no image");
        try {
            byte[] png = Base64.getDecoder().decode(response.imageBase64());
            validatePng(png);
            return new CaptureReply(response.worldGeneration(), response.snapshotRevision(), png);
        } catch (IllegalArgumentException invalid) { throw new BridgeException("Bridge returned an invalid image", invalid); }
    }

    /** Starts pairing once, then waits only on this local MCP process for the explicit in-game decision. */
    public PairResult pair(Duration waitLimit) {
        Objects.requireNonNull(waitLimit);
        if (waitLimit.isNegative() || waitLimit.isZero() || waitLimit.compareTo(Duration.ofMinutes(2)) > 0)
            throw new IllegalArgumentException("Pair wait must be between zero and two minutes");
        String challenge = pendingPairCode;
        if (challenge == null) {
            PairWire started = pairWire("POST", "/v1/pair");
            if (!"WAITING".equals(started.state()) || started.code() == null) return new PairResult(started.state(), "Could not start pairing");
            challenge = started.code(); pendingPairCode = challenge;
        }
        long deadline = System.nanoTime() + waitLimit.toNanos();
        while (System.nanoTime() < deadline) {
            PairWire state = pairWire("GET", "/v1/pair/status?code=" + challenge);
            if ("APPROVED".equals(state.state()) && state.token() != null) {
                setToken(state.token()); pendingPairCode = null;
                return new PairResult("APPROVED", "Local Minecraft bridge paired successfully.");
            }
            if ("REJECTED".equals(state.state()) || "EXPIRED".equals(state.state())) {
                pendingPairCode = null; return new PairResult(state.state(), "Pairing was " + state.state().toLowerCase() + ".");
            }
            try { TimeUnit.MILLISECONDS.sleep(400); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new BridgeException("Pairing interrupted", interrupted); }
        }
        return new PairResult("WAITING", "Pairing is still pending in Minecraft Builder Settings.");
    }

    public String pendingPairCode() { return pendingPairCode; }

    private BridgeReply call(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision) {
        return call(path, method, bounds, page, pageSize, generation, revision, null, null, null);
    }
    private BridgeReply call(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision,
                             String query, String namespace, String blockId) {
        WireResponse response = wire(callRequest(path, method, bounds, page, pageSize, generation, revision, query, namespace, blockId));
        return new BridgeReply(response.ok(), response.worldGeneration(), response.snapshotRevision(), response.message(), response.payload());
    }

    private HttpRequest callRequest(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision) {
        return callRequest(path, method, bounds, page, pageSize, generation, revision, null, null, null);
    }
    private HttpRequest callRequest(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision,
                                    String query, String namespace, String blockId) {
        String auth = token;
        if (auth == null) throw new BridgeException("Pair this MCP companion with Minecraft first");
        JsonObject json = new JsonObject(); json.addProperty("version", PROTOCOL_VERSION); json.addProperty("id", "mcp-" + Long.toUnsignedString(System.nanoTime()));
        json.addProperty("method", method); json.add("bounds", GSON.toJsonTree(bounds)); json.addProperty("page", page); json.addProperty("pageSize", pageSize);
        json.addProperty("expectedWorldGeneration", generation); json.addProperty("expectedSnapshotRevision", revision); json.add("token", com.google.gson.JsonNull.INSTANCE);
        if (query != null) json.addProperty("query", query); if (namespace != null) json.addProperty("namespace", namespace);
        if (blockId != null) json.addProperty("blockId", blockId);
        return HttpRequest.newBuilder(uri(path)).timeout(timeout).header("Authorization", "Bearer " + auth)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8)).build();
    }

    private WireResponse wire(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().getBytes(StandardCharsets.UTF_8).length > MAX_IMAGE_BYTES * 2)
                throw new BridgeException("Bridge response exceeds size limit");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new BridgeException("Bridge HTTP " + response.statusCode() + ": " + response.body());
            WireResponse wire = GSON.fromJson(response.body(), WireResponse.class);
            if (wire == null || wire.version() != PROTOCOL_VERSION || wire.worldGeneration() < 0 || wire.snapshotRevision() < 0)
                throw new BridgeException("Bridge protocol version or identity mismatch");
            return wire;
        } catch (BridgeException error) { throw error; }
        catch (Exception error) { throw new BridgeException("Could not reach local Minecraft Builder bridge", error); }
    }

    private PairWire pairWire(String method, String path) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).timeout(timeout);
            if (method.equals("POST")) request.POST(HttpRequest.BodyPublishers.noBody()); else request.GET();
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new BridgeException("Bridge pairing HTTP " + response.statusCode());
            PairWire wire = GSON.fromJson(response.body(), PairWire.class);
            if (wire == null || wire.state() == null) throw new BridgeException("Invalid pairing response");
            return wire;
        } catch (BridgeException error) { throw error; }
        catch (Exception error) { throw new BridgeException("Could not reach local Minecraft Builder bridge", error); }
    }

    private URI uri(String path) { return URI.create(baseUri.toString().replaceAll("/$", "") + path); }
    private static URI validateBase(URI uri) {
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null || !(host.equals("127.0.0.1") || host.equals("localhost") || host.equals("[::1]"))
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Bridge URL must be an HTTP loopback address");
        return uri;
    }
    private static void validatePng(byte[] png) {
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        if (png.length < signature.length || png.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Image size is invalid");
        for (int i=0; i<signature.length; i++) if (png[i] != signature[i]) throw new IllegalArgumentException("Image is not a PNG");
    }

    public static final class BridgeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public BridgeException(String message) { super(message); }
        public BridgeException(String message, Throwable cause) { super(message, cause); }
    }
}

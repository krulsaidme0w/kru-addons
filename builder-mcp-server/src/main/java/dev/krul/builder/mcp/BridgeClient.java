package dev.krul.builder.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.InetAddress;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/** Typed, bounded HTTP client for the loopback-only in-game bridge. */
public final class BridgeClient {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_PAGE_SIZE = 1024;
    public static final int MAX_CATALOG_PAGE = 10_000;
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PLAN_BYTES = 16 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private final URI baseUri;
    private final HttpClient http;
    private final Duration timeout;

    public record Coordinate(int x, int y, int z) {}
    public record Bounds(Coordinate min, Coordinate max) {
        public Bounds { Objects.requireNonNull(min); Objects.requireNonNull(max); }
    }
    public record BridgeReply(boolean ok, long worldGeneration, long snapshotRevision, String message, String payload) {}
    public record CaptureReply(long worldGeneration, long snapshotRevision, byte[] png) {}
    public record BlockDescription(BridgeReply metadata, byte[] png) {}
    public record PlanInspection(BridgeReply metadata, byte[] previewPng) {}
    public record ItemDescription(BridgeReply metadata, byte[] png) {}
    private record WireResponse(int version, String id, boolean ok, long worldGeneration, long snapshotRevision,
                                String message, String payload, String imageBase64) {}

    public BridgeClient(URI baseUri, Duration timeout) {
        this.baseUri = validateBase(Objects.requireNonNull(baseUri)); this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive");
        this.http = HttpClient.newBuilder().connectTimeout(timeout).version(HttpClient.Version.HTTP_1_1).build();
    }

    public static BridgeClient localDefault() {
        String kernel = readSystemFile("/proc/sys/kernel/osrelease");
        String routes = readSystemFile("/proc/net/route");
        return new BridgeClient(defaultBridgeUri(System.getenv("BUILDER_BRIDGE_URL"), kernel, routes), defaultRequestTimeout());
    }

    static Duration defaultRequestTimeout() { return DEFAULT_REQUEST_TIMEOUT; }

    static URI defaultBridgeUri(String configured, String kernelRelease, String routeTable) {
        if (configured != null && !configured.isBlank()) return URI.create(configured);
        if (kernelRelease != null && kernelRelease.toLowerCase(java.util.Locale.ROOT).contains("microsoft")) {
            for (String line : Objects.requireNonNullElse(routeTable, "").split("\\R")) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 4 || !fields[1].equals("00000000")) continue;
                try {
                    long flags = Long.parseLong(fields[3], 16);
                    long gateway = Long.parseUnsignedLong(fields[2], 16);
                    if ((flags & 0x2) == 0) continue;
                    byte[] address = {(byte) gateway, (byte) (gateway >>> 8),
                        (byte) (gateway >>> 16), (byte) (gateway >>> 24)};
                    String host = InetAddress.getByAddress(address).getHostAddress();
                    if (isPrivateIpv4(host)) return URI.create("http://" + host + ":47653");
                } catch (RuntimeException | java.net.UnknownHostException ignored) { }
            }
        }
        return URI.create("http://127.0.0.1:47653");
    }

    private static String readSystemFile(String path) {
        try { return Files.readString(Path.of(path), StandardCharsets.UTF_8); }
        catch (Exception ignored) { return ""; }
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

    public BridgeReply listItemMods(int page) {
        if (page < 0 || page > MAX_CATALOG_PAGE) throw new IllegalArgumentException("Invalid catalog page");
        return call("/v1/catalog/items/mods", "LIST_ITEM_MODS", null, page, 20, 0, 0, null, null, null);
    }

    public BridgeReply searchItems(String query, String namespace, int page) {
        if (query == null || query.length() > 128 || (query.isBlank() && (namespace == null || namespace.isBlank()))
            || page < 0 || page > MAX_CATALOG_PAGE || (namespace != null && namespace.length() > 128))
            throw new IllegalArgumentException("Invalid item catalog search");
        return call("/v1/catalog/items/search", "SEARCH_ITEMS", null, page, 20, 0, 0, query, namespace, null);
    }

    public ItemDescription describeItem(String id) {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Expected an exact namespaced item ID");
        WireResponse response = wire(callRequest("/v1/catalog/items/describe", "DESCRIBE_ITEM", null, 0, 0, 0, 0,
            null, null, null, null, null, id));
        if (!response.ok()) throw new BridgeException(response.message());
        byte[] png = null;
        if (response.imageBase64() != null) {
            try { png = Base64.getDecoder().decode(response.imageBase64()); validatePng(png); }
            catch (IllegalArgumentException invalid) { throw new BridgeException("Bridge returned an invalid item preview", invalid); }
        }
        return new ItemDescription(new BridgeReply(true, response.worldGeneration(), response.snapshotRevision(),
            response.message(), response.payload()), png);
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

    /** Collects the exact selected snapshot for the private plan viewer/archive, never the MCP tool context. */
    public String readSelectionSnapshot(Bounds bounds, long worldGeneration, long snapshotRevision) {
        Objects.requireNonNull(bounds);
        long volume = Math.multiplyExact(Math.multiplyExact((long) bounds.max().x() - bounds.min().x() + 1,
            (long) bounds.max().y() - bounds.min().y() + 1), (long) bounds.max().z() - bounds.min().z() + 1);
        if (volume < 1 || volume > 32_768 || worldGeneration < 1 || snapshotRevision < 1)
            throw new IllegalArgumentException("Snapshot bounds or identity exceed limit");
        int pageSize = MAX_PAGE_SIZE, pages = Math.toIntExact((volume + pageSize - 1) / pageSize);
        JsonObject result = new JsonObject(); result.add("min", coordinate(bounds.min())); result.add("max", coordinate(bounds.max()));
        JsonArray cells = new JsonArray();
        for (int page = 0; page < pages; page++) {
            BridgeReply reply = readSelection(bounds, page, pageSize, worldGeneration, snapshotRevision);
            if (!reply.ok()) throw new BridgeException(reply.message());
            if (reply.worldGeneration() != worldGeneration || reply.snapshotRevision() != snapshotRevision)
                throw new BridgeException("World snapshot changed while archiving the region");
            JsonObject payload = JsonParser.parseString(reply.payload()).getAsJsonObject();
            if (payload.get("snapshotRevision").getAsLong() != snapshotRevision
                || payload.getAsJsonArray("cells").size() != Math.min(pageSize, volume - (long) page * pageSize))
                throw new BridgeException("Incomplete snapshot page received from Minecraft");
            payload.getAsJsonArray("cells").forEach(cells::add);
        }
        result.addProperty("snapshotRevision", snapshotRevision); result.addProperty("total", volume); result.add("cells", cells);
        String json = GSON.toJson(result);
        if (json.getBytes(StandardCharsets.UTF_8).length > 32 * 1024 * 1024)
            throw new BridgeException("Selected snapshot exceeds the local archive limit");
        return json;
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

    public PlanInspection inspectPlan(String contractJson, long worldGeneration, long snapshotRevision) {
        if (contractJson == null || contractJson.getBytes(StandardCharsets.UTF_8).length > MAX_PLAN_BYTES
            || worldGeneration < 1 || snapshotRevision < 1) throw new IllegalArgumentException("Invalid plan or snapshot identity");
        WireResponse response = wire(callRequest("/v1/plans/inspect", "INSPECT_PLAN", null, 0, 0,
            worldGeneration, snapshotRevision, null, null, null, contractJson, null));
        if (!response.ok()) throw new BridgeException(response.message());
        byte[] png = null;
        if (response.imageBase64() != null) {
            try { png = Base64.getDecoder().decode(response.imageBase64()); validatePng(png); }
            catch (IllegalArgumentException invalid) { throw new BridgeException("Bridge returned an invalid plan preview", invalid); }
        }
        return new PlanInspection(new BridgeReply(true, response.worldGeneration(), response.snapshotRevision(),
            response.message(), response.payload()), png);
    }

    public BridgeReply publishPlan(String validationToken, long worldGeneration, long snapshotRevision) {
        if (validationToken == null || validationToken.length() > 128 || worldGeneration < 1 || snapshotRevision < 1)
            throw new IllegalArgumentException("Invalid plan validation token or snapshot identity");
        return simplePlanCall("/v1/plans/publish", "PUBLISH_PLAN", worldGeneration, snapshotRevision, null, validationToken);
    }

    public BridgeReply planStatus(long worldGeneration, long snapshotRevision) {
        return simplePlanCall("/v1/plans/status", "PLAN_STATUS", worldGeneration, snapshotRevision, null, null);
    }

    private BridgeReply simplePlanCall(String path, String method, long generation, long revision, String payload, String validationToken) {
        WireResponse response = wire(callRequest(path, method, null, 0, 0, generation, revision,
            null, null, null, payload, validationToken));
        if (!response.ok()) throw new BridgeException(response.message());
        return new BridgeReply(true, response.worldGeneration(), response.snapshotRevision(), response.message(), response.payload());
    }

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
        return callRequest(path, method, bounds, page, pageSize, generation, revision, query, namespace, blockId, null, null);
    }
    private HttpRequest callRequest(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision,
                                    String query, String namespace, String blockId, String payload, String validationToken) {
        return callRequest(path, method, bounds, page, pageSize, generation, revision, query, namespace, blockId, payload, validationToken, null);
    }
    private HttpRequest callRequest(String path, String method, Bounds bounds, int page, int pageSize, long generation, long revision,
                                    String query, String namespace, String blockId, String payload, String validationToken, String itemId) {
        JsonObject json = new JsonObject(); json.addProperty("version", PROTOCOL_VERSION); json.addProperty("id", "mcp-" + Long.toUnsignedString(System.nanoTime()));
        json.addProperty("method", method); json.add("bounds", GSON.toJsonTree(bounds)); json.addProperty("page", page); json.addProperty("pageSize", pageSize);
        json.addProperty("expectedWorldGeneration", generation); json.addProperty("expectedSnapshotRevision", revision);
        if (query != null) json.addProperty("query", query); if (namespace != null) json.addProperty("namespace", namespace);
        if (blockId != null) json.addProperty("blockId", blockId);
        if (itemId != null) json.addProperty("itemId", itemId);
        if (payload != null) json.addProperty("payload", payload);
        if (validationToken != null) json.addProperty("validationToken", validationToken);
        String body = json.toString();
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_PLAN_BYTES * 2 + 4096)
            throw new BridgeException("Plan request exceeds size limit");
        return HttpRequest.newBuilder(uri(path)).timeout(timeout).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
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

    private URI uri(String path) { return URI.create(baseUri.toString().replaceAll("/$", "") + path); }
    private static JsonObject coordinate(Coordinate value) {
        JsonObject json = new JsonObject(); json.addProperty("x", value.x()); json.addProperty("y", value.y()); json.addProperty("z", value.z()); return json;
    }
    private static URI validateBase(URI uri) {
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(uri.getScheme()) || host == null || !(host.equals("127.0.0.1")
            || host.equals("localhost") || host.equals("[::1]") || isPrivateIpv4(host))
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Bridge URL must be loopback or a private IPv4 address");
        return uri;
    }

    private static boolean isPrivateIpv4(String host) {
        if (host == null || host.indexOf(':') >= 0) return false;
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        int[] values = new int[4];
        try {
            for (int i = 0; i < octets.length; i++) {
                if (octets[i].isEmpty() || (octets[i].length() > 1 && octets[i].charAt(0) == '0')) return false;
                values[i] = Integer.parseInt(octets[i]);
                if (values[i] < 0 || values[i] > 255) return false;
            }
        } catch (NumberFormatException invalid) { return false; }
        return values[0] == 10 || (values[0] == 172 && values[1] >= 16 && values[1] <= 31)
            || (values[0] == 192 && values[1] == 168);
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

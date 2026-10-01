package dev.krul.builder.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.krul.builder.model.Bounds;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Small, versioned JSON contract shared with the local MCP companion. */
public final class BridgeProtocol {
    public static final int VERSION = 1;
    public static final int MAX_JSON_BYTES = 256 * 1024;
    private static final Gson GSON = new Gson();
    private BridgeProtocol() {}

    public enum Method { STATUS, READ_SELECTION, CAPTURE_VIEW, LIST_BLOCK_MODS, SEARCH_BLOCKS, DESCRIBE_BLOCK, PAIR }

    public record Request(int version, String id, Method method, Bounds bounds, int page, int pageSize,
                          long expectedWorldGeneration, long expectedSnapshotRevision, String query,
                          String namespace, String blockId, String token) {
        public Request(int version, String id, Method method, Bounds bounds, int page, int pageSize,
                       long expectedWorldGeneration, long expectedSnapshotRevision, String token) {
            this(version, id, method, bounds, page, pageSize, expectedWorldGeneration, expectedSnapshotRevision,
                null, null, null, token);
        }
    }

    /** Payload/image fields are transport strings so this DTO never depends on game or MCP classes. */
    public record Response(int version, String id, boolean ok, long worldGeneration,
                           long snapshotRevision, String message, String payload, String imageBase64) {}

    public static String encodeRequest(Request request) { return GSON.toJson(request); }
    public static String encodeResponse(Response response) { return GSON.toJson(response); }

    public static Request decodeRequest(String json) {
        checkJsonSize(json);
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            fields(object, Set.of("version", "id", "method", "bounds", "page", "pageSize",
                "expectedWorldGeneration", "expectedSnapshotRevision", "query", "namespace", "blockId", "token"));
            Request request = GSON.fromJson(object, Request.class);
            if (request.version() != VERSION) throw new IllegalArgumentException("Unsupported bridge protocol version");
            if (request.id() == null || request.id().isBlank() || request.id().length() > 128 || request.method() == null
                || request.expectedWorldGeneration() < 0 || request.expectedSnapshotRevision() < 0)
                throw new IllegalArgumentException("Invalid bridge request");
            return request;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed bridge request", e);
        }
    }

    public static Response decodeResponse(String json) {
        checkJsonSize(json);
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            fields(object, Set.of("version", "id", "ok", "worldGeneration", "snapshotRevision", "message", "payload", "imageBase64"));
            Response response = GSON.fromJson(object, Response.class);
            if (response.version() != VERSION || response.worldGeneration() < 0 || response.snapshotRevision() < 0)
                throw new IllegalArgumentException("Invalid bridge response version or revision");
            return response;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed bridge response", e);
        }
    }

    private static void checkJsonSize(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new IllegalArgumentException("Bridge JSON is missing or exceeds size limit");
    }

    private static void fields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unsupported bridge field: " + key);
    }
}

package dev.krul.builder.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Stdio MCP entry point. stdout is reserved exclusively for MCP frames. */
public final class BuilderMcpServer {
    public static final String VERSION = "0.1.0";
    private BuilderMcpServer() {}

    public static void main(String[] args) {
        BridgeClient bridge = BridgeClient.localDefault();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
            McpJsonDefaults.getMapper(), System.in, System.out, 1024 * 1024);
        McpSyncServer server = McpServer.sync(transport).serverInfo("kru-builder-minecraft", VERSION)
            .requestTimeout(Duration.ofMinutes(3))
            .instructions("Inspect the user's explicitly selected Minecraft region; discover blocks incrementally; never place or break blocks or move the player. Pairing requires explicit in-game approval.")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(toolSpecifications(bridge)).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "builder-mcp-shutdown"));
        System.err.println("Builder MCP companion started (stdio; bridge is loopback-only).");
    }

    public static List<SyncToolSpecification> toolSpecifications(BridgeClient bridge) {
        Objects.requireNonNull(bridge);
        var tools = new ArrayList<SyncToolSpecification>();
        tools.add(tool("minecraft_status", "Read current Builder connection, selection, and completed snapshot counters.", emptySchema(), true,
            (exchange, request) -> {
                BridgeClient.BridgeReply result = bridge.status();
                return textResult("worldGeneration=" + result.worldGeneration() + " snapshotRevision=" + result.snapshotRevision() + "\n" + result.payload(), !result.ok());
            }));
        tools.add(tool("read_selection", "Read a bounded page from the existing complete scan. Unknown/unloaded cells are explicit; this does not trigger a scan.", selectionSchema(), true,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                BridgeClient.Bounds bounds = args.get("bounds") == null ? null : bounds(args.get("bounds"));
                BridgeClient.BridgeReply result = bridge.readSelection(bounds, integer(args, "page"), integer(args, "pageSize"),
                    longInteger(args, "worldGeneration"), longInteger(args, "snapshotRevision"));
                String body = "worldGeneration=" + result.worldGeneration() + " snapshotRevision=" + result.snapshotRevision() + "\n" + result.payload();
                return textResult(body, !result.ok());
            }));
        tools.add(tool("list_block_mods", "List active block-registry namespaces by page. Never returns the whole block registry.", pageSchema(), true,
            (exchange, request) -> {
                BridgeClient.BridgeReply result = bridge.listBlockMods(integer(request.arguments(), "page"));
                return textResult("Active block namespaces · page " + request.arguments().get("page") + "\n" + result.payload(), !result.ok());
            }));
        tools.add(tool("search_blocks", "Search exact local block IDs and localized labels. Results are limited to 20 entries; use the next page for more.", searchSchema(), true,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                String namespace = args.get("namespace") == null ? null : args.get("namespace").toString();
                BridgeClient.BridgeReply result = bridge.searchBlocks(args.get("query").toString(), namespace, integer(args, "page"));
                return textResult("Modded block search\n" + result.payload(), !result.ok());
            }));
        tools.add(tool("describe_block", "Describe one exact block ID, its legal block-state properties, and one actual Minecraft-rendered inventory-default preview.", describeSchema(), true,
            (exchange, request) -> {
                BridgeClient.BlockDescription result = bridge.describeBlock(request.arguments().get("id").toString());
                return McpSchema.CallToolResult.builder().content(List.of(
                    McpSchema.TextContent.builder(result.metadata().payload()).build(),
                    McpSchema.ImageContent.builder(Base64.getEncoder().encodeToString(result.png()), "image/png").build())).isError(false).build();
            }));
        tools.add(tool("capture_view", "Capture the current Minecraft framebuffer view without moving the camera. Returns the PNG to the model.", identitySchema(), true,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                BridgeClient.CaptureReply result = bridge.captureView(longInteger(args, "worldGeneration"), longInteger(args, "snapshotRevision"));
                return McpSchema.CallToolResult.builder().content(List.of(
                    McpSchema.TextContent.builder("Current view · generation " + result.worldGeneration() + " · snapshot " + result.snapshotRevision()).build(),
                    McpSchema.ImageContent.builder(Base64.getEncoder().encodeToString(result.png()), "image/png").build())).isError(false).build();
            }));
        tools.add(tool("pair_local_minecraft", "Start one-time pairing, or resume waiting for the in-game decision. Open Minecraft Builder Settings and click Allow local companion, then call again if still pending.", emptySchema(), false,
            (exchange, request) -> {
                BridgeClient.PairResult result = bridge.pair(Duration.ofSeconds(5));
                return textResult(result.message(), "REJECTED".equals(result.state()) || "EXPIRED".equals(result.state()));
            }));
        return List.copyOf(tools);
    }

    private static SyncToolSpecification tool(String name, String description, Map<String, Object> schema, boolean readOnly,
            java.util.function.BiFunction<io.modelcontextprotocol.server.McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        var annotations = McpSchema.ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(false)
            .idempotentHint(readOnly).openWorldHint(false).build();
        var definition = McpSchema.Tool.builder(name, schema).description(description).annotations(annotations).build();
        return SyncToolSpecification.builder().tool(definition).callHandler((exchange, request) -> {
            try { return handler.apply(exchange, request); }
            catch (RuntimeException failure) {
                String message = failure.getMessage() == null ? "Invalid request or local bridge failure" : failure.getMessage();
                return textResult("Builder MCP error: " + message, true);
            }
        }).build();
    }

    private static McpSchema.CallToolResult textResult(String text, boolean error) {
        String safe = text == null ? "No payload" : text;
        if (safe.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024)
            return McpSchema.CallToolResult.builder().content(List.of(McpSchema.TextContent.builder("Builder MCP error: response exceeds the 256 KiB tool-output limit; request a smaller page.").build())).isError(true).build();
        return McpSchema.CallToolResult.builder().content(List.of(McpSchema.TextContent.builder(safe).build())).isError(error).build();
    }

    private static Map<String, Object> emptySchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }
    private static Map<String, Object> identitySchema() {
        return objectSchema(Map.of("worldGeneration", integerSchema(), "snapshotRevision", integerSchema()), "worldGeneration", "snapshotRevision");
    }
    private static Map<String, Object> pageSchema() {
        return objectSchema(Map.of("page", Map.of("type", "integer", "minimum", 0, "maximum", BridgeClient.MAX_CATALOG_PAGE)), "page");
    }
    private static Map<String, Object> searchSchema() {
        return objectSchema(Map.of("query", Map.of("type", "string", "maxLength", 128),
            "namespace", Map.of("anyOf", List.of(Map.of("type", "string", "maxLength", 128), Map.of("type", "null"))),
            "page", Map.of("type", "integer", "minimum", 0, "maximum", BridgeClient.MAX_CATALOG_PAGE)), "query", "namespace", "page");
    }
    private static Map<String, Object> describeSchema() {
        return objectSchema(Map.of("id", Map.of("type", "string", "maxLength", 256, "pattern", "^[a-z0-9_.-]+:[a-z0-9_./-]+$")), "id");
    }
    private static Map<String, Object> selectionSchema() {
        Map<String, Object> coordinate = objectSchema(Map.of("x", Map.of("type", "integer"),
            "y", Map.of("type", "integer"), "z", Map.of("type", "integer")), "x", "y", "z");
        Map<String, Object> bounds = objectSchema(Map.of("min", coordinate, "max", coordinate), "min", "max");
        return objectSchema(Map.of("bounds", Map.of("anyOf", List.of(bounds, Map.of("type", "null"))),
            "page", integerSchema(), "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", BridgeClient.MAX_PAGE_SIZE),
            "worldGeneration", integerSchema(), "snapshotRevision", integerSchema()),
            "bounds", "page", "pageSize", "worldGeneration", "snapshotRevision");
    }
    private static Map<String, Object> integerSchema() { return Map.of("type", "integer", "minimum", 0); }
    private static Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false);
    }
    private static int integer(Map<String, Object> args, String key) { return Math.toIntExact(longInteger(args, key)); }
    private static long longInteger(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing integer argument: " + key);
        double doubleValue = number.doubleValue(); long longValue = number.longValue();
        if (!Double.isFinite(doubleValue) || doubleValue != longValue) throw new IllegalArgumentException("Invalid integer argument: " + key);
        return longValue;
    }
    @SuppressWarnings("unchecked") private static BridgeClient.Bounds bounds(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("bounds must be an object or null");
        return new BridgeClient.Bounds(coordinate(map.get("min")), coordinate(map.get("max")));
    }
    @SuppressWarnings("unchecked") private static BridgeClient.Coordinate coordinate(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalArgumentException("coordinate must be an object");
        return new BridgeClient.Coordinate(coordinateValue(map.get("x")), coordinateValue(map.get("y")), coordinateValue(map.get("z")));
    }
    private static int coordinateValue(Object raw) {
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()) throw new IllegalArgumentException("coordinate must contain integers");
        return Math.toIntExact(number.longValue());
    }
}

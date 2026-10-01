package dev.krul.builder.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import dev.krul.builder.mcp.archive.PlanArchiveStore;
import dev.krul.builder.mcp.viewer.PlanViewerServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Stdio MCP entry point. stdout is reserved exclusively for MCP frames. */
public final class BuilderMcpServer {
    public static final String VERSION = "0.1.0";
    private BuilderMcpServer() {}

    public static void main(String[] args) {
        BridgeClient bridge = BridgeClient.localDefault();
        PlanArchiveStore archive = new PlanArchiveStore(PlanArchiveStore.defaultDirectory());
        PlanViewerServer viewer = new PlanViewerServer(archive);
        String viewerUrl = null;
        try { viewerUrl = viewer.start(); }
        catch (java.io.IOException unavailable) { System.err.println("Builder local plan viewer unavailable: " + unavailable.getMessage()); }
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
            McpJsonDefaults.getMapper(), System.in, System.out, 32 * 1024 * 1024);
        McpSyncServer server = McpServer.sync(transport).serverInfo("kru-builder-minecraft", VERSION)
            .requestTimeout(Duration.ofMinutes(3))
            .instructions("Inspect the user's explicitly selected Minecraft region; discover blocks incrementally; propose strict PLACE/REPLACE/REMOVE plans, inspect against an exact snapshot, and publish preview only after explicit approval. Never place or break blocks or move the player. Pairing requires explicit in-game approval. Archived plans can be revisited and must be revalidated.")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
            .tools(toolSpecifications(bridge, archive, viewerUrl)).build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.close(); viewer.close(); }, "builder-mcp-shutdown"));
        System.err.println("Builder MCP companion started (stdio; bridge is loopback-only).");
    }

    public static List<SyncToolSpecification> toolSpecifications(BridgeClient bridge) {
        return toolSpecifications(bridge, new PlanArchiveStore(PlanArchiveStore.defaultDirectory()));
    }

    public static List<SyncToolSpecification> toolSpecifications(BridgeClient bridge, PlanArchiveStore archive) {
        return toolSpecifications(bridge, archive, null);
    }

    public static List<SyncToolSpecification> toolSpecifications(BridgeClient bridge, PlanArchiveStore archive, String viewerUrl) {
        Objects.requireNonNull(bridge);
        Objects.requireNonNull(archive);
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
        tools.add(tool("inspect_build_plan", "Validate a strict staged PLACE/REPLACE/REMOVE plan against the exact current selection snapshot and archive this plan revision locally. Returns diagnostics and a schematic preview; does not change the world.", planInspectionSchema(), false,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                String json = new com.google.gson.Gson().toJson(args.get("plan"));
                BridgeClient.PlanInspection inspection = bridge.inspectPlan(json,
                    longInteger(args, "worldGeneration"), longInteger(args, "snapshotRevision"));
                String archived = archivePlan(bridge, archive, json, inspection);
                return planInspectionResult(inspection, "Plan inspection archived locally: " + archived);
            }));
        tools.add(tool("publish_build_preview", "Publish the latest still-current validated plan into Builder's visual preview only. This never places or removes blocks; requires explicit user approval.", planIdentitySchema(), false,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                BridgeClient.BridgeReply result = bridge.publishPlan(args.get("validationToken").toString(),
                    longInteger(args, "worldGeneration"), longInteger(args, "snapshotRevision"));
                return textResult("Published to Builder preview only; Minecraft world unchanged.\n" + result.payload(), false);
            }));
        tools.add(tool("get_build_plan_status", "Check whether a plan inspection token is still valid for the current world snapshot.", identitySchema(), true,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments();
                BridgeClient.BridgeReply result = bridge.planStatus(longInteger(args, "worldGeneration"), longInteger(args, "snapshotRevision"));
                return textResult(result.payload(), !result.ok());
            }));
        tools.add(tool("list_plan_history", "List recent locally archived Builder plan revisions. Only plan JSON, snapshot evidence and previews are stored; no conversation transcript is copied.",
            objectSchema(Map.of("limit", Map.of("type", "integer", "minimum", 1, "maximum", PlanArchiveStore.MAX_RECORDS)), "limit"), true,
            (exchange, request) -> {
                try {
                    var records = archive.list(integer(request.arguments(), "limit"));
                    return textResult("Local plan archive: " + archiveLocation() + "\n" + PlanArchiveStore.encodeSummaries(records), false);
                } catch (java.io.IOException failure) { throw new IllegalStateException("Could not read local plan archive: " + failure.getMessage(), failure); }
            }));
        tools.add(tool("reinspect_archived_plan", "Load one archived plan by ID, bind it to the provided current snapshot, revalidate all exact preconditions, and archive the fresh inspection as a new revision. This does not publish it.",
            objectSchema(Map.of("archiveId", Map.of("type", "string", "format", "uuid"),
                "worldGeneration", Map.of("type", "integer", "minimum", 1), "snapshotRevision", Map.of("type", "integer", "minimum", 1)),
                "archiveId", "worldGeneration", "snapshotRevision"), false,
            (exchange, request) -> {
                Map<String, Object> args = request.arguments(); UUID id = UUID.fromString(args.get("archiveId").toString());
                PlanArchiveStore.Record record;
                try { record = archive.load(id).orElseThrow(() -> new IllegalArgumentException("Archived plan was not found")); }
                catch (java.io.IOException failure) { throw new IllegalStateException("Could not load archived plan: " + failure.getMessage(), failure); }
                BridgeClient.BridgeReply status = bridge.status();
                if (!status.ok() || status.worldGeneration() != longInteger(args, "worldGeneration")
                    || status.snapshotRevision() != longInteger(args, "snapshotRevision"))
                    throw new IllegalArgumentException("Current world/snapshot identity changed; read status again");
                String currentWorld = JsonParser.parseString(status.payload()).getAsJsonObject().get("world").getAsString();
                if (!record.worldIdentity().equals(currentWorld)) throw new IllegalArgumentException("Archived plan belongs to a different world or dimension");
                if (record.snapshotJson() == null) throw new IllegalArgumentException("This archived plan has no exact region snapshot and cannot be restored safely");
                JsonObject archivedBounds = JsonParser.parseString(record.snapshotJson()).getAsJsonObject();
                com.google.gson.JsonElement selectionElement = JsonParser.parseString(status.payload()).getAsJsonObject().get("selection");
                if (selectionElement == null || selectionElement.isJsonNull())
                    throw new IllegalArgumentException("Select and scan the archived region before reinspection");
                JsonObject currentSelection = selectionElement.getAsJsonObject();
                if (!archivedBounds.getAsJsonObject("min").equals(currentSelection.getAsJsonObject("min"))
                    || !archivedBounds.getAsJsonObject("max").equals(currentSelection.getAsJsonObject("max")))
                    throw new IllegalArgumentException("Archived plan belongs to different selection bounds");
                BridgeClient.PlanInspection inspection = bridge.inspectPlan(record.planJson(), status.worldGeneration(), status.snapshotRevision());
                String newId = archivePlan(bridge, archive, record.planJson(), inspection);
                return planInspectionResult(inspection, "Re-inspected archived plan; new archive revision " + newId + ". It was not published.");
            }));
        tools.add(tool("open_plan_viewer", "Return the private loopback URL for the local archived-plan viewer. It reads saved plans and never edits Minecraft or the archive.", emptySchema(), true,
            (exchange, request) -> viewerUrl == null ? textResult("Local plan viewer could not bind to 127.0.0.1:47654; check whether another process is using that port.", true)
                : textResult("Open the local Builder plan viewer: " + viewerUrl, false)));
        tools.add(tool("pair_local_minecraft", "Start one-time pairing, or resume waiting for the in-game decision. Open Minecraft Builder Settings and click Allow local companion, then call again if still pending.", emptySchema(), false,
            (exchange, request) -> {
                BridgeClient.PairResult result = bridge.pair(Duration.ofSeconds(5));
                return textResult(result.message(), "REJECTED".equals(result.state()) || "EXPIRED".equals(result.state()));
            }));
        return List.copyOf(tools);
    }

    private static McpSchema.CallToolResult planInspectionResult(BridgeClient.PlanInspection inspection, String archiveNote) {
        List<McpSchema.Content> content = new ArrayList<>();
        content.add(McpSchema.TextContent.builder("Plan validation against snapshot " + inspection.metadata().snapshotRevision()
            + " (world generation " + inspection.metadata().worldGeneration() + ")\n" + archiveNote + "\n" + inspection.metadata().payload()).build());
        if (inspection.previewPng() != null) content.add(McpSchema.ImageContent.builder(
            Base64.getEncoder().encodeToString(inspection.previewPng()), "image/png").build());
        return McpSchema.CallToolResult.builder().content(content).isError(false).build();
    }

    private static String archivePlan(BridgeClient bridge, PlanArchiveStore archive, String planJson,
                                      BridgeClient.PlanInspection inspection) {
        String world = "snapshot unavailable"; String snapshotJson = null;
        try {
            BridgeClient.BridgeReply before = bridge.status();
            if (before.ok() && before.worldGeneration() == inspection.metadata().worldGeneration()
                && before.snapshotRevision() == inspection.metadata().snapshotRevision()) {
                JsonObject state = JsonParser.parseString(before.payload()).getAsJsonObject(); world = state.get("world").getAsString();
                JsonObject selected = state.getAsJsonObject("selection");
                if (selected != null && !selected.isJsonNull()) {
                    BridgeClient.Bounds bounds = new BridgeClient.Bounds(coordinate(selected.getAsJsonObject("min")), coordinate(selected.getAsJsonObject("max")));
                    snapshotJson = bridge.readSelectionSnapshot(bounds, before.worldGeneration(), before.snapshotRevision());
                    BridgeClient.BridgeReply after = bridge.status();
                    if (!after.ok() || after.worldGeneration() != before.worldGeneration() || after.snapshotRevision() != before.snapshotRevision())
                        snapshotJson = null;
                }
            }
        } catch (RuntimeException ignored) { snapshotJson = null; }
        try {
            JsonObject contract = JsonParser.parseString(planJson).getAsJsonObject();
            UUID archiveId = UUID.randomUUID();
            var record = new PlanArchiveStore.Record(archiveId, contract.get("title").getAsString(), java.time.Instant.now(), world,
                inspection.metadata().worldGeneration(), inspection.metadata().snapshotRevision(), planJson,
                Objects.requireNonNullElse(inspection.metadata().payload(), "{}"), snapshotJson,
                inspection.previewPng() == null ? null : Base64.getEncoder().encodeToString(inspection.previewPng()));
            archive.save(record);
            return archiveId + (snapshotJson == null ? " (plan/report only; snapshot capture unavailable or changed)" : " (plan and bounded snapshot saved)");
        } catch (Exception failure) {
            return "archive unavailable: " + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    private static BridgeClient.Coordinate coordinate(JsonObject json) {
        return new BridgeClient.Coordinate(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
    }

    private static String archiveLocation() { return PlanArchiveStore.defaultDirectory().toString(); }

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
    private static Map<String, Object> planIdentitySchema() {
        return objectSchema(Map.of("validationToken", Map.of("type", "string", "minLength", 1, "maxLength", 128),
            "worldGeneration", integerSchema(), "snapshotRevision", integerSchema()), "validationToken", "worldGeneration", "snapshotRevision");
    }
    private static Map<String, Object> planInspectionSchema() {
        Map<String, Object> state = objectSchema(Map.of("id", Map.of("type", "string", "maxLength", 256),
            "properties", Map.of("type", "object", "additionalProperties", Map.of("type", "string", "maxLength", 128))), "id", "properties");
        Map<String, Object> cell = objectSchema(Map.of("x", Map.of("type", "integer"), "y", Map.of("type", "integer"), "z", Map.of("type", "integer")), "x", "y", "z");
        Map<String, Object> operation = Map.of("type", "object", "oneOf", List.of(
            objectSchema(Map.of("op", Map.of("const", "PLACE"), "position", cell, "target", Map.of("type", "string", "maxLength", 128)), "op", "position", "target"),
            objectSchema(Map.of("op", Map.of("const", "REPLACE"), "position", cell, "expected", state, "target", Map.of("type", "string", "maxLength", 128)), "op", "position", "expected", "target"),
            objectSchema(Map.of("op", Map.of("const", "REMOVE"), "position", cell, "expected", state), "op", "position", "expected")));
        Map<String, Object> stage = objectSchema(Map.of("id", Map.of("type", "string", "maxLength", 128),
            "title", Map.of("type", "string", "maxLength", 256), "after", Map.of("type", "array", "items", Map.of("type", "string", "maxLength", 128)),
            "operations", Map.of("type", "array", "items", operation, "maxItems", 32768)), "id", "title", "after", "operations");
        Map<String, Object> plan = objectSchema(Map.of("schemaVersion", Map.of("const", 1), "id", Map.of("type", "string", "format", "uuid"),
            "title", Map.of("type", "string", "maxLength", 256), "revision", Map.of("type", "integer", "minimum", 1),
            "palette", Map.of("type", "object", "minProperties", 1, "additionalProperties", state),
            "stages", Map.of("type", "array", "minItems", 1, "maxItems", 32768, "items", stage)),
            "schemaVersion", "id", "title", "revision", "palette", "stages");
        return objectSchema(Map.of("plan", plan, "worldGeneration", Map.of("type", "integer", "minimum", 1),
            "snapshotRevision", Map.of("type", "integer", "minimum", 1)), "plan", "worldGeneration", "snapshotRevision");
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

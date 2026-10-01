package dev.krul.builder.agent;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Fixed, read-only App Server tools exposed by the Builder client. */
public final class BuilderDynamicTools {
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    public static final int MAX_IMAGE_BYTES = 128 * 1024;
    private static final Set<String> NAMES = Set.of("list_block_mods", "search_blocks", "describe_block", "inspect_build_plan");
    @FunctionalInterface public interface Handler { JsonObject call(String name, JsonObject arguments, BooleanSupplier currentCall) throws Exception; }
    public interface TurnAwareHandler extends Handler {
        void beginTurn(AgentRequest request);
        Review review(dev.krul.builder.project.BuildPlan finalPlan);
        void endTurn();
    }
    public record Review(boolean attempted, boolean succeeded, String failure) {}
    private final Handler handler;

    public BuilderDynamicTools(Handler handler) { this.handler = Objects.requireNonNull(handler); }

    public static JsonArray definitions() {
        JsonArray tools = new JsonArray();
        tools.add(definition("list_block_mods", "List local block mod namespaces and counts. Read-only; no world data.", schema(Map.of("page", integer(0, 10_000)), List.of("page"))));
        tools.add(definition("search_blocks", "Search local block registry IDs and localized names. Read-only; return at most 20 results.", schema(Map.of("query", string(128), "mod", string(128), "page", integer(0, 10_000)), List.of("query", "page"))));
        tools.add(definition("describe_block", "Describe one exact local block registry ID and return one small inventory-model preview. Read-only; no world data.", schema(Map.of("id", string(256)), List.of("id"))));
        JsonObject plan = new PlanResponseCodec().outputSchema().getAsJsonObject("properties").getAsJsonObject("plan")
            .getAsJsonArray("anyOf").get(0).getAsJsonObject();
        tools.add(definition("inspect_build_plan", "Validate the proposed bounded plan and return a local isometric schematic with exact block-model appearance cues. Read-only. Call once for the draft and at most once after a revision.", schema(Map.of("plan", plan), List.of("plan"))));
        return tools;
    }

    public void beginTurn(AgentRequest request) { if (handler instanceof TurnAwareHandler aware) aware.beginTurn(request); }
    public Review review(dev.krul.builder.project.BuildPlan finalPlan) { return handler instanceof TurnAwareHandler aware
        ? aware.review(finalPlan) : new Review(false, false, "Plan preview tool is unavailable"); }
    public void endTurn() { if (handler instanceof TurnAwareHandler aware) aware.endTurn(); }

    public JsonObject dispatch(String name, JsonObject args) {
        return dispatch(name, args, () -> true);
    }
    public JsonObject dispatch(String name, JsonObject args, BooleanSupplier currentCall) {
        if (!NAMES.contains(name) || args == null || !validArguments(name, args)) return failure("Unsupported tool or invalid arguments.");
        try {
            if (!currentCall.getAsBoolean()) return failure("Tool call is stale or cancelled.");
            return validateResult(handler.call(name, args.deepCopy(), currentCall));
        }
        catch (Exception ignored) { return failure("Local block catalog request failed."); }
    }

    JsonObject validateResult(JsonObject result) {
        if (result == null || !result.has("success") || !result.get("success").isJsonPrimitive() || !result.getAsJsonPrimitive("success").isBoolean()
                || !result.has("contentItems") || !result.get("contentItems").isJsonArray()) return failure("Invalid local tool result.");
        JsonArray items = result.getAsJsonArray("contentItems"); int images = 0;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) return failure("Invalid local tool result.");
            JsonObject item = element.getAsJsonObject(); String type = string(item, "type");
            if ("inputText".equals(type)) {
                if (!item.has("text") || !item.get("text").isJsonPrimitive() || !item.getAsJsonPrimitive("text").isString()) return failure("Invalid local tool result.");
            } else if ("inputImage".equals(type)) {
                if (++images > 1 || !validImage(item)) return failure("Invalid local tool result.");
            } else return failure("Invalid local tool result.");
        }
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) return failure("Local tool result exceeds the size limit.");
        return result.deepCopy();
    }

    private static boolean validImage(JsonObject item) {
        String uri = string(item, "imageUrl");
        if (uri == null || !uri.startsWith("data:image/png;base64,")) return false;
        try {
            byte[] bytes = Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length()));
            if (bytes.length < 24 || bytes.length > MAX_IMAGE_BYTES) return false;
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            return b.getInt() == 0x89504e47 && b.getInt() == 0x0d0a1a0a && b.getInt(12) == 0x49484452
                    && b.getInt(16) > 0 && b.getInt(16) <= 256 && b.getInt(20) > 0 && b.getInt(20) <= 256;
        } catch (RuntimeException ex) { return false; }
    }

    private static boolean validArguments(String name, JsonObject a) {
        Set<String> expected = switch (name) {
            case "list_block_mods" -> Set.of("page");
            case "search_blocks" -> Set.of("query", "page", "mod");
            case "describe_block" -> Set.of("id");
            case "inspect_build_plan" -> Set.of("plan");
            default -> Set.of();
        };
        if (!expected.containsAll(a.keySet())) return false;
        if (name.equals("list_block_mods")) return integerArg(a, "page", 0, 10_000);
        if (name.equals("describe_block")) return stringArg(a, "id", 256, true);
        if (name.equals("inspect_build_plan")) return a.has("plan") && a.get("plan").isJsonObject()
            && a.get("plan").toString().getBytes(StandardCharsets.UTF_8).length <= PlanResponseCodec.MAX_RESPONSE_BYTES;
        return stringArg(a, "query", 128, true) && integerArg(a, "page", 0, 10_000)
                && (!a.has("mod") || stringArg(a, "mod", 128, true));
    }
    private static boolean stringArg(JsonObject a, String key, int max, boolean nonempty) {
        if (!a.has(key) || !a.get(key).isJsonPrimitive() || !a.getAsJsonPrimitive(key).isString()) return false;
        String v = a.get(key).getAsString(); return v.length() <= max && (!nonempty || !v.isBlank());
    }
    private static boolean integerArg(JsonObject a, String key, int min, int max) {
        if (!a.has(key) || !a.get(key).isJsonPrimitive() || !a.getAsJsonPrimitive(key).isNumber()) return false;
        try { double d = a.get(key).getAsDouble(); int i = a.get(key).getAsInt(); return d == i && i >= min && i <= max; }
        catch (RuntimeException ex) { return false; }
    }
    private static JsonObject definition(String name, String description, JsonObject schema) {
        JsonObject o = new JsonObject(); o.addProperty("type", "function"); o.addProperty("name", name); o.addProperty("description", description); o.add("inputSchema", schema); return o;
    }
    private static JsonObject schema(Map<String, JsonObject> properties, List<String> required) {
        JsonObject o = new JsonObject(); o.addProperty("type", "object"); JsonObject p = new JsonObject(); properties.forEach(p::add); o.add("properties", p);
        JsonArray r = new JsonArray(); required.forEach(r::add); o.add("required", r); o.addProperty("additionalProperties", false); return o;
    }
    private static JsonObject string(int max) { JsonObject o = new JsonObject(); o.addProperty("type", "string"); o.addProperty("maxLength", max); return o; }
    private static JsonObject integer(int min, int max) { JsonObject o = new JsonObject(); o.addProperty("type", "integer"); o.addProperty("minimum", min); o.addProperty("maximum", max); return o; }
    private static String string(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonPrimitive() && o.getAsJsonPrimitive(key).isString() ? o.get(key).getAsString() : null; }
    private static JsonObject failure(String message) { JsonObject text = new JsonObject(); text.addProperty("type", "inputText"); text.addProperty("text", message); JsonArray items = new JsonArray(); items.add(text); JsonObject result = new JsonObject(); result.addProperty("success", false); result.add("contentItems", items); return result; }
}

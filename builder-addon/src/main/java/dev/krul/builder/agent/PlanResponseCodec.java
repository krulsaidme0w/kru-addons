package dev.krul.builder.agent;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/** Treats the model's structured final message as untrusted data, never as chat text. */
public final class PlanResponseCodec {
    public static final int MAX_LINE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_OPERATIONS = 4096;
    public record Response(String message, Optional<BuildPlan> plan) {}

    public Response decode(String json, AgentRequest request, Predicate<StateKey> stateExists) throws IOException {
        Objects.requireNonNull(request); Objects.requireNonNull(stateExists);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) throw invalid("Response exceeds size limit");
        JsonObject root = readObject(json);
        fields(root, Set.of("message", "plan"));
        String message = string(required(root, "message"));
        JsonElement planElement = required(root, "plan");
        if (planElement.isJsonNull()) return new Response(message, Optional.empty());
        BuildPlan plan = plan(planElement, request);
        if (request.acceptedPlan().isPresent() && plan.revision() <= request.acceptedPlan().get().revision())
            throw invalid("Proposal revision must advance the accepted plan");
        var errors = PlanValidator.diagnose(plan, request.context().bounds(), stateExists);
        if (!errors.isEmpty()) throw invalid("Proposal failed validation: " + errors.getFirst());
        return new Response(message, Optional.of(plan));
    }

    /** Strict output format for a message and optional structured proposal. */
    public JsonObject outputSchema() {
        JsonObject property = closedObject(Map.of("name", primitive("string"), "value", primitive("string")), "name", "value");
        JsonObject state = closedObject(Map.of("id", primitive("string"), "properties", obj("type", "array", "items", property)), "id", "properties");
        JsonObject block = closedObject(Map.of("op", obj("enum", array("block")), "position", coordinateSchema(), "state", primitive("string")), "op", "position", "state");
        JsonObject fill = closedObject(Map.of("op", obj("enum", array("fill")), "min", coordinateSchema(),
            "max", coordinateSchema(), "state", primitive("string")), "op", "min", "max", "state");
        JsonObject operation = obj("anyOf", arr(block, fill));
        JsonObject paletteEntry = closedObject(Map.of("key", primitive("string"), "state", state), "key", "state");
        JsonObject stage = closedObject(Map.of("id", primitive("string"), "title", primitive("string"),
            "after", obj("type", "array", "items", primitive("string")), "operations", obj("type", "array", "items", operation)),
            "id", "title", "after", "operations");
        JsonObject plan = closedObject(Map.of("revision", primitive("integer"), "palette", obj("type", "array", "items", paletteEntry),
            "stages", obj("type", "array", "items", stage)), "revision", "palette", "stages");
        JsonObject root = obj("type", "object");
        root.add("properties", props(Map.of("message", primitive("string"), "plan", obj("anyOf",
            arr(plan, obj("type", "null"))))));
        root.add("required", array("message", "plan")); root.addProperty("additionalProperties", false);
        root.addProperty("strict", true);
        return root;
    }

    private static BuildPlan plan(JsonElement element, AgentRequest request) throws IOException {
        JsonObject root = object(element); fields(root, Set.of("revision", "palette", "stages"));
        long revision = integer(required(root, "revision"));
        JsonArray paletteEntries = array(required(root, "palette"));
        if (paletteEntries.size() > PlanValidator.MAX_TARGETS) throw invalid("Palette exceeds limit");
        var palette = new HashMap<String, StateKey>();
        for (JsonElement paletteElement : paletteEntries) {
            JsonObject entry = object(paletteElement); fields(entry, Set.of("key", "state"));
            String paletteKey = string(required(entry, "key"));
            if (paletteKey.isBlank() || paletteKey.length() > 128) throw invalid("Invalid palette key");
            JsonObject state = object(required(entry, "state")); fields(state, Set.of("id", "properties"));
            JsonArray properties = array(required(state, "properties"));
            if (properties.size() > 64) throw invalid("Too many state properties");
            var values = new TreeMap<String, String>();
            for (JsonElement propertyElement : properties) {
                JsonObject property = object(propertyElement); fields(property, Set.of("name", "value"));
                String name = string(required(property, "name"));
                if (values.putIfAbsent(name, string(required(property, "value"))) != null) throw invalid("Duplicate state property");
            }
            try {
                if (palette.putIfAbsent(paletteKey, new StateKey(string(required(state, "id")), values)) != null)
                    throw invalid("Duplicate palette key");
            }
            catch (IllegalArgumentException e) { throw invalid("Invalid palette state"); }
        }
        JsonArray stagesJson = array(required(root, "stages"));
        if (stagesJson.isEmpty() || stagesJson.size() > MAX_OPERATIONS) throw invalid("Invalid stage count");
        var stages = new ArrayList<BuildStage>();
        Set<Cell> allTargets = new HashSet<>(); int operationCount = 0;
        for (JsonElement stageElement : stagesJson) {
            JsonObject stage = object(stageElement); fields(stage, Set.of("id", "title", "after", "operations"));
            var after = new HashSet<String>();
            for (JsonElement dependency : array(required(stage, "after")))
                if (!after.add(string(dependency))) throw invalid("Duplicate stage dependency");
            var targets = new HashMap<Cell, StateKey>();
            for (JsonElement operationElement : array(required(stage, "operations"))) {
                if (++operationCount > MAX_OPERATIONS) throw invalid("Too many operations");
                JsonObject operation = object(operationElement);
                String kind = string(required(operation, "op"));
                String paletteKey = string(required(operation, "state"));
                StateKey state = palette.get(paletteKey);
                if (state == null) throw invalid("Operation references undeclared palette entry");
                if (kind.equals("block")) {
                    fields(operation, Set.of("op", "position", "state"));
                    add(targets, allTargets, coordinate(required(operation, "position")), state);
                } else if (kind.equals("fill")) {
                    fields(operation, Set.of("op", "min", "max", "state"));
                    Cell min = coordinate(required(operation, "min")), max = coordinate(required(operation, "max"));
                    long volume;
                    try { volume = Math.multiplyExact(Math.multiplyExact((long) max.x() - min.x() + 1,
                            (long) max.y() - min.y() + 1), (long) max.z() - min.z() + 1); }
                    catch (ArithmeticException e) { throw invalid("Fill volume overflow"); }
                    if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z() || volume < 1
                            || volume > PlanValidator.MAX_TARGETS)
                        throw invalid("Fill exceeds target limit or has reversed bounds");
                    for (long x = min.x(); x <= max.x(); x++) for (long y = min.y(); y <= max.y(); y++)
                        for (long z = min.z(); z <= max.z(); z++) add(targets, allTargets,
                            new Cell(Math.toIntExact(x), Math.toIntExact(y), Math.toIntExact(z)), state);
                } else throw invalid("Unsupported operation");
            }
            stages.add(new BuildStage(string(required(stage, "id")), string(required(stage, "title")), after, targets));
        }
        if (revision < 1) throw invalid("Proposal revision must be positive");
        return new BuildPlan(request.project(), revision, request.contextRevision(), stages);
    }

    private static void add(Map<Cell, StateKey> targets, Set<Cell> all, Cell cell, StateKey state) throws IOException {
        StateKey old = targets.putIfAbsent(cell, state);
        if (old != null && !old.equals(state)) throw invalid("Conflicting assignments within a stage");
        all.add(cell);
        if (all.size() > PlanValidator.MAX_TARGETS) throw invalid("Proposal exceeds target limit");
    }

    private static JsonObject readObject(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement result = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing structured response data");
            return object(result);
        } catch (IllegalStateException | IllegalArgumentException e) { throw invalid("Malformed structured response"); }
    }
    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw invalid("Response nesting limit exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject result = new JsonObject();
                while (reader.hasNext()) { String key = reader.nextName(); if (result.has(key)) throw invalid("Duplicate JSON field"); result.add(key, read(reader, depth + 1)); }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> { reader.beginArray(); JsonArray result = new JsonArray(); while (reader.hasNext()) result.add(read(reader, depth + 1)); reader.endArray(); yield result; }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("Unexpected JSON token");
        };
    }
    private static JsonElement required(JsonObject object, String key) throws IOException { if (!object.has(key)) throw invalid("Missing field " + key); return object.get(key); }
    private static void fields(JsonObject object, Set<String> allowed) throws IOException { for (String key : object.keySet()) if (!allowed.contains(key)) throw invalid("Unsupported field " + key); }
    private static JsonObject object(JsonElement value) throws IOException { if (value == null || !value.isJsonObject()) throw invalid("Expected object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value) throws IOException { if (value == null || !value.isJsonArray()) throw invalid("Expected array"); return value.getAsJsonArray(); }
    private static String string(JsonElement value) throws IOException { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string"); return value.getAsString(); }
    private static long integer(JsonElement value) throws IOException { try { if (!value.isJsonPrimitive()) throw invalid("Expected integer"); return new BigDecimal(value.getAsString()).longValueExact(); } catch (NumberFormatException | ArithmeticException e) { throw invalid("Invalid integer"); } }
    private static Cell coordinate(JsonElement value) throws IOException { JsonArray a = array(value); if (a.size() != 3) throw invalid("Coordinate must have three integers"); return new Cell(Math.toIntExact(integer(a.get(0))), Math.toIntExact(integer(a.get(1))), Math.toIntExact(integer(a.get(2)))); }
    private static IOException invalid(String message) { return new IOException(message); }
    private static JsonObject primitive(String type) { return obj("type", type); }
    private static JsonObject coordinateSchema() { return obj("type", "array", "items", primitive("integer"), "minItems", 3, "maxItems", 3); }
    private static JsonObject props(Map<String, JsonObject> properties) { JsonObject result = new JsonObject(); properties.forEach(result::add); return result; }
    private static JsonArray array(String... values) { JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result; }
    private static JsonObject closedObject(Map<String, JsonObject> properties, String... required) {
        JsonObject result = obj("type", "object"); result.add("properties", props(properties));
        result.add("required", array(required)); result.addProperty("additionalProperties", false); return result;
    }
    private static JsonArray arr(JsonObject... values) { JsonArray result = new JsonArray(); for (JsonObject value : values) result.add(value); return result; }
    private static JsonObject obj(Object... pairs) { JsonObject result = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) { String key = (String) pairs[i]; Object value = pairs[i + 1]; result.add(key, value instanceof JsonObject o ? o : value instanceof JsonArray a ? a : value instanceof Number n ? new JsonPrimitive(n) : value instanceof Boolean b ? new JsonPrimitive(b) : new JsonPrimitive(value.toString())); } return result; }
}

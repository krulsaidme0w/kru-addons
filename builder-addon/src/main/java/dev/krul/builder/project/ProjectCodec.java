package dev.krul.builder.project;

import com.google.gson.*;
import com.google.gson.stream.*;
import dev.krul.builder.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned blueprint only. Registry/world validation must be repeated on explicit reopen. */
public final class ProjectCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STRING = 4096;

    public String encode(BuildPlan plan) throws IOException {
        validate(plan);
        var root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("project", plan.project().toString());
        root.addProperty("revision", plan.revision());
        root.addProperty("contextRevision", plan.contextRevision());
        var stages = new JsonArray();
        for (var stage : plan.stages()) {
            var object = new JsonObject();
            object.addProperty("id", checked(stage.id())); object.addProperty("title", checked(stage.title()));
            var after = new JsonArray();
            for (String id : new TreeSet<>(stage.after())) after.add(checked(id));
            object.add("after", after);
            var targets = new JsonArray();
            for (var entry : stage.targets().entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z))).toList()) {
                var target = new JsonObject(); var cell = entry.getKey(); var state = entry.getValue();
                target.addProperty("x", cell.x()); target.addProperty("y", cell.y()); target.addProperty("z", cell.z());
                var stateJson = new JsonObject(); stateJson.addProperty("id", checked(state.id()));
                var properties = new JsonObject();
                if (state.properties().size() > 64) throw invalid("Too many state properties");
                for (var property : new TreeMap<>(state.properties()).entrySet())
                    properties.addProperty(checked(property.getKey()), checked(property.getValue()));
                stateJson.add("properties", properties); target.add("state", stateJson); targets.add(target);
            }
            object.add("targets", targets); stages.add(object);
        }
        root.add("stages", stages);
        String json = root.toString();
        // Enforce exactly the same bounded format for writes and reads.
        decode(json);
        return json;
    }

    public BuildPlan decode(String json) throws IOException {
        if (json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid("Project exceeds byte limit");
        rejectLiteralControls(json);
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            var root = object(read(reader, 0, new int[]{0}), "schemaVersion", "project", "revision", "contextRevision", "stages");
            if (number(root.get("schemaVersion")) != SCHEMA_VERSION) throw invalid("Unsupported project schema version");
            String identity = string(root.get("project"));
            UUID project = UUID.fromString(identity);
            if (!project.toString().equals(identity)) throw invalid("Noncanonical project UUID");
            var stages = new ArrayList<BuildStage>();
            int targetCount = 0;
            for (var value : array(root.get("stages"))) {
                var stage = object(value, "id", "title", "after", "targets");
                var after = new HashSet<String>();
                for (var dependency : array(stage.get("after")))
                    if (!after.add(string(dependency))) throw invalid("Duplicate dependency");
                var targets = new HashMap<Cell, StateKey>();
                for (var targetValue : array(stage.get("targets"))) {
                    if (++targetCount > PlanValidator.MAX_TARGETS) throw invalid("Too many targets");
                    var target = object(targetValue, "x", "y", "z", "state");
                    var cell = new Cell(coordinate(target.get("x")), coordinate(target.get("y")), coordinate(target.get("z")));
                    var state = object(target.get("state"), "id", "properties");
                    if (!state.get("properties").isJsonObject()) throw invalid("Expected properties object");
                    var properties = new TreeMap<String, String>();
                    var props = state.getAsJsonObject("properties");
                    if (props.size() > 64) throw invalid("Too many state properties");
                    for (var entry : props.entrySet()) properties.put(entry.getKey(), string(entry.getValue()));
                    if (targets.putIfAbsent(cell, new StateKey(string(state.get("id")), properties)) != null)
                        throw invalid("Duplicate target coordinate");
                }
                stages.add(new BuildStage(string(stage.get("id")), string(stage.get("title")), after, targets));
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON");
            var plan = new BuildPlan(project, number(root.get("revision")), number(root.get("contextRevision")), stages);
            validate(plan);
            return plan;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("Invalid project JSON", e);
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 16 || ++nodes[0] > 600_000) throw invalid("Project structure exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); var object = new JsonObject();
                while (reader.hasNext()) {
                    String name = checked(reader.nextName());
                    if (object.has(name)) throw invalid("Duplicate JSON field");
                    object.add(name, read(reader, depth + 1, nodes));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); var array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= PlanValidator.MAX_TARGETS) throw invalid("Array exceeds limit");
                    array.add(read(reader, depth + 1, nodes));
                }
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(checked(reader.nextString()));
            case NUMBER -> {
                String token = reader.nextString();
                if (!token.matches("0|-?[1-9][0-9]{0,18}")) throw invalid("Expected bounded integer");
                yield new JsonPrimitive(Long.parseLong(token));
            }
            default -> throw invalid("Unexpected JSON value");
        };
    }

    private static void rejectLiteralControls(String json) throws IOException {
        // Gson's non-lenient reader still accepts literal controls inside quoted strings.
        boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted && c < 0x20) throw invalid("Literal control character in JSON string");
            if (escaped) escaped = false;
            else if (quoted && c == '\\') escaped = true;
            else if (c == '"') quoted = !quoted;
        }
    }

    private static void validate(BuildPlan plan) throws IOException {
        // Relative nonnegative coordinates; no live world bounds or registry are consulted here.
        var errors = PlanValidator.diagnose(plan,
            new Bounds(new Cell(0, 0, 0), new Cell(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)), state -> true);
        if (!errors.isEmpty()) throw invalid("Invalid blueprint: " + errors.getFirst().code());
    }
    private static JsonObject object(JsonElement value, String... fields) throws IOException {
        if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of(fields)))
            throw invalid("Missing or unknown project fields");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value) throws IOException {
        if (!value.isJsonArray()) throw invalid("Expected array");
        return value.getAsJsonArray();
    }
    private static String string(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string");
        return checked(value.getAsString());
    }
    private static String checked(String value) throws IOException {
        if (value.length() > MAX_STRING) throw invalid("String exceeds limit");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw invalid("Unpaired Unicode surrogate");
            } else if (Character.isLowSurrogate(c)) throw invalid("Unpaired Unicode surrogate");
        }
        return value;
    }
    private static long number(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected integer");
        return value.getAsLong();
    }
    private static int coordinate(JsonElement value) throws IOException {
        long coordinate = number(value);
        if (coordinate < 0 || coordinate > Integer.MAX_VALUE) throw invalid("Invalid relative coordinate");
        return (int) coordinate;
    }
    private static IOException invalid(String message) { return new IOException(message); }
}

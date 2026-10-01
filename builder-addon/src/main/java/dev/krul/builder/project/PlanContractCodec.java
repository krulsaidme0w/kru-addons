package dev.krul.builder.project;

import com.google.gson.*;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict JSON v1 decoder/encoder. Unknown fields and ambiguous per-stage assignments fail closed. */
public final class PlanContractCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_PROPERTIES = 64;
    private static final Gson GSON = new Gson();

    public String encode(PlanContract plan) {
        Objects.requireNonNull(plan);
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", plan.id().toString()); root.addProperty("title", plan.title()); root.addProperty("revision", plan.revision());
        JsonObject palette = new JsonObject(); plan.palette().forEach((key, state) -> palette.add(key, state(state)));
        root.add("palette", palette);
        JsonArray stages = new JsonArray(); Set<String> priorStages = new HashSet<>(); int count = 0;
        for (var stage : plan.stages()) {
            if (!priorStages.add(stage.id())) throw invalid("Duplicate stage ID");
            for (String dependency : stage.after()) if (!priorStages.contains(dependency) || dependency.equals(stage.id())) throw invalid("Stage dependency must name a prior stage");
            JsonObject item = new JsonObject(); item.addProperty("id", stage.id()); item.addProperty("title", stage.title());
            JsonArray after = new JsonArray(); stage.after().forEach(after::add); item.add("after", after);
            JsonArray operations = new JsonArray(); Set<Cell> cells = new HashSet<>();
            for (var operation : stage.operations()) {
                if (++count > PlanValidator.MAX_TARGETS) throw invalid("Plan operation count exceeds limit");
                if (!cells.add(operation.position())) throw invalid("Duplicate cell assignment within stage");
                if (operation.target() != null && !plan.palette().containsKey(operation.target())) throw invalid("Unknown target palette key");
                operations.add(operation(operation));
            }
            item.add("operations", operations); stages.add(item);
        }
        root.add("stages", stages); String result = GSON.toJson(root);
        if (result.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid("Plan exceeds size limit");
        return result;
    }

    public PlanContract decode(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid("Plan is missing or exceeds size limit");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject(); fields(root, Set.of("schemaVersion", "id", "title", "revision", "palette", "stages"));
            if (integer(root.get("schemaVersion")) != SCHEMA_VERSION) throw invalid("Unsupported plan schema version");
            JsonObject paletteObject = root.getAsJsonObject("palette"); var palette = new TreeMap<String, StateKey>();
            if (paletteObject.size() > PlanValidator.MAX_TARGETS) throw invalid("Palette exceeds limit");
            paletteObject.entrySet().forEach(entry -> palette.put(entry.getKey(), readState(entry.getValue().getAsJsonObject())));
            JsonArray stagesJson = root.getAsJsonArray("stages");
            if (stagesJson.size() > PlanValidator.MAX_TARGETS) throw invalid("Too many stages");
            List<PlanContract.Stage> stages = new ArrayList<>(); Set<String> stageIds = new HashSet<>(); int operationCount = 0;
            for (JsonElement value : stagesJson) {
                JsonObject item = value.getAsJsonObject(); fields(item, Set.of("id", "title", "after", "operations"));
                String id = item.get("id").getAsString();
                if (!stageIds.add(id)) throw invalid("Duplicate stage ID");
                List<String> after = strings(item.getAsJsonArray("after"));
                for (String dependency : after) if (!stageIds.contains(dependency) || dependency.equals(id)) throw invalid("Dependency must reference a prior stage");
                JsonArray ops = item.getAsJsonArray("operations"); List<PlanOperation> operations = new ArrayList<>(); Set<Cell> cells = new HashSet<>();
                for (JsonElement operationValue : ops) {
                    if (++operationCount > PlanValidator.MAX_TARGETS) throw invalid("Plan operation count exceeds limit");
                    PlanOperation operation = readOperation(operationValue.getAsJsonObject());
                    if (!cells.add(operation.position())) throw invalid("Duplicate cell assignment within stage");
                    if (operation.target() != null && !palette.containsKey(operation.target())) throw invalid("Unknown target palette key");
                    operations.add(operation);
                }
                stages.add(new PlanContract.Stage(id, item.get("title").getAsString(), after, operations));
            }
            return new PlanContract(UUID.fromString(root.get("id").getAsString()), root.get("title").getAsString(),
                integer(root.get("revision")), palette, stages);
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("Malformed plan contract", malformed); }
    }

    private static JsonObject operation(PlanOperation operation) {
        JsonObject result = new JsonObject(); result.addProperty("op", operation.kind().name());
        result.add("position", cell(operation.position()));
        if (operation.expected() != null) result.add("expected", state(operation.expected()));
        if (operation.target() != null) result.addProperty("target", operation.target());
        return result;
    }
    private static PlanOperation readOperation(JsonObject json) {
        String rawKind = json.get("op").getAsString(); PlanOperation.Kind kind;
        try { kind = PlanOperation.Kind.valueOf(rawKind); } catch (IllegalArgumentException invalid) { throw invalid("Unsupported operation kind"); }
        Set<String> fields = switch (kind) {
            case PLACE -> Set.of("op", "position", "target");
            case REPLACE -> Set.of("op", "position", "expected", "target");
            case REMOVE -> Set.of("op", "position", "expected");
        };
        fields(json, fields);
        StateKey expected = json.has("expected") ? readState(json.getAsJsonObject("expected")) : null;
        String target = json.has("target") ? json.get("target").getAsString() : null;
        return new PlanOperation(kind, readCell(json.getAsJsonObject("position")), expected, target);
    }
    private static JsonObject state(StateKey key) {
        JsonObject result = new JsonObject(); result.addProperty("id", key.id()); JsonObject properties = new JsonObject();
        new TreeMap<>(key.properties()).forEach(properties::addProperty); result.add("properties", properties); return result;
    }
    private static StateKey readState(JsonObject json) {
        fields(json, Set.of("id", "properties")); JsonObject propertyJson = json.getAsJsonObject("properties");
        if (propertyJson.size() > MAX_PROPERTIES) throw invalid("Too many state properties");
        var properties = new TreeMap<String, String>(); propertyJson.entrySet().forEach(e -> properties.put(e.getKey(), e.getValue().getAsString()));
        return new StateKey(json.get("id").getAsString(), properties);
    }
    private static JsonObject cell(Cell value) {
        JsonObject result = new JsonObject(); result.addProperty("x", value.x()); result.addProperty("y", value.y()); result.addProperty("z", value.z()); return result;
    }
    private static Cell readCell(JsonObject json) {
        fields(json, Set.of("x", "y", "z"));
        return new Cell(Math.toIntExact(integer(json.get("x"))), Math.toIntExact(integer(json.get("y"))), Math.toIntExact(integer(json.get("z"))));
    }
    private static List<String> strings(JsonArray array) { List<String> result = new ArrayList<>(); for (JsonElement item : array) result.add(item.getAsString()); return result; }
    private static long integer(JsonElement value) { try { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected integer"); return new BigDecimal(value.getAsString()).longValueExact(); } catch (NumberFormatException | ArithmeticException error) { throw invalid("Invalid integer"); } }
    private static void fields(JsonObject json, Set<String> allowed) { for (String key : json.keySet()) if (!allowed.contains(key)) throw invalid("Unsupported field: " + key); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

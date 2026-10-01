package dev.krul.builder.archive;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.PlanValidator;
import dev.krul.builder.project.ProjectCodec;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Versioned bounded archive serialization. Plan payloads retain the existing strict plan codec. */
public final class ConversationArchiveCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_NODES = 1_200_000;
    private static final int MAX_STRING = 65_536;
    private final ProjectCodec plans = new ProjectCodec();

    public String encode(ConversationArchive archive) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("id", archive.id().toString());
        root.addProperty("title", archive.title());
        root.addProperty("createdAt", archive.createdAtEpochMillis());
        root.addProperty("updatedAt", archive.updatedAtEpochMillis());
        JsonArray entries = new JsonArray();
        for (ConversationEntry entry : archive.entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id().toString());
            item.addProperty("kind", entry.kind().name());
            item.addProperty("createdAt", entry.createdAtEpochMillis());
            item.addProperty("text", entry.text());
            item.add("planRevision", entry.planRevision().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
            item.add("diagnostic", entry.diagnostic().<JsonElement>map(this::encodeDiagnostic).orElse(JsonNull.INSTANCE));
            entries.add(item);
        }
        root.add("entries", entries);
        JsonArray history = new JsonArray();
        for (ConversationPlan item : archive.plans()) history.add(encodePlan(item));
        root.add("plans", history);
        return serializeBounded(root);
    }

    public ConversationArchive decode(String json) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid("Conversation archive exceeds byte limit");
        JsonObject root = object(parse(json), "schemaVersion", "id", "title", "createdAt", "updatedAt", "entries", "plans");
        if (number(root.get("schemaVersion")) != SCHEMA_VERSION) throw invalid("Unsupported conversation archive schema version");
        UUID id = uuid(string(root.get("id")));
        String title = string(root.get("title"));
        long createdAt = number(root.get("createdAt")), updatedAt = number(root.get("updatedAt"));
        JsonArray encodedEntries = array(root.get("entries")), encodedPlans = array(root.get("plans"));
        if (encodedEntries.size() > ConversationArchive.MAX_ENTRIES || encodedPlans.size() > ConversationArchive.MAX_PLANS)
            throw invalid("Conversation history exceeds limit");
        List<ConversationEntry> entries = new ArrayList<>(encodedEntries.size());
        for (JsonElement value : encodedEntries) entries.add(decodeEntry(value));
        List<ConversationPlan> history = new ArrayList<>(encodedPlans.size());
        for (JsonElement value : encodedPlans) history.add(decodePlan(value));
        try { return new ConversationArchive(id, title, createdAt, updatedAt, entries, history); }
        catch (IllegalArgumentException failure) { throw invalid("Invalid conversation archive", failure); }
    }

    private JsonObject encodeDiagnostic(BuilderDiagnostic diagnostic) {
        JsonObject item = new JsonObject();
        item.addProperty("category", diagnostic.category().name());
        item.addProperty("operation", diagnostic.operation());
        item.addProperty("cause", diagnostic.cause());
        item.addProperty("detail", diagnostic.detail());
        item.addProperty("nextAction", diagnostic.nextAction());
        item.addProperty("occurredAt", diagnostic.occurredAtEpochMillis());
        item.addProperty("occurrences", diagnostic.occurrences());
        return item;
    }

    private JsonObject encodePlan(ConversationPlan item) throws IOException {
        JsonObject result = new JsonObject();
        result.add("plan", JsonParser.parseString(plans.encode(item.plan())));
        result.addProperty("lifecycle", item.lifecycle().name());
        result.addProperty("contextSession", item.contextSession().toString());
        result.addProperty("contextRevision", item.contextRevision());
        result.add("sourceBounds", item.sourceBounds().<JsonElement>map(this::encodeBounds).orElse(JsonNull.INSTANCE));
        JsonArray diagnostics = new JsonArray();
        item.diagnostics().forEach(diagnostics::add);
        result.add("diagnostics", diagnostics);
        result.addProperty("visualReviewed", item.visualReviewed());
        result.add("visualReviewFailure", item.visualReviewFailure().<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
        return result;
    }

    private JsonObject encodeBounds(Bounds bounds) {
        JsonObject result = new JsonObject();
        result.add("min", encodeCell(bounds.min()));
        result.add("max", encodeCell(bounds.max()));
        return result;
    }

    private static JsonObject encodeCell(Cell cell) {
        JsonObject result = new JsonObject();
        result.addProperty("x", cell.x()); result.addProperty("y", cell.y()); result.addProperty("z", cell.z());
        return result;
    }

    private ConversationEntry decodeEntry(JsonElement value) throws IOException {
        if (value == null || !value.isJsonObject()) throw invalid("Expected object");
        JsonObject item = value.getAsJsonObject();
        Set<String> oldFields = Set.of("id", "kind", "createdAt", "text", "diagnostic");
        Set<String> newFields = Set.of("id", "kind", "createdAt", "text", "planRevision", "diagnostic");
        if (!item.keySet().equals(oldFields) && !item.keySet().equals(newFields))
            throw invalid("Unexpected conversation entry fields");
        ConversationEntry.Kind kind = enumValue(ConversationEntry.Kind.class, string(item.get("kind")));
        JsonElement encodedDiagnostic = item.get("diagnostic");
        var diagnostic = encodedDiagnostic.isJsonNull() ? java.util.Optional.<BuilderDiagnostic>empty()
            : java.util.Optional.of(decodeDiagnostic(encodedDiagnostic));
        var planRevision = !item.has("planRevision") || item.get("planRevision").isJsonNull() ? java.util.Optional.<Long>empty()
            : java.util.Optional.of(number(item.get("planRevision")));
        try {
            return new ConversationEntry(uuid(string(item.get("id"))), kind, number(item.get("createdAt")),
                string(item.get("text")), diagnostic, planRevision);
        } catch (IllegalArgumentException failure) { throw invalid("Invalid conversation entry", failure); }
    }

    private BuilderDiagnostic decodeDiagnostic(JsonElement value) throws IOException {
        JsonObject item = object(value, "category", "operation", "cause", "detail", "nextAction", "occurredAt", "occurrences");
        try {
            return new BuilderDiagnostic(enumValue(BuilderDiagnostic.Category.class, string(item.get("category"))),
                string(item.get("operation")), string(item.get("cause")), string(item.get("detail")),
                string(item.get("nextAction")), number(item.get("occurredAt")), integer(item.get("occurrences")));
        } catch (IllegalArgumentException failure) { throw invalid("Invalid conversation diagnostic", failure); }
    }

    private ConversationPlan decodePlan(JsonElement value) throws IOException {
        JsonObject item = object(value, "plan", "lifecycle", "contextSession", "contextRevision", "sourceBounds", "diagnostics", "visualReviewed", "visualReviewFailure");
        BuildPlan plan = plans.decode(item.get("plan").toString());
        java.util.Optional<Bounds> bounds = item.get("sourceBounds").isJsonNull() ? java.util.Optional.empty()
            : java.util.Optional.of(decodeBounds(item.get("sourceBounds")));
        JsonArray encodedDiagnostics = array(item.get("diagnostics"));
        if (encodedDiagnostics.size() > 256) throw invalid("Too many plan diagnostics");
        List<String> diagnostics = new ArrayList<>(encodedDiagnostics.size());
        for (JsonElement diagnostic : encodedDiagnostics) diagnostics.add(string(diagnostic));
        JsonElement reviewFailure = item.get("visualReviewFailure");
        try {
            return new ConversationPlan(plan, enumValue(ConversationPlan.Lifecycle.class, string(item.get("lifecycle"))),
                uuid(string(item.get("contextSession"))), number(item.get("contextRevision")), bounds, diagnostics,
                bool(item.get("visualReviewed")), reviewFailure.isJsonNull() ? java.util.Optional.empty()
                    : java.util.Optional.of(string(reviewFailure)));
        } catch (IllegalArgumentException failure) { throw invalid("Invalid conversation plan metadata", failure); }
    }

    private static Bounds decodeBounds(JsonElement value) throws IOException {
        JsonObject bounds = object(value, "min", "max");
        try { return new Bounds(decodeCell(bounds.get("min")), decodeCell(bounds.get("max"))); }
        catch (IllegalArgumentException | ArithmeticException failure) { throw invalid("Invalid plan source bounds", failure); }
    }

    private static Cell decodeCell(JsonElement value) throws IOException {
        JsonObject cell = object(value, "x", "y", "z");
        return new Cell(Math.toIntExact(number(cell.get("x"))), Math.toIntExact(number(cell.get("y"))), Math.toIntExact(number(cell.get("z"))));
    }

    private String serializeBounded(JsonObject root) throws IOException {
        String json = root.toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid("Conversation archive exceeds byte limit");
        return json;
    }

    private static JsonElement parse(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement value = read(reader, 0, new int[]{0});
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON data");
            return value;
        } catch (IllegalStateException | IllegalArgumentException failure) {
            throw invalid("Invalid conversation archive JSON", failure);
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > MAX_NODES) throw invalid("Conversation archive structure exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String key = checked(reader.nextName());
                    if (object.has(key)) throw invalid("Duplicate JSON field");
                    object.add(key, read(reader, depth + 1, nodes));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= PlanValidator.MAX_TARGETS)
                        throw invalid("Archive array exceeds limit");
                    array.add(read(reader, depth + 1, nodes));
                }
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(checked(reader.nextString()));
            case NUMBER -> {
                String value = reader.nextString();
                if (!value.matches("0|-?[1-9][0-9]{0,18}")) throw invalid("Expected bounded integer");
                try { yield new JsonPrimitive(Long.parseLong(value)); }
                catch (NumberFormatException failure) { throw invalid("Integer is outside range", failure); }
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("Unexpected JSON value");
        };
    }

    private static JsonObject object(JsonElement value, String... keys) throws IOException {
        if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of(keys)))
            throw invalid("Missing or unknown archive fields");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value) throws IOException {
        if (!value.isJsonArray()) throw invalid("Expected archive array");
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected archive string");
        return checked(value.getAsString());
    }

    private static String checked(String value) throws IOException {
        if (value.length() > MAX_STRING) throw invalid("Archive string exceeds limit");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid("Invalid Unicode surrogate");
            } else if (Character.isLowSurrogate(ch)) throw invalid("Invalid Unicode surrogate");
            else if (ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t')
                throw invalid("Unsupported control character in archive string");
        }
        return value;
    }

    private static long number(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected archive integer");
        try {
            String token = value.getAsString();
            if (!token.matches("0|-?[1-9][0-9]{0,18}")) throw invalid("Expected archive integer");
            return Long.parseLong(token);
        } catch (NumberFormatException failure) { throw invalid("Archive integer is outside range", failure); }
    }

    private static int integer(JsonElement value) throws IOException {
        long number = number(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw invalid("Archive integer is outside range");
        return (int) number;
    }

    private static boolean bool(JsonElement value) throws IOException {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Expected archive boolean");
        return value.getAsBoolean();
    }

    private static UUID uuid(String value) throws IOException {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw invalid("Noncanonical conversation UUID");
            return id;
        } catch (IllegalArgumentException failure) { throw invalid("Invalid conversation UUID", failure); }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) throws IOException {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException failure) { throw invalid("Unknown " + type.getSimpleName(), failure); }
    }

    private static IOException invalid(String message) { return new IOException(message); }
    private static IOException invalid(String message, Throwable cause) { return new IOException(message, cause); }
}

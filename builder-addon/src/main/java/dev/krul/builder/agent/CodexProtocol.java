package dev.krul.builder.agent;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.HashSet;

/** Bounded JSON-lines framing and strict object parsing for Codex App Server. */
public final class CodexProtocol {
    private CodexProtocol() {}

    /** Returns null only for clean EOF between frames. A partial final line is a protocol error. */
    public static JsonObject readFrame(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream(1024);
        while (true) {
            int value = input.read();
            if (value < 0) {
                if (bytes.size() == 0) return null;
                throw new IOException("Codex protocol ended inside a frame");
            }
            if (value == '\n') break;
            if (bytes.size() >= PlanResponseCodec.MAX_LINE_BYTES) throw new IOException("Codex protocol line exceeds 4 MiB");
            bytes.write(value);
        }
        byte[] line = bytes.toByteArray();
        int length = line.length;
        if (length > 0 && line[length - 1] == '\r') length--;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(line, 0, length)).toString();
            try (var reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                JsonElement element = read(reader, 0);
                if (reader.peek() != JsonToken.END_DOCUMENT || !element.isJsonObject()) throw new IOException("Codex frame must be one JSON object");
                return element.getAsJsonObject();
            }
        } catch (CharacterCodingException | IllegalStateException | IllegalArgumentException e) {
            throw new IOException("Malformed Codex JSON frame", e);
        }
    }

    public static String encodeRequest(long id, String method, JsonObject params) {
        var request = new JsonObject();
        request.addProperty("id", id); request.addProperty("method", method); request.add("params", params.deepCopy());
        return request.toString() + "\n";
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IOException("Codex frame nesting limit exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); JsonObject object = new JsonObject(); var keys = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!keys.add(key)) throw new IOException("Duplicate Codex frame field");
                    object.add(key, read(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> { reader.beginArray(); JsonArray array = new JsonArray(); while (reader.hasNext()) array.add(read(reader, depth + 1)); reader.endArray(); yield array; }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Unexpected Codex JSON token");
        };
    }
}

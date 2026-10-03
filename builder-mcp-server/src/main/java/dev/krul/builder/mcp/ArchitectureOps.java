package dev.krul.builder.mcp;

import com.google.gson.*;
import java.util.*;

/** Expands a small, deterministic architectural vocabulary into exact PLACE operations. */
public final class ArchitectureOps {
    public static final int MAX_OPERATIONS = 32_768;
    private ArchitectureOps() {}

    public static JsonObject expand(JsonObject input) {
        JsonArray source = input.getAsJsonArray("ops");
        if (source == null || source.size() > 128) throw new IllegalArgumentException("ops must contain 1..128 primitives");
        JsonArray output = new JsonArray(); Map<String, String> occupied = new HashMap<>();
        for (JsonElement element : source) {
            JsonObject op = element.getAsJsonObject(); String type = required(op, "type");
            String target = required(op, "target"); JsonObject from = point(op, "from"), to = point(op, "to");
            switch (type) {
                case "box" -> each(from, to, (x, y, z) -> add(output, occupied, x, y, z, target));
                case "floor" -> each(from, to, (x, y, z) -> add(output, occupied, x, from.get("y").getAsInt(), z, target));
                case "wall" -> perimeter(from, to, (x, y, z) -> add(output, occupied, x, y, z, target));
                case "line", "trim" -> line(from, to, (x, y, z) -> add(output, occupied, x, y, z, target));
                case "window" -> each(from, to, (x, y, z) -> add(output, occupied, x, y, z, target));
                default -> throw new IllegalArgumentException("Unsupported architecture op: " + type);
            }
            if (output.size() > MAX_OPERATIONS) throw new IllegalArgumentException("Architecture ops exceed 32,768 cells");
        }
        JsonObject result = new JsonObject(); result.add("operations", output); result.addProperty("count", output.size());
        result.addProperty("deduplicated", occupied.size()); return result;
    }

    private interface CellConsumer { void accept(int x, int y, int z); }
    private static void each(JsonObject from, JsonObject to, CellConsumer consumer) {
        int[] a = bounds(from, to); for (int x = a[0]; x <= a[3]; x++) for (int y = a[1]; y <= a[4]; y++) for (int z = a[2]; z <= a[5]; z++) consumer.accept(x, y, z);
    }
    private static void perimeter(JsonObject from, JsonObject to, CellConsumer consumer) {
        int[] a = bounds(from, to); for (int x = a[0]; x <= a[3]; x++) for (int y = a[1]; y <= a[4]; y++) for (int z = a[2]; z <= a[5]; z++)
            if (x == a[0] || x == a[3] || z == a[2] || z == a[5]) consumer.accept(x, y, z);
    }
    private static void line(JsonObject from, JsonObject to, CellConsumer consumer) {
        int[] a = bounds(from, to); boolean x = a[0] != a[3], y = a[1] != a[4], z = a[2] != a[5];
        if ((x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0) > 1) throw new IllegalArgumentException("line/trim must be axis-aligned");
        each(from, to, consumer);
    }
    private static int[] bounds(JsonObject from, JsonObject to) {
        int fx = from.get("x").getAsInt(), fy = from.get("y").getAsInt(), fz = from.get("z").getAsInt();
        int tx = to.get("x").getAsInt(), ty = to.get("y").getAsInt(), tz = to.get("z").getAsInt();
        return new int[]{Math.min(fx, tx), Math.min(fy, ty), Math.min(fz, tz), Math.max(fx, tx), Math.max(fy, ty), Math.max(fz, tz)};
    }
    private static void add(JsonArray output, Map<String, String> occupied, int x, int y, int z, String target) {
        String key = x + "," + y + "," + z; String previous = occupied.putIfAbsent(key, target);
        if (previous != null) {
            if (previous.equals(target)) return;
            throw new IllegalArgumentException("Architecture primitives overlap at " + key + "; split the change into stages");
        }
        JsonObject operation = new JsonObject(); operation.addProperty("op", "PLACE");
        JsonObject position = new JsonObject(); position.addProperty("x", x); position.addProperty("y", y); position.addProperty("z", z);
        operation.add("position", position); operation.addProperty("target", target); output.add(operation);
    }
    private static String required(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull() || object.get(key).getAsString().isBlank()) throw new IllegalArgumentException("Missing architecture op field: " + key);
        return object.get(key).getAsString();
    }
    private static JsonObject point(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) throw new IllegalArgumentException("Missing architecture op point: " + key);
        JsonObject point = object.getAsJsonObject(key); for (String axis : List.of("x", "y", "z")) if (!point.has(axis)) throw new IllegalArgumentException("Point missing " + axis);
        return point;
    }
}

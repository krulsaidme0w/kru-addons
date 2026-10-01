package dev.krul.builder.agent;

import com.google.gson.*;
import dev.krul.builder.model.*;
import dev.krul.builder.project.PlanValidator;

import java.io.IOException;
import java.util.*;

/** Compact exact voxel view for the model; runs advance along X, then Z, then Y. */
final class WorldSnapshotGeometry {
    private WorldSnapshotGeometry() {}

    static String encode(WorldSnapshot snapshot) throws IOException {
        if (snapshot.bounds().volume() > PlanValidator.MAX_TARGETS)
            throw new IOException("World snapshot bounds exceed the context limit");

        var states = snapshot.known().values().stream().distinct()
            .sorted(Comparator.comparing(StateKey::id).thenComparing(WorldSnapshotGeometry::propertiesKey)).toList();
        var codes = new HashMap<StateKey, String>();
        JsonArray palette = new JsonArray();
        for (int index = 0; index < states.size(); index++) {
            StateKey state = states.get(index);
            String code = Integer.toString(index, 36);
            codes.put(state, code);
            JsonObject entry = new JsonObject();
            entry.addProperty("code", code);
            entry.addProperty("id", state.id());
            JsonObject properties = new JsonObject();
            new TreeMap<>(state.properties()).forEach(properties::addProperty);
            entry.add("properties", properties);
            palette.add(entry);
        }

        Bounds bounds = snapshot.bounds();
        JsonObject root = new JsonObject();
        JsonObject range = new JsonObject();
        range.add("min", cell(bounds.min())); range.add("max", cell(bounds.max()));
        root.add("bounds", range);
        root.add("palette", palette);
        JsonArray rows = new JsonArray();
        for (long y = bounds.min().y(); y <= bounds.max().y(); y++) {
            for (long z = bounds.min().z(); z <= bounds.max().z(); z++) {
                var row = new StringBuilder("y=").append(y).append(" z=").append(z).append(": ");
                String previous = null;
                int run = 0;
                for (long x = bounds.min().x(); x <= bounds.max().x(); x++) {
                    Cell cell = new Cell(Math.toIntExact(x), Math.toIntExact(y), Math.toIntExact(z));
                    StateKey state = snapshot.known().get(cell);
                    String code = state == null ? (snapshot.unknown().contains(cell) ? "?" : ".") : codes.get(state);
                    if (code.equals(previous)) run++;
                    else {
                        if (previous != null) appendRun(row, previous, run);
                        previous = code;
                        run = 1;
                    }
                }
                if (previous != null) appendRun(row, previous, run);
                rows.add(row.toString());
            }
        }
        root.add("rows", rows);
        return root.toString();
    }

    private static String propertiesKey(StateKey state) { return new TreeMap<>(state.properties()).toString(); }
    private static JsonObject cell(Cell cell) {
        JsonObject result = new JsonObject();
        result.addProperty("x", cell.x()); result.addProperty("y", cell.y()); result.addProperty("z", cell.z());
        return result;
    }
    private static void appendRun(StringBuilder row, String code, int length) {
        if (row.charAt(row.length() - 1) != ' ') row.append(',');
        row.append(length).append('*').append(code);
    }
}

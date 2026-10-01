package dev.krul.builder.bridge;

import com.google.gson.*;
import dev.krul.builder.model.*;

/** Deterministic bounded encoding of an existing complete scan; never reads the world itself. */
public final class BridgeSnapshotPage {
    public static final int MAX_PAGE_SIZE = 1024;
    private BridgeSnapshotPage() {}

    public static String encode(WorldSnapshot snapshot, Bounds requested, int page, int pageSize) {
        if (snapshot == null || requested == null) throw new IllegalArgumentException("Snapshot and bounds are required");
        BridgeRequestValidator.validateSelection(requested, snapshot.bounds(), 32_768);
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new IllegalArgumentException("Page size is out of range");
        long total = requested.volume();
        long offset = (long) page * pageSize;
        if (page < 0 || offset >= total) throw new IllegalArgumentException("Page is outside available results");
        JsonObject root = new JsonObject();
        root.addProperty("snapshotRevision", snapshot.revision());
        root.addProperty("total", total);
        root.addProperty("offset", offset);
        JsonArray cells = new JsonArray();
        int knownCount = 0, unknownCount = 0;
        long end = Math.min(total, offset + pageSize);
        Cell min = requested.min();
        long sizeX = (long) requested.max().x() - min.x() + 1;
        long sizeZ = (long) requested.max().z() - min.z() + 1;
        for (long index = offset; index < end; index++) {
            long x = min.x() + index % sizeX;
            long z = min.z() + (index / sizeX) % sizeZ;
            long y = min.y() + index / (sizeX * sizeZ);
            Cell absolute = new Cell(Math.toIntExact(x), Math.toIntExact(y), Math.toIntExact(z));
            JsonObject entry = new JsonObject();
            entry.add("absolute", cell(absolute));
            entry.add("relative", cell(new Cell(absolute.x() - min.x(), absolute.y() - min.y(), absolute.z() - min.z())));
            StateKey state = snapshot.known().get(absolute);
            if (state == null) { entry.addProperty("unknown", true); unknownCount++; }
            else {
                entry.addProperty("unknown", false); entry.addProperty("id", state.id());
                JsonObject properties = new JsonObject();
                new java.util.TreeMap<>(state.properties()).forEach(properties::addProperty);
                entry.add("properties", properties); knownCount++;
            }
            cells.add(entry);
        }
        root.addProperty("known", knownCount); root.addProperty("unknown", unknownCount); root.add("cells", cells);
        return root.toString();
    }

    private static JsonObject cell(Cell cell) {
        JsonObject value = new JsonObject();
        value.addProperty("x", cell.x()); value.addProperty("y", cell.y()); value.addProperty("z", cell.z());
        return value;
    }
}

package dev.krul.builder.mcp.archive;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/** Bounded, atomic archive for plan proposals only; it intentionally has no conversation/chat fields. */
public final class PlanArchiveStore {
    public static final int MAX_PLAN_BYTES = 16 * 1024 * 1024;
    public static final int MAX_REPORT_BYTES = 256 * 1024;
    public static final int MAX_PREVIEW_BYTES = 192 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024;
    public static final int MAX_RECORD_BYTES = 96 * 1024 * 1024;
    public static final int MAX_RECORDS = 100;
    public static final long MAX_ARCHIVE_BYTES = 256L * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private record WireRecord(UUID id, String title, long createdAtEpochMillis, String worldIdentity,
                              long worldGeneration, long snapshotRevision, String planJson,
                              String validationJson, String snapshotJson, String previewPngBase64) {}

    public record Record(UUID id, String title, Instant createdAt, String worldIdentity,
                         long worldGeneration, long snapshotRevision, String planJson,
                         String validationJson, String snapshotJson, String previewPngBase64) {
        public Record {
            Objects.requireNonNull(id); Objects.requireNonNull(createdAt);
            title = bounded(title, 256, "title"); worldIdentity = bounded(worldIdentity, 512, "world identity");
            planJson = bounded(planJson, MAX_PLAN_BYTES, "plan");
            validationJson = bounded(validationJson, MAX_REPORT_BYTES, "validation report");
            if (snapshotJson != null && snapshotJson.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES)
                throw new IllegalArgumentException("Snapshot exceeds archive size limit");
            if (worldGeneration < 1 || snapshotRevision < 1) throw new IllegalArgumentException("Invalid snapshot identity");
            if (previewPngBase64 != null) {
                byte[] preview;
                try { preview = Base64.getDecoder().decode(previewPngBase64); }
                catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid preview encoding", invalid); }
                if (preview.length > MAX_PREVIEW_BYTES || preview.length < 8 || !png(preview)) throw new IllegalArgumentException("Preview exceeds limit or is not PNG");
            }
        }
        private static String bounded(String value, int max, String label) {
            if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max)
                throw new IllegalArgumentException("Invalid " + label);
            return value;
        }
    }

    public record Summary(UUID id, String title, Instant createdAt, String worldIdentity,
                          long worldGeneration, long snapshotRevision) {}

    private final Path directory;
    public static Path defaultDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        if (os.contains("win")) return Path.of(System.getenv().getOrDefault("LOCALAPPDATA", home.resolve("AppData/Local").toString())).toAbsolutePath().normalize().resolve("kru-builder/plan-archive");
        if (os.contains("mac")) return home.resolve("Library/Application Support/kru-builder/plan-archive");
        return Path.of(System.getenv().getOrDefault("XDG_DATA_HOME", home.resolve(".local/share").toString())).toAbsolutePath().normalize().resolve("kru-builder/plan-archive");
    }
    public PlanArchiveStore(Path directory) { this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize(); }

    public synchronized void save(Record record) throws IOException {
        Objects.requireNonNull(record);
        ensureDirectory(); Path target = file(record.id()); Path temporary = directory.resolve(record.id() + ".tmp");
        rejectLink(target); rejectLink(temporary);
        String json = GSON.toJson(toWire(record));
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RECORD_BYTES) throw new IOException("Plan archive record exceeds size limit");
        try {
            Files.deleteIfExists(temporary);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        trim(record.id());
    }

    public synchronized Optional<Record> load(UUID id) throws IOException {
        if (id == null) throw new IllegalArgumentException("Archive ID is required"); ensureDirectory(); Path path = file(id); rejectLink(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return read(path);
    }

    public synchronized List<Summary> list(int limit) throws IOException {
        if (limit < 1 || limit > MAX_RECORDS) throw new IllegalArgumentException("Archive page limit is out of range");
        ensureDirectory(); var records = new ArrayList<Summary>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.json"))
                    .limit(MAX_RECORDS * 2L).toList()) {
                if (Files.isSymbolicLink(path)) continue;
                try { readSummary(path).ifPresent(records::add); } catch (IOException | RuntimeException ignored) { /* isolate corrupt archive entries */ }
            }
        }
        records.sort(Comparator.comparing(Summary::createdAt).reversed().thenComparing(Summary::id));
        return List.copyOf(records.subList(0, Math.min(limit, records.size())));
    }

    public synchronized boolean delete(UUID id) throws IOException {
        if (id == null) throw new IllegalArgumentException("Archive ID is required"); ensureDirectory(); Path path = file(id); rejectLink(path);
        return Files.deleteIfExists(path);
    }

    public static String encode(Record record) { return GSON.toJson(toWire(record)); }
    public static String encodeSummaries(List<Summary> summaries) {
        JsonArray array = new JsonArray();
        for (Summary summary : summaries) {
            JsonObject item = new JsonObject(); item.addProperty("id", summary.id().toString()); item.addProperty("title", summary.title());
            item.addProperty("createdAtEpochMillis", summary.createdAt().toEpochMilli()); item.addProperty("worldIdentity", summary.worldIdentity());
            item.addProperty("worldGeneration", summary.worldGeneration()); item.addProperty("snapshotRevision", summary.snapshotRevision()); array.add(item);
        }
        return GSON.toJson(array);
    }

    private Optional<Record> read(Path path) throws IOException {
        rejectLink(path);
        long size = Files.size(path);
        if (size < 2 || size > MAX_RECORD_BYTES) throw new IOException("Invalid plan archive size");
        try {
            WireRecord wire = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), WireRecord.class);
            return Optional.ofNullable(wire).map(PlanArchiveStore::fromWire);
        } catch (JsonParseException | IllegalArgumentException malformed) { throw new IOException("Corrupt plan archive entry", malformed); }
    }

    private Optional<Summary> readSummary(Path path) throws IOException {
        rejectLink(path);
        long size = Files.size(path); if (size < 2 || size > MAX_RECORD_BYTES) return Optional.empty();
        UUID id = null; String title = null, world = null; long created = 0, generation = 0, revision = 0;
        try (var input = Files.newBufferedReader(path, StandardCharsets.UTF_8); var reader = new JsonReader(input)) {
            reader.beginObject();
            while (reader.hasNext()) switch (reader.nextName()) {
                case "id" -> id = UUID.fromString(reader.nextString());
                case "title" -> title = reader.nextString();
                case "createdAtEpochMillis" -> created = reader.nextLong();
                case "worldIdentity" -> world = reader.nextString();
                case "worldGeneration" -> generation = reader.nextLong();
                case "snapshotRevision" -> revision = reader.nextLong();
                default -> reader.skipValue();
            }
            reader.endObject();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) return Optional.empty();
            return id == null || title == null || world == null ? Optional.empty()
                : Optional.of(new Summary(id, title, Instant.ofEpochMilli(created), world, generation, revision));
        } catch (RuntimeException malformed) { throw new IOException("Corrupt plan archive entry", malformed); }
    }

    private void trim(UUID newest) throws IOException {
        var all = new ArrayList<Summary>(); long total = 0;
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> value.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.json")).toList()) {
                try { readSummary(path).ifPresent(all::add); total += Files.size(path); }
                catch (IOException | RuntimeException ignored) { }
            }
        }
        all.sort(Comparator.comparing(Summary::createdAt));
        while (!all.isEmpty() && (all.size() > MAX_RECORDS || total > MAX_ARCHIVE_BYTES)) {
            int index = all.size() > 1 && all.getFirst().id().equals(newest) ? 1 : 0;
            Summary old = all.remove(index);
            Path path = file(old.id()); long size = Files.size(path); if (Files.deleteIfExists(path)) total -= size;
        }
    }

    private void ensureDirectory() throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Plan archive directory is not a regular directory");
        } else Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) throw new IOException("Plan archive directory cannot be a symlink");
    }
    private Path file(UUID id) { return directory.resolve(id + ".json"); }
    private static WireRecord toWire(Record record) {
        return new WireRecord(record.id(), record.title(), record.createdAt().toEpochMilli(), record.worldIdentity(),
            record.worldGeneration(), record.snapshotRevision(), record.planJson(), record.validationJson(), record.snapshotJson(), record.previewPngBase64());
    }
    private static Record fromWire(WireRecord wire) {
        return new Record(wire.id(), wire.title(), Instant.ofEpochMilli(wire.createdAtEpochMillis()), wire.worldIdentity(),
            wire.worldGeneration(), wire.snapshotRevision(), wire.planJson(), wire.validationJson(), wire.snapshotJson(), wire.previewPngBase64());
    }
    private static void rejectLink(Path path) throws IOException { if (Files.isSymbolicLink(path)) throw new IOException("Plan archive entry cannot be a symlink"); }
    private static boolean png(byte[] bytes) {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }
}

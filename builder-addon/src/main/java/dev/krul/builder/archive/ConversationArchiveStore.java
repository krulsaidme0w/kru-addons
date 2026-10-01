package dev.krul.builder.archive;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Local archive storage. Callers own asynchronous scheduling. */
public final class ConversationArchiveStore {
    private static final int INDEX_SCHEMA = 1;
    private static final int MAX_INDEX_BYTES = 4096;
    private static final String INDEX_FILE = ".conversation-index.json";

    public record SaveResult(boolean nonAtomicFallback, List<IOException> cleanupWarnings) {
        public SaveResult { cleanupWarnings = List.copyOf(cleanupWarnings); }
    }
    public record LoadIssue(Optional<UUID> conversationId, String message) {
        public LoadIssue { conversationId = conversationId == null ? Optional.empty() : conversationId; Objects.requireNonNull(message); }
    }
    public record ListResult(List<ConversationSummary> conversations, List<LoadIssue> issues) {
        public ListResult { conversations = List.copyOf(conversations); issues = List.copyOf(issues); }
    }
    @FunctionalInterface interface Move { void move(Path from, Path to, CopyOption... options) throws IOException; }
    @FunctionalInterface interface Delete { void deleteIfExists(Path path) throws IOException; }

    private final Path root;
    private final Move move;
    private final Delete delete;
    private final ConversationArchiveCodec codec = new ConversationArchiveCodec();

    public ConversationArchiveStore(Path root) { this(root, Files::move, Files::deleteIfExists); }
    ConversationArchiveStore(Path root, Move move) { this(root, move, Files::deleteIfExists); }
    ConversationArchiveStore(Path root, Move move, Delete delete) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.move = Objects.requireNonNull(move);
        this.delete = Objects.requireNonNull(delete);
    }

    public synchronized ListResult list() throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new ListResult(List.of(), List.of());
        if (Files.isSymbolicLink(root)) throw new IOException("Conversation archive directory must not be a symbolic link");
        var summaries = new ArrayList<ConversationSummary>();
        var issues = new ArrayList<LoadIssue>();
        try (Stream<Path> paths = Files.list(root)) {
            for (Path path : paths.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".json") || name.equals(INDEX_FILE)) continue;
                String stem = name.substring(0, name.length() - 5);
                UUID id;
                try {
                    id = UUID.fromString(stem);
                    if (!id.toString().equals(stem)) continue;
                } catch (IllegalArgumentException invalidName) { continue; }
                try {
                    Optional<ConversationArchive> archive = load(id);
                    if (archive.isPresent()) summaries.add(ConversationSummary.from(archive.orElseThrow()));
                } catch (IOException failure) {
                    issues.add(new LoadIssue(Optional.of(id), "Conversation archive could not be loaded: " + safeMessage(failure)));
                }
            }
        }
        summaries.sort(Comparator.comparingLong(ConversationSummary::updatedAtEpochMillis).reversed()
            .thenComparing(summary -> summary.id().toString()));
        return new ListResult(summaries, issues);
    }

    public synchronized Optional<ConversationArchive> load(UUID id) throws IOException {
        Path file = path(id);
        rejectLink(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(ConversationArchiveCodec.MAX_BYTES + 1);
        } catch (NoSuchFileException missing) { return Optional.empty(); }
        if (bytes.length > ConversationArchiveCodec.MAX_BYTES) throw new IOException("Conversation archive exceeds byte limit");
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IOException("Conversation archive is not valid UTF-8", malformed);
        }
        ConversationArchive archive = codec.decode(json);
        if (!archive.id().equals(id)) throw new IOException("Conversation UUID does not match filename");
        return Optional.of(archive);
    }

    public synchronized SaveResult save(ConversationArchive archive) throws IOException {
        Objects.requireNonNull(archive);
        byte[] bytes = codec.encode(archive).getBytes(StandardCharsets.UTF_8);
        return write(path(archive.id()), bytes, ".conversation-", ConversationArchiveCodec.MAX_BYTES);
    }

    public synchronized void delete(UUID id) throws IOException {
        Path file = path(id);
        rejectLink(file);
        delete.deleteIfExists(file);
        if (selectedRaw().filter(id::equals).isPresent()) writeIndex(Optional.empty());
    }

    public synchronized Optional<UUID> selected() throws IOException {
        Optional<UUID> selected = selectedRaw();
        if (selected.isEmpty()) return Optional.empty();
        return load(selected.orElseThrow()).isPresent() ? selected : Optional.empty();
    }

    public synchronized void select(UUID id) throws IOException {
        Objects.requireNonNull(id);
        if (load(id).isEmpty()) throw new IOException("Cannot select a missing conversation");
        writeIndex(Optional.of(id));
    }

    private Optional<UUID> selectedRaw() throws IOException {
        Path file = root.resolve(INDEX_FILE);
        rejectLink(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_INDEX_BYTES + 1);
        } catch (NoSuchFileException missing) { return Optional.empty(); }
        if (bytes.length > MAX_INDEX_BYTES) throw new IOException("Conversation index exceeds byte limit");
        String text = decodeSelectedIndex(new String(bytes, StandardCharsets.UTF_8));
        if (text == null) return Optional.empty();
        try {
            UUID id = UUID.fromString(text);
            if (!id.toString().equals(text)) throw new IllegalArgumentException("Noncanonical UUID");
            return Optional.of(id);
        } catch (IllegalArgumentException failure) { throw new IOException("Conversation index UUID is invalid", failure); }
    }

    private static String decodeSelectedIndex(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new IOException("Conversation index is invalid");
            reader.beginObject();
            var fields = new java.util.HashSet<String>();
            String selected = null;
            boolean selectionPresent = false;
            int version = -1;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!fields.add(name)) throw new IOException("Duplicate conversation index field");
                switch (name) {
                    case "schemaVersion" -> {
                        if (reader.peek() != JsonToken.NUMBER) throw new IOException("Conversation index schema is invalid");
                        String value = reader.nextString();
                        if (!value.equals(Integer.toString(INDEX_SCHEMA))) throw new IOException("Unsupported conversation index schema");
                        version = INDEX_SCHEMA;
                    }
                    case "selected" -> {
                        selectionPresent = true;
                        if (reader.peek() == JsonToken.NULL) reader.nextNull();
                        else if (reader.peek() == JsonToken.STRING) selected = reader.nextString();
                        else throw new IOException("Conversation index selection is invalid");
                    }
                    default -> throw new IOException("Unknown conversation index field");
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || version != INDEX_SCHEMA || !selectionPresent)
                throw new IOException("Conversation index schema is invalid");
            return selected;
        } catch (IllegalStateException failure) { throw new IOException("Conversation index is invalid", failure); }
    }

    private void writeIndex(Optional<UUID> id) throws IOException {
        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", INDEX_SCHEMA);
        index.add("selected", id.<JsonElement>map(value -> new com.google.gson.JsonPrimitive(value.toString())).orElse(JsonNull.INSTANCE));
        write(root.resolve(INDEX_FILE), index.toString().getBytes(StandardCharsets.UTF_8), ".index-", MAX_INDEX_BYTES);
    }

    private SaveResult write(Path destination, byte[] bytes, String prefix, int maxBytes) throws IOException {
        if (bytes.length > maxBytes) throw new IOException("Conversation archive exceeds byte limit");
        Files.createDirectories(root);
        rejectLink(destination);
        Path temporary = Files.createTempFile(root, prefix, ".tmp");
        var warnings = new ArrayList<IOException>();
        Throwable primary = null;
        boolean fallback = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { move.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                replaceWithBackup(temporary, destination, warnings);
                fallback = true;
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally { cleanup(temporary, primary, warnings); }
        return new SaveResult(fallback, warnings);
    }

    private void replaceWithBackup(Path temporary, Path destination, List<IOException> warnings) throws IOException {
        Path backup = null;
        Throwable primary = null;
        try {
            rejectLink(destination);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                backup = Files.createTempFile(root, ".previous-", ".tmp");
                Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try { move.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException failure) {
                try {
                    if (backup != null) Files.copy(backup, destination, StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(destination);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                    backup = null;
                }
                throw failure;
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally { if (backup != null) cleanup(backup, primary, warnings); }
    }

    private void cleanup(Path file, Throwable primary, List<IOException> warnings) {
        try { delete.deleteIfExists(file); }
        catch (IOException failure) {
            if (primary == null) warnings.add(failure);
            else if (failure != primary) primary.addSuppressed(failure);
        }
    }

    private Path path(UUID id) { return root.resolve(Objects.requireNonNull(id).toString() + ".json"); }
    private static void rejectLink(Path file) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException("Conversation archive path must not be a symbolic link");
    }
    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "invalid archive data" : message;
    }
}

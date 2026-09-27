package dev.krul.builder.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public final class ProjectStore {
    /** Caller supplies instanceDirectory.resolve(DIRECTORY); no global/user path is inferred. */
    public static final String DIRECTORY = "kru-builder";
    /** A returned result means committed. Task7 must surface fallback and cleanup warnings separately. */
    public record SaveResult(boolean nonAtomicFallback, List<IOException> cleanupWarnings) {
        public SaveResult { cleanupWarnings = List.copyOf(cleanupWarnings); }
        public SaveResult(boolean nonAtomicFallback) { this(nonAtomicFallback, List.of()); }
    }
    @FunctionalInterface interface Move { void move(Path from, Path to, CopyOption... options) throws IOException; }
    @FunctionalInterface interface Delete { void deleteIfExists(Path path) throws IOException; }
    private final Path root;
    private final Move move;
    private final Delete delete;
    private final ProjectCodec codec = new ProjectCodec();

    public ProjectStore(Path root) { this(root, Files::move); }
    ProjectStore(Path root, Move move) {
        this(root, move, Files::deleteIfExists);
    }
    ProjectStore(Path root, Move move, Delete delete) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.move = Objects.requireNonNull(move);
        this.delete = Objects.requireNonNull(delete);
    }

    /** Blocking I/O on immutable input. The caller owns background scheduling and error presentation. */
    public synchronized SaveResult save(BuildPlan plan) throws IOException {
        byte[] bytes = codec.encode(plan).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(root);
        Path destination = path(plan.project());
        rejectLink(destination);
        Path temporary = Files.createTempFile(root, ".project-", ".tmp");
        var cleanupWarnings = new ArrayList<IOException>();
        Throwable primary = null;
        boolean nonAtomicFallback = false;
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                move.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                replaceWithBackup(temporary, destination, cleanupWarnings);
                nonAtomicFallback = true;
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally { cleanup(temporary, primary, cleanupWarnings); }
        return new SaveResult(nonAtomicFallback, cleanupWarnings);
    }

    private void replaceWithBackup(Path temporary, Path destination, List<IOException> cleanupWarnings) throws IOException {
        Path backup = null;
        Throwable primary = null;
        try {
            rejectLink(destination);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                backup = Files.createTempFile(root, ".previous-", ".tmp");
                Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                move.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                try {
                    if (backup != null) Files.copy(backup, destination, StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(destination);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                    // Keep the recovery copy if the filesystem also refuses restoration.
                    backup = null;
                }
                throw failure;
            }
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally { if (backup != null) cleanup(backup, primary, cleanupWarnings); }
    }

    private void cleanup(Path file, Throwable primary, List<IOException> warnings) {
        try { delete.deleteIfExists(file); }
        catch (IOException failure) {
            if (primary == null) warnings.add(failure);
            else if (failure != primary) primary.addSuppressed(failure);
        }
    }

    /** Empty means missing only; corruption, unsupported versions and I/O failures are errors. */
    public synchronized Optional<BuildPlan> load(UUID project) throws IOException {
        Path file = path(project);
        rejectLink(file);
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(ProjectCodec.MAX_BYTES + 1);
        } catch (NoSuchFileException missing) { return Optional.empty(); }
        if (bytes.length > ProjectCodec.MAX_BYTES) throw new IOException("Project exceeds byte limit");
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        BuildPlan plan = codec.decode(json);
        if (!plan.project().equals(project)) throw new IOException("Project UUID does not match filename");
        return Optional.of(plan);
    }

    private Path path(UUID project) { return root.resolve(Objects.requireNonNull(project) + ".json"); }
    private static void rejectLink(Path file) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException("Project file must not be a symbolic link");
    }
}

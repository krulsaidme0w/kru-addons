package dev.krul.builder.catalog;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Atomic app-owned cache. Invalid or stale data is ignored, never deleted on read. */
public final class BlockCatalogStore {
    private final Path file;
    private final BlockCatalogSnapshotCodec codec;

    public BlockCatalogStore(Path builderDirectory) {
        this.file = Objects.requireNonNull(builderDirectory).resolve("catalog").resolve("catalog-v1.json");
        this.codec = new BlockCatalogSnapshotCodec();
    }
    public Path path() { return file; }

    public Optional<BlockCatalogSnapshotCodec.Snapshot> load(BlockCatalogFingerprint expected) {
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > BlockCatalogSnapshotCodec.MAX_BYTES) return Optional.empty();
            var snapshot = codec.decode(Files.readAllBytes(file));
            return snapshot.fingerprint().sha256().equals(expected.sha256()) ? Optional.of(snapshot) : Optional.empty();
        } catch (IOException | RuntimeException unavailable) { return Optional.empty(); }
    }

    public void save(BlockCatalogSnapshotCodec.Snapshot snapshot) throws IOException { save(snapshot, () -> true); }
    public void save(BlockCatalogSnapshotCodec.Snapshot snapshot, BooleanSupplier current) throws IOException {
        Objects.requireNonNull(current);
        if (!current.getAsBoolean()) throw new CancellationException("Catalog generation is stale");
        byte[] bytes = codec.encode(snapshot);
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (!current.getAsBoolean()) throw new CancellationException("Catalog generation is stale");
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static final class CancellationException extends IOException {
        private static final long serialVersionUID = 1L;
        public CancellationException(String message) { super(message); }
    }
}

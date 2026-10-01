package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Small, symlink-safe, atomic store for one selection file per world/dimension context. */
public final class BoundsStore {
    private static final int MAX_FILE_BYTES = 128;
    private final Path directory;

    public BoundsStore(Path instanceDirectory) {
        directory = instanceDirectory.toAbsolutePath().normalize().resolve("kru-builder").resolve("bounds");
    }

    public Optional<Bounds> load(BoundsContext context) {
        try {
            Path file = safeFile(context);
            if (!Files.exists(file, NOFOLLOW_LINKS)) return Optional.empty();
            rejectLinkOrWrongType(file, false);
            byte[] bytes;
            try (SeekableByteChannel channel = Files.newByteChannel(file, Set.of(READ, NOFOLLOW_LINKS))) {
                ByteBuffer buffer = ByteBuffer.allocate(MAX_FILE_BYTES + 1);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
                if (buffer.position() > MAX_FILE_BYTES) return Optional.empty();
                bytes = new byte[buffer.position()];
                buffer.flip(); buffer.get(bytes);
            }
            return decode(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unsafeOrMalformed) {
            return Optional.empty();
        }
    }

    public void save(BoundsContext context, Bounds bounds) throws IOException {
        validate(bounds);
        ensureDirectory();
        Path target = file(context);
        rejectLinkOrWrongType(target, false);
        Path temporary = directory.resolve(".bounds-" + UUID.randomUUID() + ".tmp");
        byte[] data = encode(bounds).getBytes(StandardCharsets.UTF_8);
        try {
            try (SeekableByteChannel channel = Files.newByteChannel(temporary, Set.of(CREATE_NEW, WRITE, NOFOLLOW_LINKS))) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                if (channel instanceof java.nio.channels.FileChannel fileChannel) fileChannel.force(true);
            }
            ensureDirectory();
            rejectLinkOrWrongType(target, false);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void clear(BoundsContext context) throws IOException {
        Path target = safeFile(context);
        if (!Files.exists(target, NOFOLLOW_LINKS)) return;
        rejectLinkOrWrongType(target, false);
        Files.delete(target);
    }

    private Path safeFile(BoundsContext context) throws IOException {
        ensureDirectory();
        Path target = file(context);
        rejectLinkOrWrongType(target, false);
        return target;
    }

    private Path file(BoundsContext context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(context.storageKey().getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".bounds");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void ensureDirectory() throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.exists(current, NOFOLLOW_LINKS)) {
                rejectLinkOrWrongType(current, true);
            } else {
                try { Files.createDirectory(current); }
                catch (FileAlreadyExistsException raced) { rejectLinkOrWrongType(current, true); }
            }
        }
    }

    private static void rejectLinkOrWrongType(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Builder bounds storage refuses symbolic links");
        if (Files.exists(path, NOFOLLOW_LINKS)
            && (directory ? !Files.isDirectory(path, NOFOLLOW_LINKS) : !Files.isRegularFile(path, NOFOLLOW_LINKS)))
            throw new IOException("Unexpected Builder bounds storage entry");
    }

    private static void validate(Bounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("Bounds are required");
        try {
            if (bounds.volume() > ScanCursor.MAX_CELLS) throw new IllegalArgumentException("Selection exceeds 32768 cells");
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Selection exceeds 32768 cells", overflow);
        }
    }

    private static String encode(Bounds bounds) {
        return "v1\n" + bounds.min().x() + "\n" + bounds.min().y() + "\n" + bounds.min().z() + "\n"
            + bounds.max().x() + "\n" + bounds.max().y() + "\n" + bounds.max().z() + "\n";
    }

    private static Optional<Bounds> decode(String value) {
        String[] fields = value.split("\\n", -1);
        if (fields.length != 8 || !fields[0].equals("v1") || !fields[7].isEmpty()) return Optional.empty();
        try {
            Bounds bounds = new Bounds(new Cell(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3])),
                new Cell(Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), Integer.parseInt(fields[6])));
            validate(bounds);
            return Optional.of(bounds);
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    Path directory() { return directory; }
}

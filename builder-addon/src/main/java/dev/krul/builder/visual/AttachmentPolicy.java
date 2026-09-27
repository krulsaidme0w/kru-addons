package dev.krul.builder.visual;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Owns validated image copies and bounds attachment count, dimensions and encoded input size. */
public final class AttachmentPolicy implements AutoCloseable {
    public static final int MAX_EDGE = 1280, MAX_ATTACHMENTS = 4;
    public static final long MAX_ENCODED_BYTES = 8L * 1024 * 1024;
    private static final String PREFIX = "kru-builder-images-";
    private final Path root, store;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<Path> captures = new HashSet<>();
    private boolean closed;
    private boolean storeReleased;

    public record Attachment(UUID id, Path path, int width, int height, long encodedBytes) {}
    private static final class Entry {
        final Attachment attachment;
        int references;
        boolean removed;
        Entry(Attachment attachment) { this.attachment = attachment; }
    }

    public AttachmentPolicy(Path root) throws IOException {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        store = Files.createDirectory(this.root.resolve(PREFIX + ProcessHandle.current().pid() + "-" + UUID.randomUUID()));
        lockChannel = FileChannel.open(store.resolve(".owner.lock"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        lock = lockChannel.lock();
    }

    public synchronized Attachment add(Path source) {
        ensureOpen();
        Objects.requireNonNull(source, "source");
        if (entries.size() >= MAX_ATTACHMENTS) throw new IllegalArgumentException("At most four images are allowed");
        ImageFacts facts = validate(source);
        long total = entries.values().stream().mapToLong(e -> e.attachment.encodedBytes()).sum();
        if (facts.bytes > MAX_ENCODED_BYTES - total) throw new IllegalArgumentException("Combined image input exceeds 8 MiB");
        UUID id = UUID.randomUUID(); Path copy = store.resolve("image-" + id + ".png");
        try { Files.copy(source, copy); }
        catch (IOException e) { throw new IllegalArgumentException("Unable to copy image", e); }
        var attachment = new Attachment(id, copy, facts.width, facts.height, facts.bytes);
        entries.put(id, new Entry(attachment));
        return attachment;
    }

    public synchronized List<Attachment> draft() {
        return entries.values().stream().filter(e -> !e.removed).map(e -> e.attachment).toList();
    }

    public synchronized void remove(UUID id) {
        Entry entry = entries.get(Objects.requireNonNull(id));
        if (entry == null || entry.removed) return;
        entry.removed = true;
        deleteWhenUnused(entry);
    }

    /** Creates addon-owned PNG data; callers should add it before exposing it as an attachment. */
    public Path storeCapture(BufferedImage image) {
        Objects.requireNonNull(image);
        if (image.getWidth() < 1 || image.getHeight() < 1) throw new IllegalArgumentException("Empty capture");
        int width = image.getWidth(), height = image.getHeight();
        double scale = Math.min(1d, (double) MAX_EDGE / Math.max(width, height));
        int outWidth = Math.max(1, (int) Math.floor(width * scale));
        int outHeight = Math.max(1, (int) Math.floor(height * scale));
        BufferedImage bounded = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bounded.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, outWidth, outHeight, null);
        } finally { graphics.dispose(); }
        Path path;
        synchronized (this) { ensureOpen(); path = store.resolve("capture-" + UUID.randomUUID() + ".png"); captures.add(path); }
        try {
            if (!ImageIO.write(bounded, "png", path.toFile())) throw new IllegalStateException("PNG encoder unavailable");
            return path;
        } catch (IOException | RuntimeException e) {
            synchronized (this) { captures.remove(path); releaseStoreIfUnused(); }
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            throw new IllegalStateException("Capture encoding failed", e);
        }
    }

    public synchronized void releaseCapture(Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.getParent().equals(store) || !normalized.getFileName().toString().startsWith("capture-")) return;
        if (entries.values().stream().anyMatch(e -> e.attachment.path().equals(normalized))) return;
        captures.remove(normalized);
        try { Files.deleteIfExists(normalized); } catch (IOException ignored) { }
        releaseStoreIfUnused();
    }

    public synchronized RequestLease retainForRequest() {
        ensureOpen();
        List<Entry> retained = entries.values().stream().filter(e -> !e.removed).toList();
        retained.forEach(e -> e.references++);
        List<Path> paths = retained.stream().map(e -> e.attachment.path()).toList();
        return new RequestLease(this, retained, paths);
    }

    public static final class RequestLease implements AutoCloseable {
        private final AttachmentPolicy owner; private final List<Entry> entries; private final List<Path> paths;
        private boolean released;
        private RequestLease(AttachmentPolicy owner, List<Entry> entries, List<Path> paths) { this.owner = owner; this.entries = entries; this.paths = paths; }
        public List<Path> paths() { return paths; }
        public synchronized void releaseWhen(CompletableFuture<?> request) {
            if (released) throw new IllegalStateException("Lease already released");
            released = true;
            request.whenComplete((ignored, failure) -> owner.release(entries));
        }
        @Override public synchronized void close() { if (!released) { released = true; owner.release(entries); } }
    }

    private synchronized void release(List<Entry> retained) {
        for (Entry entry : retained) { if (entry.references > 0) entry.references--; deleteWhenUnused(entry); }
    }

    private void deleteWhenUnused(Entry entry) {
        if ((entry.removed || closed) && entry.references == 0) {
            try { Files.deleteIfExists(entry.attachment.path()); } catch (IOException ignored) { }
            entries.remove(entry.attachment.id());
            releaseStoreIfUnused();
        }
    }

    private record ImageFacts(int width, int height, long bytes) {}
    private static ImageFacts validate(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes <= 0 || bytes > MAX_ENCODED_BYTES) throw new IllegalArgumentException("Image input must be at most 8 MiB");
            try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
                if (input == null) throw new IllegalArgumentException("Unsupported image");
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image");
                ImageReader reader = readers.next();
                try {
                    if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IllegalArgumentException("Only PNG images are supported");
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE) throw new IllegalArgumentException("Image dimensions exceed 1280 pixels per edge");
                    reader.read(0); // Force full decode; metadata-only or truncated files are not accepted.
                    return new ImageFacts(width, height, bytes);
                } finally { reader.dispose(); }
            }
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Invalid or truncated PNG image", failure);
        }
    }

    public static void cleanupStale(Path root, Instant olderThan) {
        Objects.requireNonNull(root); Objects.requireNonNull(olderThan);
        try (var children = Files.list(root.toAbsolutePath().normalize())) {
            children.filter(path -> path.getFileName().toString().startsWith(PREFIX)).limit(32).forEach(path -> {
                try {
                    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return;
                    if (!Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(olderThan)) return;
                    Path marker = path.resolve(".owner.lock");
                    if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return;
                    try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.WRITE); FileLock acquired = tryLock(channel)) {
                        if (acquired == null) return;
                        try (var files = Files.list(path)) {
                            for (Path file : files.limit(16).toList()) {
                                if (!file.equals(marker) && !Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                                    && (file.getFileName().toString().startsWith("image-") || file.getFileName().toString().startsWith("capture-"))) Files.deleteIfExists(file);
                            }
                        }
                        Files.deleteIfExists(marker);
                    }
                    Files.deleteIfExists(path);
                } catch (IOException | OverlappingFileLockException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try { return channel.tryLock(); } catch (OverlappingFileLockException active) { return null; }
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Attachment policy is closed"); }

    private void releaseStoreIfUnused() {
        if (!closed || storeReleased || !entries.isEmpty() || !captures.isEmpty()) return;
        storeReleased = true;
        try {
            Files.deleteIfExists(store.resolve(".owner.lock"));
            Files.deleteIfExists(store);
        } catch (IOException ignored) { }
        try { lock.release(); lockChannel.close(); } catch (IOException ignored) { }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (Entry entry : List.copyOf(entries.values())) deleteWhenUnused(entry);
        releaseStoreIfUnused();
    }
}

package dev.krul.cobblemon.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.ByteBuffer;
import java.util.*;
import com.google.gson.JsonParser;

/** Explicit local profile selection. No server detection and no network I/O. */
public final class ProfileStore {
    private final RuleProfile.State state = new RuleProfile.State();
    private final Path root;
    private final Object rootKey;
    private final FileTime rootCreated;
    private String selected;

    /** Legacy arbitrary-path reader. No write root is implicitly chosen. */
    public ProfileStore() { root = null; rootKey = null; rootCreated = null; }

    /** Flat editor namespace of .json filenames, rooted in an explicitly supplied real directory. */
    public ProfileStore(Path profilesRoot) throws IOException {
        Path absolute = Objects.requireNonNull(profilesRoot).toAbsolutePath().normalize();
        checkDirectories(absolute, true);
        Files.createDirectories(absolute);
        checkDirectories(absolute, false);
        root = absolute.toRealPath();
        var attributes = attributes(root);
        rootKey = attributes.fileKey();
        // Windows BasicFileAttributes has no fileKey. Creation time supplements canonical-path/reparse checks.
        rootCreated = attributes.creationTime();
    }

    public record Result(boolean success, List<String> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
        static Result ok() { return new Result(true, List.of()); }
        static Result failure(String reason) { return new Result(false, List.of(reason)); }
    }
    public RuleProfile current() { return state.current(); }
    public void disable() { state.disable(); }
    public void load(Path file) {
        selected = null;
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) { state.fail("Profile exceeds 128 KiB"); return; }
            RuleProfile parsed = RuleProfile.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed.diagnostics().isEmpty() && parsed.sourceVersion() != 1)
                state.fail("Legacy profile reader supports version 1 only; use the editor for version 2");
            else state.replace(parsed);
        } catch (IOException | RuntimeException error) {
            state.fail("Cannot read profile: " + error.getMessage());
        }
    }

    /** Explicit selection clears the old active profile even if reading/validation fails. V1 is preview-only. */
    public ProfileDraft select(String name) {
        state.disable();
        selected = name;
        ProfileDraft draft = reload();
        if (!draft.valid()) state.fail(String.join("; ", draft.diagnostics()));
        else if (draft.requiresMigrationConfirmation()) state.fail("Version 1 migration requires confirmation");
        else state.replace(draft.validate());
        return draft;
    }

    /** Reads the selected file into a draft; never implicitly applies it. */
    public ProfileDraft reload() {
        try {
            return ProfileDraft.parse(new String(read(fileName(selected)), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException error) {
            return ProfileDraft.unavailable("Cannot read profile: " + error.getMessage());
        }
    }

    public Result apply(ProfileDraft draft, boolean confirmMigration) {
        Result checked = check(draft, confirmMigration);
        if (!checked.success()) return checked;
        state.replace(draft.validate());
        return Result.ok();
    }

    /** Saves without applying or changing selection. Atomic replacement has no non-atomic fallback. */
    public Result save(String name, ProfileDraft draft, boolean confirmMigration) {
        Result checked = check(draft, confirmMigration);
        if (!checked.success()) return checked;
        try {
            Path target = fileName(name);
            byte[] existing = checkedFile(target) != null ? read(target) : null;
            boolean legacyFile = isVersionOne(existing);
            if (legacyFile && !confirmMigration) return Result.failure("Version 1 backup and replacement require confirmation");
            if (legacyFile) {
                Path backup = root.resolve(name + ".v1.bak");
                if (checkedFile(backup) != null) {
                    if (!Arrays.equals(existing, read(backup))) return Result.failure("Existing v1 backup differs; refusing to overwrite it");
                } else {
                    // CREATE_NEW is exclusive: even a concurrently created link/backup is never followed or overwritten.
                    writeNew(backup, existing);
                }
            }
            Path temporary = root.resolve(".profile-" + UUID.randomUUID() + ".tmp");
            try {
                writeNew(temporary, draft.validate().canonicalJson().getBytes(StandardCharsets.UTF_8));
                checkedFile(temporary);
                checkedFile(target);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                if (checkedFile(temporary) != null) Files.delete(temporary);
            }
            return Result.ok();
        } catch (IOException | RuntimeException error) { return Result.failure("Cannot save profile: " + error.getMessage()); }
    }

    /** Creates a bundled starter profile once; never overwrites a user file. */
    public Result seedIfAbsent(String name, String json, boolean confirmMigration) {
        try {
            Path target = fileName(name);
            if (checkedFile(target) != null) return Result.ok();
            return save(name, ProfileDraft.parse(json), confirmMigration);
        } catch (IOException | RuntimeException error) {
            return Result.failure("Cannot seed profile: " + error.getMessage());
        }
    }

    private static Result check(ProfileDraft draft, boolean confirmed) {
        if (draft == null) return Result.failure("No draft");
        if (!draft.valid()) return new Result(false, draft.diagnostics());
        if (draft.requiresMigrationConfirmation() && !confirmed) return Result.failure("Version 1 migration requires confirmation");
        return Result.ok();
    }

    private static boolean isVersionOne(byte[] bytes) {
        if (bytes == null) return false;
        try {
            return RuleProfile.readVersion(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()) == 1;
        } catch (RuntimeException malformed) { return false; }
    }

    private Path fileName(String name) throws IOException {
        if (root == null) throw new IOException("Read-only store has no profiles root");
        if (name == null || name.isBlank() || name.chars().anyMatch(c -> c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0)
            || !name.endsWith(".json"))
            throw new IOException("Expected a single .json filename inside profiles root");
        String stem = name.split("\\.", 2)[0].stripTrailing().toUpperCase(Locale.ROOT);
        if (stem.matches("CON|PRN|AUX|NUL|CONIN\\$|CONOUT\\$|COM[1-9¹²³]|LPT[1-9¹²³]"))
            throw new IOException("Reserved Windows device filename");
        Path path = root.getFileSystem().getPath(name);
        if (path.isAbsolute() || path.getNameCount() != 1 || name.equals("..")) throw new IOException("Profile path escapes root");
        return root.resolve(path);
    }

    /** Portable checks cannot eliminate hostile concurrent ancestor replacement; callers own the local root. */
    private void checkRoot() throws IOException {
        if (root == null) throw new IOException("Read-only store has no profiles root");
        checkDirectories(root, false);
        var current = attributes(root);
        if (!root.equals(root.toRealPath()) || !Objects.equals(rootKey, current.fileKey())
            || !rootCreated.equals(current.creationTime())) throw new IOException("Profiles root changed");
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void checkDirectories(Path directory, boolean allowMissing) throws IOException {
        // Check from the filesystem root down, before accessing any child through an ancestor.
        List<Path> paths = new ArrayList<>();
        for (Path p = directory; p != null; p = p.getParent()) paths.add(p);
        Collections.reverse(paths);
        for (Path path : paths) {
            try {
                var a = attributes(path);
                if (!a.isDirectory() || a.isSymbolicLink() || a.isOther())
                    throw new IOException("Profiles path contains a link/reparse point or non-directory: " + path);
            } catch (NoSuchFileException missing) { if (!allowMissing) throw missing; }
        }
    }

    private BasicFileAttributes checkedFile(Path path) throws IOException {
        checkRoot();
        if (!root.equals(path.getParent())) throw new IOException("Profile path escapes root");
        try {
            var a = attributes(path);
            if (!a.isRegularFile() || a.isSymbolicLink() || a.isOther() || !path.toRealPath().getParent().equals(root))
                throw new IOException("Profile/backup must be a regular file, not a link/reparse point");
            return a;
        } catch (NoSuchFileException missing) { return null; }
    }

    private byte[] read(Path path) throws IOException {
        var before = checkedFile(path);
        if (before == null) throw new NoSuchFileException(path.toString());
        try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             var input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes(131073);
            if (bytes.length > 131072) throw new IOException("Profile exceeds 128 KiB");
            var after = checkedFile(path);
            if (after == null || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.creationTime().equals(after.creationTime()) || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || before.size() != after.size()) throw new IOException("Profile changed while reading");
            return bytes;
        }
    }

    private void writeNew(Path path, byte[] bytes) throws IOException {
        checkedFile(path);
        try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            if (channel instanceof FileChannel file) file.force(true);
        }
        checkedFile(path);
    }
}

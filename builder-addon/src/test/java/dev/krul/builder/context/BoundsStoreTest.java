package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class BoundsStoreTest {
    @TempDir Path temp;
    private final Bounds local = new Bounds(new Cell(-12, 60, 2), new Cell(12, 75, 19));
    private final Bounds remote = new Bounds(new Cell(1, 2, 3), new Cell(4, 5, 6));

    @Test void persistsByWorldAndDimensionWithoutCrossContextRestore() throws Exception {
        BoundsStore store = new BoundsStore(temp.resolve("instance"));
        BoundsContext overworld = BoundsContext.local("/saves/first", "minecraft:overworld");
        BoundsContext nether = BoundsContext.local("/saves/first", "minecraft:the_nether");
        BoundsContext otherSave = BoundsContext.local("/saves/second", "minecraft:overworld");
        store.save(overworld, local);
        store.save(nether, remote);

        assertEquals(local, store.load(overworld).orElseThrow());
        assertEquals(remote, store.load(nether).orElseThrow());
        assertTrue(store.load(otherSave).isEmpty());
    }

    @Test void distinguishesRemoteAddressesAndRejectsInvalidBounds() throws Exception {
        BoundsStore store = new BoundsStore(temp.resolve("instance"));
        BoundsContext one = BoundsContext.remote("example.org:25565", "minecraft:overworld");
        BoundsContext two = BoundsContext.remote("other.org:25565", "minecraft:overworld");
        store.save(one, remote);
        assertEquals(remote, store.load(one).orElseThrow());
        assertTrue(store.load(two).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.save(one,
            new Bounds(new Cell(0, 0, 0), new Cell(32768, 0, 0))));
    }

    @Test void malformedStoredDataIsIgnoredAndClearRemovesOnlyThatContext() throws Exception {
        BoundsStore store = new BoundsStore(temp.resolve("instance"));
        BoundsContext context = BoundsContext.remote("broken.test", "minecraft:overworld");
        Path file = fileFor(store, context);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "v1\n1\n2\nnot-an-int\n4\n5\n6\n", StandardCharsets.UTF_8);
        assertTrue(store.load(context).isEmpty());
        store.save(context, remote);
        store.clear(context);
        assertTrue(store.load(context).isEmpty());
    }

    @Test void refusesToFollowStorageSymlinks() throws Exception {
        BoundsStore store = new BoundsStore(temp.resolve("instance"));
        BoundsContext context = BoundsContext.remote("symlink.test", "minecraft:overworld");
        Path outside = temp.resolve("outside");
        Files.createDirectories(temp.resolve("instance"));
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(temp.resolve("instance/kru-builder"), outside);
        } catch (UnsupportedOperationException | SecurityException failure) {
            return;
        }
        assertThrows(Exception.class, () -> store.save(context, remote));
        try (var files = Files.list(outside)) { assertTrue(files.findAny().isEmpty()); }
    }

    private static Path fileFor(BoundsStore store, BoundsContext context) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(context.storageKey().getBytes(StandardCharsets.UTF_8));
        return store.directory().resolve(HexFormat.of().formatHex(hash) + ".bounds");
    }
}

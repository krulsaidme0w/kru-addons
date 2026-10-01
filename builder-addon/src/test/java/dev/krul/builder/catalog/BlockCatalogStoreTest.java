package dev.krul.builder.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCatalogStoreTest {
    @TempDir Path temp;
    @Test void persistsAtomicallyAndRejectsFingerprintMismatch() throws Exception {
        var store = new BlockCatalogStore(temp);
        var current = BlockCatalogFingerprint.create("1.21.1", "fabric", Map.of("a", "1"), List.of());
        var changed = BlockCatalogFingerprint.create("1.21.1", "fabric", Map.of("a", "2"), List.of());
        var snapshot = new BlockCatalogSnapshotCodec.Snapshot(current, List.of(new BlockCatalog.BlockEntry("a:block", "a", "Block", Map.of())));
        store.save(snapshot);
        assertEquals(snapshot, store.load(current).orElseThrow());
        assertTrue(store.load(changed).isEmpty());
    }

    @Test void treatsCorruptCacheAsUnavailableWithoutDeletingIt() throws Exception {
        var store = new BlockCatalogStore(temp);
        var current = BlockCatalogFingerprint.create("1.21.1", "fabric", Map.of(), List.of());
        Path cache = store.path(); java.nio.file.Files.createDirectories(cache.getParent());
        java.nio.file.Files.writeString(cache, "broken");
        assertTrue(store.load(current).isEmpty());
        assertEquals("broken", java.nio.file.Files.readString(cache));
    }
}

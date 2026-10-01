package dev.krul.builder.catalog;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCatalogSnapshotCodecTest {
    private final BlockCatalogSnapshotCodec codec = new BlockCatalogSnapshotCodec();
    private final BlockCatalogFingerprint fingerprint = BlockCatalogFingerprint.create("1.21.1", "fabric", Map.of("demo", "1"), List.of("vanilla"));

    @Test void roundTripsBoundedCatalogWithFingerprint() {
        var entry = new BlockCatalog.BlockEntry("demo:block", "demo", "Block", Map.of("axis", List.of("x", "y")));
        var snapshot = new BlockCatalogSnapshotCodec.Snapshot(fingerprint, List.of(entry));
        var decoded = codec.decode(codec.encode(snapshot));
        assertEquals(snapshot, decoded);
    }

    @Test void rejectsCorruptUnknownVersionAndOversizedSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{broken"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"schemaVersion\":2}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(" ".repeat(BlockCatalogSnapshotCodec.MAX_BYTES + 1)));
    }
}

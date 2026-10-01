package dev.krul.builder.catalog;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCatalogFingerprintTest {
    @Test void fingerprintIgnoresInputOrderButChangesWithModOrPackVersions() {
        var first = BlockCatalogFingerprint.create("1.21.1", "fabric-0.16", Map.of("zmod", "2", "amod", "1"), List.of("pack-b", "pack-a"));
        var reordered = BlockCatalogFingerprint.create("1.21.1", "fabric-0.16", Map.of("amod", "1", "zmod", "2"), List.of("pack-a", "pack-b"));
        var changed = BlockCatalogFingerprint.create("1.21.1", "fabric-0.16", Map.of("amod", "1", "zmod", "3"), List.of("pack-a", "pack-b"));
        assertEquals(first.sha256(), reordered.sha256());
        assertNotEquals(first.sha256(), changed.sha256());
        assertNotEquals(first.sha256(), BlockCatalogFingerprint.create("1.21.1", "fabric-0.16", Map.of("amod", "1", "zmod", "2"), List.of("pack-c")).sha256());
    }
}

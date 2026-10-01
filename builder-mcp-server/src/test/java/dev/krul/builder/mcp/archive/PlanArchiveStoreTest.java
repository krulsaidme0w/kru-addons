package dev.krul.builder.mcp.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlanArchiveStoreTest {
    @TempDir Path temp;

    @Test void atomicallyPersistsAndLoadsBoundedPlanRecords() throws Exception {
        var store = new PlanArchiveStore(temp.resolve("plans"));
        var record = new PlanArchiveStore.Record(UUID.randomUUID(), "Lake base", Instant.parse("2026-10-01T00:00:00Z"),
            "world-a", 4, 9, "{\"schemaVersion\":1}", "{\"valid\":true}", "{\"cells\":[]}", null);
        store.save(record);
        assertEquals(record, store.load(record.id()).orElseThrow());
        assertEquals(record.id(), store.list(10).getFirst().id());
        assertEquals("world-a", store.list(10).getFirst().worldIdentity());
        assertFalse(Files.exists(temp.resolve("plans").resolve(record.id() + ".tmp")));
    }

    @Test void ignoresCorruptFilesAndRejectsSymlinkedArchiveDirectory() throws Exception {
        Path directory = temp.resolve("plans"); var store = new PlanArchiveStore(directory);
        Files.createDirectories(directory); Files.writeString(directory.resolve(UUID.randomUUID() + ".json"), "not json");
        assertTrue(store.list(20).isEmpty());
        Path outside = temp.resolve("outside"); Files.createDirectories(outside);
        Path link = temp.resolve("linked"); Files.createSymbolicLink(link, outside);
        assertThrows(java.io.IOException.class, () -> new PlanArchiveStore(link).save(new PlanArchiveStore.Record(
            UUID.randomUUID(), "p", Instant.now(), "w", 1, 1, "{}", "{}", null, null)));
    }

    @Test void rejectsOversizedPlanAndUnsafeIdentifiers() {
        var store = new PlanArchiveStore(temp.resolve("plans"));
        assertThrows(IllegalArgumentException.class, () -> new PlanArchiveStore.Record(UUID.randomUUID(), "p", Instant.now(), "w", 1, 1,
            "x".repeat(PlanArchiveStore.MAX_PLAN_BYTES + 1), "{}", null, null));
        assertThrows(IllegalArgumentException.class, () -> store.load(null));
    }
}

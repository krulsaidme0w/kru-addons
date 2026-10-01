package dev.krul.builder.archive;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.BuildStage;
import dev.krul.builder.project.ProjectStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectMigrationTest {
    @TempDir Path temp;
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test void absentLegacyProjectDoesNothing() throws Exception {
        var archives = new ConversationArchiveStore(temp.resolve("chats"));
        assertEquals(LegacyProjectMigration.Result.ABSENT,
            LegacyProjectMigration.migrate(new ProjectStore(temp.resolve("old")), archives, ID, 10));
        assertTrue(archives.load(ID).isEmpty());
    }

    @Test void importsOnceAndNeverActivatesTheLegacyPlan() throws Exception {
        var old = new ProjectStore(temp.resolve("old"));
        var plan = plan(); old.save(plan);
        var archives = new ConversationArchiveStore(temp.resolve("chats"));
        assertEquals(LegacyProjectMigration.Result.IMPORTED, LegacyProjectMigration.migrate(old, archives, ID, 10));
        assertEquals(LegacyProjectMigration.Result.ALREADY_IMPORTED, LegacyProjectMigration.migrate(old, archives, ID, 11));
        var imported = archives.load(ID).orElseThrow();
        assertEquals("Recovered project", imported.title());
        assertEquals(plan, imported.plans().getFirst().plan());
        assertEquals(ConversationPlan.Lifecycle.SAVED, imported.plans().getFirst().lifecycle());
        assertFalse(imported.plans().getFirst().visualReviewed());
        assertTrue(imported.plans().getFirst().sourceBounds().isEmpty());
        assertTrue(imported.entries().getFirst().text().contains("revalidated against a fresh scan"));
        assertEquals(plan, old.load(ID).orElseThrow(), "legacy source stays intact until and after verified import");
    }

    @Test void existingConversationIsPreservedAndLegacyPlanIsAddedWithAnIdempotentMarker() throws Exception {
        var old = new ProjectStore(temp.resolve("old")); old.save(plan());
        var archives = new ConversationArchiveStore(temp.resolve("chats"));
        var current = new ConversationArchive(ID, "Current chat", 10, 10, List.of(new ConversationEntry(UUID.randomUUID(),
            ConversationEntry.Kind.USER, 10, "Existing messages stay", java.util.Optional.empty())), List.of());
        archives.save(current);
        assertEquals(LegacyProjectMigration.Result.IMPORTED, LegacyProjectMigration.migrate(old, archives, ID, 20));
        assertEquals(LegacyProjectMigration.Result.ALREADY_IMPORTED, LegacyProjectMigration.migrate(old, archives, ID, 21));
        var imported = archives.load(ID).orElseThrow();
        assertEquals("Current chat", imported.title());
        assertTrue(imported.entries().stream().anyMatch(entry -> entry.text().equals("Existing messages stay")));
        assertEquals(1, imported.plans().size());
    }

    @Test void corruptLegacyPlanDoesNotCreateAMigrationMarker() throws Exception {
        Path legacyRoot = temp.resolve("old"); java.nio.file.Files.createDirectories(legacyRoot);
        java.nio.file.Files.writeString(legacyRoot.resolve(ID + ".json"), "not a project");
        var archives = new ConversationArchiveStore(temp.resolve("chats"));
        assertThrows(java.io.IOException.class, () -> LegacyProjectMigration.migrate(new ProjectStore(legacyRoot), archives, ID, 10));
        assertTrue(archives.load(ID).isEmpty());
    }

    private static BuildPlan plan() {
        return new BuildPlan(ID, 2, 7, List.of(new BuildStage("base", "Base", Set.of(),
            Map.of(new Cell(0, 0, 0), new StateKey("minecraft:stone", Map.of())))));
    }
}

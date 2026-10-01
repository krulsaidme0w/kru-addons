package dev.krul.builder.archive;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConversationArchiveStoreTest {
    @TempDir Path root;
    private static final UUID FIRST = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static ConversationArchive archive(UUID id, String title, long updatedAt) {
        return new ConversationArchive(id, title, 1, updatedAt, List.of(), List.of());
    }

    @Test void savesListsLoadsAndRemembersSelectedConversationAcrossStoreInstances() throws Exception {
        var store = new ConversationArchiveStore(root);
        assertTrue(store.load(FIRST).isEmpty());
        assertFalse(store.save(archive(FIRST, "Lake house", 2)).nonAtomicFallback());
        store.save(archive(SECOND, "Tower", 3));
        store.select(FIRST);

        var reopened = new ConversationArchiveStore(root);
        assertEquals(Optional.of(FIRST), reopened.selected());
        assertEquals(List.of(SECOND, FIRST), reopened.list().conversations().stream().map(ConversationSummary::id).toList());
        assertEquals("Lake house", reopened.load(FIRST).orElseThrow().title());
    }

    @Test void oneCorruptArchiveIsReportedWithoutHidingValidConversations() throws Exception {
        var store = new ConversationArchiveStore(root);
        store.save(archive(FIRST, "Valid", 2));
        Files.writeString(root.resolve(SECOND + ".json"), "{broken");

        var result = store.list();
        assertEquals(List.of(FIRST), result.conversations().stream().map(ConversationSummary::id).toList());
        assertEquals(SECOND, result.issues().getFirst().conversationId().orElseThrow());
        assertTrue(result.issues().getFirst().message().contains("could not be loaded"));
    }

    @Test void rejectsOversizedArchiveAndSymbolicLinkWithoutReadingOutsideRoot() throws Exception {
        var store = new ConversationArchiveStore(root);
        Path outside = root.resolveSibling("builder-archive-outside.json");
        Files.writeString(outside, "outside-data");
        try {
            Files.writeString(root.resolve(FIRST + ".json"), " ".repeat(ConversationArchiveCodec.MAX_BYTES + 1));
            assertThrows(IOException.class, () -> store.load(FIRST));
            Files.delete(root.resolve(FIRST + ".json"));
            Files.createSymbolicLink(root.resolve(FIRST + ".json"), outside);
            assertThrows(IOException.class, () -> store.load(FIRST));
            assertEquals("outside-data", Files.readString(outside));
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void selectionRequiresAnExistingValidConversationAndDeleteClearsIt() throws Exception {
        var store = new ConversationArchiveStore(root);
        assertThrows(IOException.class, () -> store.select(FIRST));
        store.save(archive(FIRST, "Saved", 2));
        store.select(FIRST);
        store.delete(FIRST);
        assertTrue(store.selected().isEmpty());
        assertTrue(store.load(FIRST).isEmpty());
    }

    @Test void rejectsDuplicateOrUnknownSelectedIndexFields() throws Exception {
        var store = new ConversationArchiveStore(root);
        store.save(archive(FIRST, "Saved", 2));
        String index = "{\"schemaVersion\":1,\"selected\":\"" + FIRST + "\",\"selected\":null}";
        Files.writeString(root.resolve(".conversation-index.json"), index);
        assertThrows(IOException.class, store::selected);
        Files.writeString(root.resolve(".conversation-index.json"), "{\"schemaVersion\":1,\"selected\":null,\"extra\":true}");
        assertThrows(IOException.class, store::selected);
    }

    @Test void reportsAtomicMoveFallbackAndPreservesPriorArchiveWhenReplacementFails() throws Exception {
        var normal = new ConversationArchiveStore(root);
        normal.save(archive(FIRST, "Before", 2));
        var fallback = new ConversationArchiveStore(root, (from, to, options) -> {
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE))
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "test filesystem");
            Files.move(from, to, options);
        });
        assertTrue(fallback.save(archive(FIRST, "After", 3)).nonAtomicFallback());
        assertEquals("After", normal.load(FIRST).orElseThrow().title());

        var failed = new ConversationArchiveStore(root, (from, to, options) -> { throw new IOException("disk failure"); });
        assertThrows(IOException.class, () -> failed.save(archive(FIRST, "Lost", 4)));
        assertEquals("After", normal.load(FIRST).orElseThrow().title());
    }
}

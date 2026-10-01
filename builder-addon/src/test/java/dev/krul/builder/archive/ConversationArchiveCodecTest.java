package dev.krul.builder.archive;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.BuildStage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversationArchiveCodecTest {
    private static final UUID CHAT = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test void roundTripKeepsOrderedConversationEntriesAndPlanHistory() throws Exception {
        var diagnostic = new BuilderDiagnostic(BuilderDiagnostic.Category.INVALID_PROPOSAL,
            "proposal validation", "Target is outside the selection", "stage=roof cell=[11,4,2]",
            "Choose a target inside the selected bounds", 1_700_000_000_003L, 1);
        var plan = new BuildPlan(CHAT, 2, 8, List.of(new BuildStage("base", "Foundation", Set.of(),
            Map.of(new Cell(0, 0, 0), new StateKey("minecraft:stone", Map.of())))));
        var archive = new ConversationArchive(CHAT, "Lake house", 1_700_000_000_000L, 1_700_000_000_004L,
            List.of(
                new ConversationEntry(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), ConversationEntry.Kind.USER,
                    1_700_000_000_001L, "Build a small lake house", Optional.empty()),
                new ConversationEntry(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), ConversationEntry.Kind.FAILURE,
                    1_700_000_000_003L, diagnostic.cause(), Optional.of(diagnostic))),
            List.of(new ConversationPlan(plan, ConversationPlan.Lifecycle.REVISED, CHAT, 8,
                Optional.of(new Bounds(new Cell(-10, 40, 20), new Cell(10, 90, 40))), List.of("replaced unsupported chest"),
                true, Optional.empty())));

        var codec = new ConversationArchiveCodec();
        assertEquals(archive, codec.decode(codec.encode(archive)));
    }

    @Test void roundTripsAnEmptyNewConversation() throws Exception {
        var archive = new ConversationArchive(CHAT, "New chat", 12, 12, List.of(), List.of());
        var codec = new ConversationArchiveCodec();
        assertEquals(archive, codec.decode(codec.encode(archive)));
    }

    @Test void rejectsUnsupportedSchemaAndOversizedArchive() {
        var codec = new ConversationArchiveCodec();
        assertThrows(IOException.class, () -> codec.decode("{\"schemaVersion\":99}"));
        assertThrows(IOException.class, () -> codec.decode(" ".repeat(16 * 1024 * 1024 + 1)));
    }

    @Test void rejectsDuplicateFieldsAndMalformedPlanHistory() {
        var codec = new ConversationArchiveCodec();
        assertThrows(IOException.class, () -> codec.decode("{\"schemaVersion\":1,\"schemaVersion\":1}"));
    }

    @Test void supportsPlanTargetArraysLargerThanMessageHistoryLimit() throws Exception {
        var targets = IntStream.range(0, 11_000).boxed().collect(Collectors.toMap(
            x -> new Cell(x, 0, 0), x -> new StateKey("minecraft:stone", Map.of())));
        var plan = new BuildPlan(CHAT, 1, 3, List.of(new BuildStage("base", "Wide", Set.of(), targets)));
        var history = new ConversationPlan(plan, ConversationPlan.Lifecycle.PROPOSED, CHAT, 3,
            Optional.empty(), List.of(), false, Optional.empty());
        var archive = new ConversationArchive(CHAT, "Wide build", 1, 1, List.of(), List.of(history));

        assertEquals(11_000, new ConversationArchiveCodec().decode(new ConversationArchiveCodec().encode(archive))
            .plans().getFirst().plan().stages().getFirst().targets().size());
    }
}

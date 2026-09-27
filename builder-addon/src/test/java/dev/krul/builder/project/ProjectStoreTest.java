package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStoreTest {
    @TempDir Path tempDir;
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String JSON = """
        {"schemaVersion":1,"project":"12345678-1234-1234-1234-123456789abc","revision":2,"contextRevision":7,
         "stages":[{"id":"base","title":"Основа","after":[],"targets":[
         {"x":0,"y":1,"z":2,"state":{"id":"minecraft:oak_stairs","properties":{"facing":"north","waterlogged":"false"}}}]}]}
        """;
    private BuildPlan plan() {
        return new BuildPlan(ID, 2, 7, List.of(new BuildStage("base", "Основа", Set.of(),
            Map.of(new Cell(0, 1, 2), new StateKey("minecraft:oak_stairs", Map.of("facing", "north", "waterlogged", "false"))))));
    }
    private Path file() { return tempDir.resolve(ID + ".json"); }

    @Test void roundtripUsesOnlyPlanFieldsAndUuidFilename() throws Exception {
        var store = new ProjectStore(tempDir);
        assertTrue(store.load(ID).isEmpty());
        store.save(plan());
        assertEquals(plan(), new ProjectStore(tempDir).load(ID).orElseThrow());
        try (var paths = Files.list(tempDir)) { assertEquals(List.of(file()), paths.toList()); }
        var object = com.google.gson.JsonParser.parseString(Files.readString(file())).getAsJsonObject();
        assertEquals(Set.of("schemaVersion", "project", "revision", "contextRevision", "stages"), object.keySet());
        assertEquals(1, object.get("schemaVersion").getAsInt());
        assertEquals(plan(), new ProjectCodec().decode(JSON));
    }

    @Test void corruptionIsAnErrorNeverAnAbsentProject() throws Exception {
        for (String bad : List.of("", "{", "null", JSON + "{}", JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                JSON.replace("\"revision\":2", "\"revision\":2,\"revision\":3"),
                JSON.replace("\"revision\":2", "\"revision\":\"2\""),
                JSON.replace("\"revision\":2", "\"revision\":2.1"),
                JSON.replace("\"revision\":2", "\"revision\":9223372036854775808"),
                JSON.replace("\"revision\":2", "\"revision\":0"),
                JSON.replace("\"x\":0", "\"x\":-1"),
                JSON.replace("\"x\":0", "\"x\":2147483648"),
                JSON.replace("\"after\":[]", "\"after\":[\"missing\"]"),
                JSON.replace("\"targets\":", "\"world\":\"private\",\"targets\":"),
                JSON.replace("\"facing\":\"north\"", "\"facing\":false"),
                JSON.replace("minecraft:oak_stairs", "invalid"),
                JSON.replace(ID.toString(), "../../escape"), JSON.replace("\"title\":\"Основа\",", ""))) {
            Files.writeString(file(), bad);
            assertThrows(IOException.class, () -> new ProjectStore(tempDir).load(ID), bad);
        }
        Files.write(file(), new byte[]{(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> new ProjectStore(tempDir).load(ID));
    }

    @Test void rejectsOversizeDeepInputAndWrongIdentity() throws Exception {
        var codec = new ProjectCodec();
        assertThrows(IOException.class, () -> codec.decode("[".repeat(10000) + "]".repeat(10000)));
        assertThrows(IOException.class, () -> codec.decode(JSON.replace("Основа", "a".repeat(4097))));
        Files.writeString(file(), " ".repeat(8 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> new ProjectStore(tempDir).load(ID));
        Files.writeString(file(), JSON.replace(ID.toString(), UUID.randomUUID().toString()));
        assertThrows(IOException.class, () -> new ProjectStore(tempDir).load(ID));
    }

    @Test void invalidUnicodeSavePreservesPreviousInsteadOfSilentlyChangingPlan() throws Exception {
        Files.writeString(file(), JSON);
        var invalid = new BuildPlan(ID, 3, 7, List.of(new BuildStage("base", "\ud800", Set.of(), Map.of())));
        assertThrows(IOException.class, () -> new ProjectStore(tempDir).save(invalid));
        assertEquals(JSON, Files.readString(file()));
        assertThrows(IOException.class, () -> new ProjectCodec().decode(JSON.replace("Основа", "\\ud800")));
    }

    @Test void rejectsLiteralControlCharactersInsideJsonStrings() {
        var codec = new ProjectCodec();
        for (String control : List.of("\n", "\t", "\u0000", "\\\n"))
            assertThrows(IOException.class, () -> codec.decode(JSON.replace("Основа", "bad" + control + "title")));
    }

    @Test void duplicateTargetsStagesDependenciesAndOversizedCollectionsAreRejected() throws Exception {
        var codec = new ProjectCodec();
        var root = com.google.gson.JsonParser.parseString(JSON).getAsJsonObject();
        var stages = root.getAsJsonArray("stages");
        var targets = stages.get(0).getAsJsonObject().getAsJsonArray("targets");
        targets.add(targets.get(0).deepCopy());
        assertThrows(IOException.class, () -> codec.decode(root.toString()));
        targets.remove(1);
        stages.add(stages.get(0).deepCopy());
        assertThrows(IOException.class, () -> codec.decode(root.toString()));
        assertThrows(IOException.class, () -> codec.decode(JSON.replace("\"after\":[]", "\"after\":[\"base\"]")));
        assertThrows(IOException.class, () -> codec.decode(JSON.replace("\"after\":[]", "\"after\":[\"base\",\"base\"]")));
        assertThrows(IOException.class, () -> codec.decode(JSON.replace("\"after\":[]",
            "\"after\":[" + "\"base\",".repeat(32768) + "\"base\"]")));
    }

    @Test void replacementAndDependenciesAirAndEmptyPropertiesRoundtrip() throws Exception {
        var store = new ProjectStore(tempDir.resolve("kru-builder"));
        store.save(plan());
        var next = new BuildPlan(ID, 3, 8, List.of(plan().stages().getFirst(),
            new BuildStage("remove", "Remove 😀", Set.of("base"),
                Map.of(new Cell(2, 0, 0), new StateKey("minecraft:air", Map.of())))));
        store.save(next);
        assertEquals(next, store.load(ID).orElseThrow());
        var invalid = new BuildPlan(ID, 0, 8, next.stages());
        assertThrows(IOException.class, () -> store.save(invalid));
        assertEquals(next, store.load(ID).orElseThrow());
    }

    @Test void rejectsSymlinkProjectWithoutReadingOrOverwritingOutsideRoot() throws Exception {
        Path outside = tempDir.resolve("outside.json"), root = tempDir.resolve("kru-builder");
        Files.createDirectory(root); Files.writeString(outside, JSON);
        Files.createSymbolicLink(root.resolve(ID + ".json"), outside);
        var store = new ProjectStore(root);
        assertThrows(IOException.class, () -> store.load(ID));
        assertThrows(IOException.class, () -> store.save(plan()));
        assertEquals(JSON, Files.readString(outside));
    }

    @Test void failedReplacementKeepsPreviousAndCleansTemporaryFile() throws Exception {
        Files.writeString(file(), JSON);
        var store = new ProjectStore(tempDir, (from, to, options) -> { throw new IOException("disk failure"); });
        assertThrows(IOException.class, () -> store.save(plan()));
        assertEquals(JSON, Files.readString(file()));
        try (var paths = Files.list(tempDir)) { assertEquals(1, paths.count()); }
    }

    @Test void fallbackIsExplicitAndFailedFallbackRestoresPreviousContents() throws Exception {
        Files.writeString(file(), JSON);
        var store = new ProjectStore(tempDir, (from, to, options) -> {
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE))
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "test filesystem");
            Files.move(from, to, options);
        });
        assertTrue(store.save(plan()).nonAtomicFallback());
        assertEquals(plan(), store.load(ID).orElseThrow());
        String previous = Files.readString(file());
        var failing = new ProjectStore(tempDir, (from, to, options) -> {
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE))
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "test filesystem");
            Files.delete(to);
            throw new IOException("failure during non-atomic replacement");
        });
        assertThrows(IOException.class, () -> failing.save(plan()));
        assertEquals(previous, Files.readString(file()));
        try (var paths = Files.list(tempDir)) { assertEquals(1, paths.count()); }
    }
}

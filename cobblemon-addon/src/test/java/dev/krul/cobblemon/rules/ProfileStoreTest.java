package dev.krul.cobblemon.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {
    @TempDir Path directory;
    @Test void legacyReadRejectsDynamicV2AndClearsPreviouslyLoadedV1() throws Exception {
        Path file = directory.resolve("profile.json");
        Files.writeString(file, ProfileEditorTest.V1);
        var store = new ProfileStore();
        store.load(file);
        assertEquals(1, store.current().size());
        Files.writeString(file, VersionTwoRulesTest.json());
        store.load(file);
        assertEquals(0, store.current().size());
        assertFalse(store.current().diagnostics().isEmpty());
        var editor = new ProfileStore(directory);
        assertTrue(editor.select("profile.json").valid());
        assertEquals("sixth", editor.current().match(java.util.Map.of("level", 85)).orElseThrow().target());
    }
    @Test void missingAndOversizedFilesClearThePreviouslySelectedProfile() throws Exception {
        Path file = directory.resolve("profile.json");
        Files.writeString(file, """
            {"version":1,"rules":[{"id":"box","target":"CHEST","label":"Box","all":[{"field":"kind","equals":"BLOCK_ENTITY"}]}]}
            """);
        ProfileStore store = new ProfileStore();
        store.load(file);
        assertEquals(1, store.current().size());
        Files.writeString(file, " ".repeat(131073));
        store.load(file);
        assertEquals(0, store.current().size());
        assertFalse(store.current().diagnostics().isEmpty());
        store.load(directory.resolve("missing.json"));
        assertEquals(0, store.current().size());
        assertFalse(store.current().diagnostics().isEmpty());
        store.disable();
        assertTrue(store.current().diagnostics().isEmpty());
    }

    @Test void bundledSeedCreatesOnceAndNeverOverwritesUserProfile() throws Exception {
        var store = new ProfileStore(directory);
        assertTrue(store.seedIfAbsent("starter.json", ProfileEditorTest.V1, true).success());
        String userVersion = Files.readString(directory.resolve("starter.json"));
        assertTrue(userVersion.contains("\"version\":2"));
        Files.writeString(directory.resolve("starter.json"), "user-owned");
        assertTrue(store.seedIfAbsent("starter.json", ProfileEditorTest.V1, true).success());
        assertEquals("user-owned", Files.readString(directory.resolve("starter.json")));
    }
}

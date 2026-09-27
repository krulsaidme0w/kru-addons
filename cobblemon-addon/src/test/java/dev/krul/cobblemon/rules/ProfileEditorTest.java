package dev.krul.cobblemon.rules;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileEditorTest {
    @TempDir Path directory;
    @Test void portableNamespaceRejectsWindowsStreamsDevicesAndControlCharacters() throws Exception {
        var store = new ProfileStore(directory);
        var draft = ProfileDraft.parse(VersionTwoRulesTest.json());
        for (String name : List.of("safe.json:stream.json", "NUL.json", "con.json", "COM1.json", "LPT².json",
            "CONIN$.json", "file?.json", "bad\nname.json", "bad\n\nname.json")) {
            assertFalse(store.save(name, draft, false).success(), name);
            assertFalse(store.select(name).valid(), name);
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void changedRootAndSymlinkAncestorCannotRedirectPortableIo() throws Exception {
        Path root = directory.resolve("profiles");
        var store = new ProfileStore(root);
        Files.move(root, directory.resolve("original"));
        Files.createDirectory(root);
        Files.setAttribute(root, "basic:creationTime", java.nio.file.attribute.FileTime.fromMillis(1000));
        assertFalse(store.save("new.json", ProfileDraft.parse(VersionTwoRulesTest.json()), false).success());
        assertFalse(Files.exists(root.resolve("new.json")));
        Path linked = directory.resolve("linked");
        Files.createSymbolicLink(linked, root);
        assertThrows(java.io.IOException.class, () -> new ProfileStore(linked.resolve("child")));
        assertFalse(Files.exists(root.resolve("child")));
    }
    @Test void everyExactNumericVersionOneRequiresConfirmationAndByteExactBackupEvenWithInvalidRules() throws Exception {
        var store = new ProfileStore(directory);
        var draft = ProfileDraft.parse(VersionTwoRulesTest.json());
        int index = 0;
        for (String version : List.of("1", "1.0", "1e0", "1.00e+0")) {
            for (boolean validRules : List.of(true, false)) {
                String source = V1.replace("\"version\":1", "\"version\":" + version);
                if (!validRules) source = source.replace("CHEST", "BAD_TARGET");
                String name = "old" + index++ + ".json";
                Path file = directory.resolve(name);
                Files.writeString(file, source);
                assertFalse(store.save(name, draft, false).success(), version);
                assertEquals(source, Files.readString(file));
                assertFalse(Files.exists(directory.resolve(name + ".v1.bak")));
                assertTrue(store.save(name, draft, true).success(), version);
                assertEquals(source, Files.readString(directory.resolve(name + ".v1.bak")));
            }
        }
    }
    static final String V1 = """
        {"version":1,"rules":[{"id":"old","target":"CHEST","label":"Box",
          "all":[{"field":"kind","equals":"BLOCK_ENTITY"}]}]}
        """;

    @Test void validatePreviewAndSaveDoNotApplyAndInvalidDraftPreservesActiveAndFile() throws Exception {
        var store = new ProfileStore(directory.resolve("profiles"));
        var draft = ProfileDraft.parse(VersionTwoRulesTest.json());
        assertTrue(draft.valid());
        assertEquals(RuleProfile.Truth.UNKNOWN, draft.preview(Map.of()).getFirst().truth());
        assertEquals(0, store.current().size());
        assertTrue(store.save("new.json", draft, false).success());
        String saved = Files.readString(directory.resolve("profiles/new.json"));
        assertEquals(0, store.current().size());
        assertTrue(store.apply(draft, false).success());
        var active = store.current();
        var invalid = ProfileDraft.parse("{");
        assertFalse(store.apply(invalid, false).success());
        assertFalse(store.save("new.json", invalid, false).success());
        assertSame(active, store.current());
        assertEquals(saved, Files.readString(directory.resolve("profiles/new.json")));
        assertTrue(store.select("new.json").valid());
        Files.writeString(directory.resolve("profiles/new.json"), "{");
        active = store.current();
        assertFalse(store.reload().valid());
        assertSame(active, store.current());
        assertFalse(store.select("missing.json").valid());
        assertEquals(0, store.current().size());
        assertFalse(store.current().diagnostics().isEmpty());
    }

    @Test void migrationNeedsConfirmationAndPreservesOriginalBackupAndOverrides() throws Exception {
        Files.writeString(directory.resolve("old.json"), V1);
        var store = new ProfileStore(directory);
        var preview = store.select("old.json");
        assertTrue(preview.requiresMigrationConfirmation());
        assertEquals(0, store.current().size());
        assertFalse(store.apply(preview, false).success());
        assertFalse(store.save("old.json", preview, false).success());
        assertEquals(V1, Files.readString(directory.resolve("old.json")));
        var override = new RuleCategory("CHEST", "Saved chest", false, List.of(5,6,7,8), false, true, false, true, 72);
        var migrated = ProfileDraft.migrateV1(V1, Map.of("CHEST", override));
        assertEquals(5, migrated.validate().categories().size());
        assertEquals(override, migrated.validate().categories().getFirst());
        assertTrue(store.save("old.json", migrated, true).success());
        assertEquals(V1, Files.readString(directory.resolve("old.json.v1.bak")));
        assertEquals(2, JsonParser.parseString(Files.readString(directory.resolve("old.json"))).getAsJsonObject().get("version").getAsInt());
        assertTrue(store.save("old.json", ProfileDraft.parse(VersionTwoRulesTest.json()), false).success());
        assertEquals(V1, Files.readString(directory.resolve("old.json.v1.bak")));
        Files.writeString(directory.resolve("old.json"), V1.replace("Box", "Changed"));
        assertFalse(store.save("old.json", migrated, true).success());
        assertTrue(Files.readString(directory.resolve("old.json")).contains("Changed"));
        assertEquals(V1, Files.readString(directory.resolve("old.json.v1.bak")));
    }

    @Test void editorDocumentsAreDefensiveAndPreserveLabelsDisabledFlagsPriorityAndOrder() {
        var input = JsonParser.parseString(VersionTwoRulesTest.json()).getAsJsonObject();
        input.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("enabled", false);
        var draft = ProfileDraft.fromJson(input);
        input.remove("rules");
        var copy = draft.editableJson();
        copy.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("priority", 999);
        var canonical = draft.validate().editableJson().getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals(10, canonical.get("priority").getAsInt());
        assertFalse(canonical.get("enabled").getAsBoolean());
        assertEquals("{observation.name}", canonical.get("label").getAsString());
        assertTrue(RuleProfile.parse(draft.validate().canonicalJson()).diagnostics().isEmpty());
    }

    @Test void confinementRejectsTraversalAbsolutePathsAndSymlinksAndReadOnlyStoreCannotWrite() throws Exception {
        var root = directory.resolve("profiles");
        var store = new ProfileStore(root);
        var draft = ProfileDraft.parse(VersionTwoRulesTest.json());
        Path external = directory.resolve("outside.json");
        Files.writeString(external, "untouched");
        for (String name : List.of("../outside.json", external.toString(), "sub/../../outside.json")) {
            assertFalse(store.save(name, draft, false).success());
            assertFalse(store.select(name).valid());
        }
        Files.createSymbolicLink(root.resolve("link.json"), external);
        assertFalse(store.save("link.json", draft, false).success());
        assertFalse(store.select("link.json").valid());
        Files.createSymbolicLink(root.resolve("nested"), directory);
        assertFalse(store.save("nested/outside.json", draft, false).success());
        assertEquals("untouched", Files.readString(external));
        assertFalse(new ProfileStore().save(external.toString(), draft, false).success());
        Files.writeString(root.resolve("old.json"), V1);
        Files.createSymbolicLink(root.resolve("old.json.v1.bak"), external);
        assertFalse(store.save("old.json", ProfileDraft.parse(V1), true).success());
        assertEquals(V1, Files.readString(root.resolve("old.json")));
    }

    @Test void successfulReplacementLeavesCompleteParseableFileAndNoTemporaryFiles() throws Exception {
        var store = new ProfileStore(directory);
        assertTrue(store.save("rules.json", ProfileDraft.parse(VersionTwoRulesTest.json()), false).success());
        try (var original = Files.newInputStream(directory.resolve("rules.json"))) {
            assertTrue(store.save("rules.json", ProfileDraft.parse(VersionTwoRulesTest.json().replace("Custom", "Replacement")), false).success());
            assertEquals("Custom", RuleProfile.parse(new String(original.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).categories().getFirst().name());
        }
        assertEquals("Replacement", RuleProfile.parse(Files.readString(directory.resolve("rules.json"))).categories().getFirst().name());
        try (var files = Files.list(directory)) { assertEquals(List.of("rules.json"), files.map(p -> p.getFileName().toString()).toList()); }
    }

    @Test void invalidStructuredDraftRetainsEditorContentAndMigrationProvenance() {
        var draft = ProfileDraft.parse(V1);
        var document = draft.editableJson();
        document.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("categoryId", "missing");
        var invalid = draft.withJson(document);
        assertFalse(invalid.valid());
        assertTrue(invalid.requiresMigrationConfirmation());
        assertEquals("missing", invalid.editableJson().getAsJsonArray("rules").get(0).getAsJsonObject().get("categoryId").getAsString());
    }

    @Test void invalidV1OnDiskStillRequiresConfirmedBackupBeforeReplacement() throws Exception {
        String brokenV1 = V1.replace("CHEST", "BAD_TARGET");
        Files.writeString(directory.resolve("old.json"), brokenV1);
        var store = new ProfileStore(directory);
        var replacement = ProfileDraft.parse(VersionTwoRulesTest.json());
        assertFalse(store.save("old.json", replacement, false).success());
        assertEquals(brokenV1, Files.readString(directory.resolve("old.json")));
        assertTrue(store.save("old.json", replacement, true).success());
        assertEquals(brokenV1, Files.readString(directory.resolve("old.json.v1.bak")));
    }

    @Test void migratedProfilesKeepSchemaFilteringInStarscriptAndExactLegacyDefaults() {
        String source = V1.replace("Box", "{observation.uncatalogued}");
        var draft = ProfileDraft.parse(source);
        assertEquals("null", draft.validate().match(Map.of("kind", "BLOCK_ENTITY", "uncatalogued", "leaked")).orElseThrow().label());
        var categories = draft.validate().categories();
        assertEquals(List.of(255,190,55,220), categories.get(0).color());
        assertEquals(List.of(255,85,85,230), categories.get(1).color());
        assertEquals(List.of(90,220,255,230), categories.get(2).color());
        assertEquals(List.of(210,110,255,225), categories.get(3).color());
        assertEquals(List.of(120,255,145,225), categories.get(4).color());
        assertTrue(categories.getFirst().boxes());
        assertTrue(categories.getFirst().arrows());
        assertEquals(1024, categories.getFirst().maxDistance());
        assertFalse(categories.get(3).boxes());
        assertFalse(categories.get(3).tracers());
        assertFalse(categories.get(3).arrows());
        assertTrue(categories.get(3).nametags());
    }

    @Test void replacedRootCannotRedirectWritesToAnotherDirectory() throws Exception {
        Path root = directory.resolve("profiles");
        var store = new ProfileStore(root);
        Path external = directory.resolve("external");
        Files.createDirectory(external);
        Files.move(root, directory.resolve("original"));
        Files.createSymbolicLink(root, external);
        assertFalse(store.save("new.json", ProfileDraft.parse(VersionTwoRulesTest.json()), false).success());
        assertFalse(Files.exists(external.resolve("new.json")));
    }

    @Test void migrationRejectsCanonicalExpansionBeyondByteLimitBeforeAnySave() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray rules = new JsonArray();
        for (int i = 0; i < 128; i++) {
            JsonObject rule = JsonParser.parseString(V1).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();
            rule.addProperty("id", "r" + i);
            rule.addProperty("label", "я".repeat(450));
            rules.add(rule);
        }
        root.add("rules", rules);
        String source = root.toString();
        assertTrue(source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 131072);
        assertTrue(RuleProfile.parse(source).diagnostics().isEmpty());
        var draft = ProfileDraft.parse(source);
        assertFalse(draft.valid(), "Expanded v2 document must be bounded too");
        Files.writeString(directory.resolve("old.json"), source);
        var store = new ProfileStore(directory);
        assertFalse(store.save("old.json", draft, true).success());
        assertEquals(source, Files.readString(directory.resolve("old.json")));
        assertFalse(Files.exists(directory.resolve("old.json.v1.bak")));
    }
}

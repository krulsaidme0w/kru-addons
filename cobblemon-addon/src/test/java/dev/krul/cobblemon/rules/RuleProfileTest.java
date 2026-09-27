package dev.krul.cobblemon.rules;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuleProfileTest {
    @Test void versionTwoReadsProfileSettingsWithSafeDefaults() {
        var profile = RuleProfile.parse("""
            {"version":2,"settings":{"scanIntervalSeconds":3,"visualRange":640,"crosshairInspection":true,"verboseLogs":false},"categories":[],"rules":[]}
            """);

        assertTrue(profile.diagnostics().isEmpty());
        assertEquals(3, profile.settings().scanIntervalSeconds());
        assertEquals(640, profile.settings().visualRange());
        assertTrue(profile.settings().crosshairInspection());
        assertFalse(profile.settings().verboseLogs());
    }

    @Test void missingProfileSettingsUseMinimalSafeDefaults() {
        var profile = RuleProfile.parse("""
            {"version":2,"categories":[],"rules":[]}
            """);

        assertTrue(profile.diagnostics().isEmpty());
        assertEquals(RuleProfile.Settings.DEFAULT, profile.settings());
    }
    private static final String PROFILE = """
        {"version":1,"rules":[{"id":"loot","target":"POKE_BALL",
          "label":"{observation.name} Lv.{observation.level}",
          "all":[{"field":"dimension","equals":"example:ruins"},
                 {"field":"model_data","equals":17},
                 {"field":"level","min":80}]}]}
        """;

    @Test void noProfileProducesNoClassification() {
        assertTrue(RuleProfile.disabled().match(Map.of("name", "Guardian")).isEmpty());
    }

    @Test void exactModelDimensionAndMinimumLevelMustAllBeProven() {
        var profile = RuleProfile.parse(PROFILE);
        assertTrue(profile.diagnostics().isEmpty());
        var facts = Map.<String, Object>of("dimension", "example:ruins", "model_data", 17, "level", 80, "name", "Loot");
        assertEquals("Loot Lv.80", profile.match(facts).orElseThrow().label());
        assertTrue(profile.match(Map.of("dimension", "example:ruins", "model_data", 17)).isEmpty());
        assertTrue(profile.match(Map.of("dimension", "other:ruins", "model_data", 17, "level", 80)).isEmpty());
        assertTrue(profile.match(Map.of("dimension", "example:ruins", "model_data", 17, "level", 79)).isEmpty());
    }

    @Test void editingDataChangesModelMappingWithoutJavaChanges() {
        var facts = Map.<String, Object>of("dimension", "example:ruins", "model_data", 18, "level", 80, "name", "New");
        assertTrue(RuleProfile.parse(PROFILE).match(facts).isEmpty());
        assertEquals("New Lv.80", RuleProfile.parse(PROFILE.replace("\"equals\":17", "\"equals\":18"))
            .match(facts).orElseThrow().label());
    }

    @Test void suffixAndTokenPredicatesDoNotMatchMissingInputs() {
        var profile = RuleProfile.parse("""
            {"version":1,"rules":[{"id":"marker","target":"WEAPON","label":"{observation.name}",
              "all":[{"field":"name","suffix":"_marker"},{"field":"aspects","token":"large"}]}]}
            """);
        assertTrue(profile.match(Map.of("name", "red_marker", "aspects", "large,red")).isPresent());
        assertTrue(profile.match(Map.of("name", "red_marker", "aspects", "enlarged")).isEmpty());
        assertTrue(profile.match(Map.of("name", "red_marker")).isEmpty());
    }

    @Test void expressionsAreCompiledAndContextsNeverLeakBetweenObservations() {
        var profile = RuleProfile.parse("""
            {"version":1,"rules":[{"id":"label","target":"CHEST","label":"{observation.name}",
              "all":[{"field":"kind","equals":"BLOCK_ENTITY"}]}]}
            """);
        assertEquals("First", profile.match(Map.of("kind", "BLOCK_ENTITY", "name", "First")).orElseThrow().label());
        assertEquals("Second", profile.match(Map.of("kind", "BLOCK_ENTITY", "name", "Second")).orElseThrow().label());
        assertEquals("null", profile.match(Map.of("kind", "BLOCK_ENTITY")).orElseThrow().label());
    }

    @Test void unsafeIntegersNeverCollapseIntoTheSamePredicateValue() {
        String json = """
            {"version":1,"rules":[{"id":"number","target":"CHEST","label":"Exact",
              "all":[{"field":"number","equals":9007199254740993}]}]}
            """;
        var profile = RuleProfile.parse(json);
        assertFalse(profile.diagnostics().isEmpty());
        assertTrue(profile.match(Map.of("number", 9007199254740992L)).isEmpty());
        var safe = RuleProfile.parse(json.replace("9007199254740993", "9007199254740991"));
        assertTrue(safe.match(Map.of("number", 9007199254740993L)).isEmpty());
    }

    @Test void snapshotVisitsAtMost64EntriesAndDisabledProfileDoesNotVisitFacts() {
        int[] reads = {0};
        Map<String, Object> large = new java.util.AbstractMap<>() {
            @Override public java.util.Set<Entry<String, Object>> entrySet() {
                return new java.util.AbstractSet<>() {
                    public int size() { return 100000; }
                    public java.util.Iterator<Entry<String, Object>> iterator() {
                        return new java.util.Iterator<>() {
                            int index;
                            public boolean hasNext() { return index < 100000; }
                            public Entry<String, Object> next() { reads[0]++; return Map.entry("field" + index++, "value"); }
                        };
                    }
                };
            }
        };
        RuleProfile.disabled().match(large);
        assertEquals(0, reads[0]);
        RuleProfile.parse(PROFILE).match(large);
        assertEquals(64, reads[0]);
    }

    @Test void labelsRespectStarscriptWhitespaceLiteralsAndNullConditionals() {
        String template = """
            {"version":1,"rules":[{"id":"label","target":"CHEST","label":"LABEL",
              "all":[{"field":"kind","equals":"BLOCK_ENTITY"}]}]}
            """;
        var facts = Map.<String, Object>of("kind", "BLOCK_ENTITY", "name", "Visible");
        assertEquals("Visible", RuleProfile.parse(template.replace("LABEL", "{observation . name}")).match(facts).orElseThrow().label());
        assertEquals("observation.name", RuleProfile.parse(template.replace("LABEL", "observation.name"))
            .match(Map.of("kind", "BLOCK_ENTITY")).orElseThrow().label());
        assertEquals("Unknown", RuleProfile.parse(template.replace("LABEL", "{observation.name == null ? 'Unknown' : observation.name}"))
            .match(Map.of("kind", "BLOCK_ENTITY")).orElseThrow().label());
    }

    @Test void invalidConfigurationFailsClosedWithDiagnostics() {
        for (String invalid : new String[]{"{", "{}", PROFILE.replace("\"min\":80", "\"minimum\":80"),
            PROFILE.replace("POKE_BALL", "NOT_A_TARGET"), PROFILE.replace("{observation.name}", "{broken(}")}) {
            var profile = RuleProfile.parse(invalid);
            assertFalse(profile.diagnostics().isEmpty(), invalid);
            assertTrue(profile.match(Map.of("dimension", "example:ruins", "model_data", 17, "level", 80)).isEmpty());
        }
    }

    @Test void reloadReplacesPreviousRulesEvenWhenNewFileIsInvalid() {
        var state = new RuleProfile.State();
        state.reload(PROFILE);
        assertEquals(1, state.current().size());
        state.reload("{");
        assertEquals(0, state.current().size());
        assertFalse(state.current().diagnostics().isEmpty());
        state.disable();
        assertTrue(state.current().diagnostics().isEmpty());
    }
}

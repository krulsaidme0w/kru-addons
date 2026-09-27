package dev.krul.cobblemon.rules;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VersionTwoRulesTest {
    @Test void versionUsesExactNumericIntegerSemantics() {
        for (String version : java.util.List.of("1", "1.0", "1e0", "1.00e+0")) {
            var profile = RuleProfile.parse(ProfileEditorTest.V1.replace("\"version\":1", "\"version\":" + version));
            assertTrue(profile.diagnostics().isEmpty(), version);
            assertEquals(1, profile.sourceVersion());
        }
        for (String version : java.util.List.of("1.1", "\"1\"", "true", "null")) {
            assertFalse(RuleProfile.parse(ProfileEditorTest.V1.replace("\"version\":1", "\"version\":" + version)).diagnostics().isEmpty());
        }
    }
    static String json() {
        return """
            {"version":2,"categories":[
              {"id":"sixth","name":"Custom","enabled":true,"color":[1,2,3,255],
               "boxes":true,"tracers":false,"nametags":true,"arrows":false,"maxDistance":128}],
             "rules":[{"id":"first","enabled":true,"categoryId":"sixth","priority":10,
               "label":"{observation.name}","all":[{"field":"level","min":80},{"field":"level","max":90}]}]}
            """;
    }

    @Test void arbitraryCategoryAndInclusiveRangeMatch() {
        var root = JsonParser.parseString(json()).getAsJsonObject();
        var sixth = root.getAsJsonArray("categories").get(0).deepCopy();
        var categories = RuleProfile.parse(ProfileEditorTest.V1).editableJson().getAsJsonArray("categories");
        categories.add(sixth);
        root.add("categories", categories);
        var profile = RuleProfile.parse(root.toString());
        assertTrue(profile.diagnostics().isEmpty(), profile.diagnostics().toString());
        assertEquals(6, profile.categories().size());
        assertEquals("sixth", profile.match(Map.of("level", 80, "name", "Seen")).orElseThrow().target());
        assertTrue(profile.match(Map.of("level", 90)).isPresent());
        assertTrue(profile.match(Map.of("level", 91)).isEmpty());
        assertTrue(profile.match(Map.of()).isEmpty());
    }

    @Test void descendingPriorityAndStableTieOrderChooseFirstEnabledMatch() {
        JsonObject root = JsonParser.parseString(json()).getAsJsonObject();
        JsonObject second = root.getAsJsonArray("rules").get(0).getAsJsonObject().deepCopy();
        second.addProperty("id", "second");
        root.getAsJsonArray("rules").add(second);
        assertEquals("first", RuleProfile.parse(root.toString()).match(Map.of("level", 85)).orElseThrow().ruleId());
        second.addProperty("priority", 11);
        assertEquals("second", RuleProfile.parse(root.toString()).match(Map.of("level", 85)).orElseThrow().ruleId());
        second.addProperty("enabled", false);
        assertEquals("first", RuleProfile.parse(root.toString()).match(Map.of("level", 85)).orElseThrow().ruleId());
        root.getAsJsonArray("categories").get(0).getAsJsonObject().addProperty("enabled", false);
        assertTrue(RuleProfile.parse(root.toString()).match(Map.of("level", 85)).isEmpty());
    }

    @Test void rejectsUnknownFieldsOperatorsCategoriesAndWrongTypes() {
        for (String invalid : new String[]{json().replace("\"level\"", "\"made_up\""),
            json().replace("\"max\"", "\"execute\""), json().replace("\"categoryId\":\"sixth\"", "\"categoryId\":\"missing\""),
            json().replace("\"min\":80", "\"min\":\"80\""), json().replace("\"level\"", "\"name\""),
            json().replace("[1,2,3,255]", "[1,2,3,256]"), json().replace("\"priority\":10", "\"priority\":1.5")}) {
            assertFalse(RuleProfile.parse(invalid).diagnostics().isEmpty(), invalid);
        }
    }

    @Test void wrongTypeAndLiveObjectsNeverReachPredicatesOrLabels() {
        var profile = RuleProfile.parse(json());
        assertTrue(profile.match(Map.of("level", "85")).isEmpty());
        assertTrue(profile.match(Map.of("level", new java.util.concurrent.atomic.AtomicInteger(85))).isEmpty());
        assertEquals("null", profile.match(Map.of("level", 85, "name", new Object() {
            public String toString() { throw new AssertionError("Live object was evaluated"); }
        })).orElseThrow().label());
    }

    @Test void explainsEveryConditionIncludingUnknownWithoutInventingFalseOrZero() {
        var p = RuleProfile.parse(json());
        var missing = p.explain(Map.of()).getFirst();
        assertEquals(RuleProfile.Truth.UNKNOWN, missing.truth());
        assertEquals(2, missing.conditions().size());
        assertNull(missing.conditions().getFirst().actual());
        assertEquals("level", missing.conditions().getFirst().field());
        assertEquals(RuleProfile.Truth.NO_MATCH, p.explain(Map.of("level", 0)).getFirst().truth());
        assertEquals(RuleProfile.Truth.UNKNOWN, p.explain(Map.of("level", "85")).getFirst().truth());
    }

    @Test void enforcesCategoryRulePredicateAndUtf8ByteBounds() {
        JsonObject root = JsonParser.parseString(json()).getAsJsonObject();
        JsonArray categories = root.getAsJsonArray("categories");
        JsonObject category = categories.get(0).getAsJsonObject();
        for (int i = 1; i < 64; i++) { var c = category.deepCopy(); c.addProperty("id", "category" + i); categories.add(c); }
        assertTrue(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        var overflow = category.deepCopy(); overflow.addProperty("id", "overflow"); categories.add(overflow);
        assertFalse(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        categories.remove(64);
        JsonArray rules = root.getAsJsonArray("rules");
        var rule = rules.get(0).getAsJsonObject();
        for (int i = 1; i < 128; i++) { var r = rule.deepCopy(); r.addProperty("id", "r" + i); rules.add(r); }
        assertTrue(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        var extra = rule.deepCopy(); extra.addProperty("id", "overflow"); rules.add(extra);
        assertFalse(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        rules.remove(128);
        var all = rule.getAsJsonArray("all");
        while (all.size() < 16) all.add(all.get(0).deepCopy());
        assertTrue(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        all.add(all.get(0).deepCopy());
        assertFalse(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        // Valid JSON whitespace fills exact byte boundary; one further byte is rejected.
        String base = json();
        assertTrue(RuleProfile.parse(base + " ".repeat(131072 - base.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)).diagnostics().isEmpty());
        assertFalse(RuleProfile.parse(base + " ".repeat(131073)).diagnostics().isEmpty());
    }

    @Test void nonAsciiProfileIsBoundedByUtf8BytesRatherThanCharacterCount() {
        String base = json().replace("Custom", "Покемон 🐾");
        int bytes = base.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact = base + " ".repeat(131072 - bytes);
        assertTrue(exact.length() < 131072);
        assertTrue(RuleProfile.parse(exact).diagnostics().isEmpty());
        assertFalse(RuleProfile.parse(exact + " ").diagnostics().isEmpty());
    }

    @Test void typedV2KeepsUnsafeNumericAndBooleanAvailabilityChecksMeaningful() {
        String exact = json().replace("\"min\":80", "\"equals\":9007199254740991").replace(",\"max\":90", ",\"max\":9007199254740991");
        // Use one predicate so the independent range ceiling cannot mask the safe-number check.
        var root = JsonParser.parseString(exact).getAsJsonObject();
        var all = root.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonArray("all");
        all.remove(1);
        var profile = RuleProfile.parse(root.toString());
        assertTrue(profile.diagnostics().isEmpty());
        assertTrue(profile.match(Map.of("level", 9007199254740991L)).isPresent());
        assertTrue(profile.match(Map.of("level", 9007199254740993L)).isEmpty());
        all.get(0).getAsJsonObject().addProperty("equals", 9007199254740993L);
        assertFalse(RuleProfile.parse(root.toString()).diagnostics().isEmpty());
        var condition = all.get(0).getAsJsonObject();
        condition.addProperty("field", "shiny"); condition.addProperty("equals", false);
        profile = RuleProfile.parse(root.toString());
        assertTrue(profile.match(Map.of()).isEmpty());
        assertTrue(profile.match(Map.of("shiny", false)).isPresent());
    }
}

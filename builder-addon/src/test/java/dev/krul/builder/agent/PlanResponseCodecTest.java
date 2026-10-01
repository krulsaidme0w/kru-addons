package dev.krul.builder.agent;

import com.google.gson.*;
import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanResponseCodecTest {
    private static final PlanResponseCodec CODEC = new PlanResponseCodec();
    private static final String VALID = """
        {"message":"Here is the proposal","plan":{"revision":2,"palette":[{"key":"stone","state":{"id":"minecraft:stone","properties":[]}}],"stages":[{"id":"base","title":"Base","after":[],"operations":[{"op":"block","position":[0,0,0],"state":"stone"},{"op":"fill","min":[1,0,0],"max":[2,0,0],"state":"stone"}]}]}}
        """;

    @Test void expandsInclusiveFillAndReturnsValidatedProposal() throws Exception {
        var response = CODEC.decode(VALID, request(), key -> true);
        assertEquals("Here is the proposal", response.message());
        BuildPlan plan = response.plan().orElseThrow();
        assertEquals(3, plan.stages().getFirst().targets().size());
        assertEquals(new StateKey("minecraft:stone", Map.of()),
            plan.stages().getFirst().targets().get(new Cell(1, 0, 0)));
        assertEquals(1, plan.contextRevision());
    }

    @Test void permitsDiscussionWithoutAPlan() throws Exception {
        assertTrue(CODEC.decode("{\"message\":\"Just discussing\",\"plan\":null}", request(), key -> true).plan().isEmpty());
    }

    @Test void proposalMustAdvanceTheAcceptedBaselineRevision() {
        AgentRequest request = request();
        var baseline = new BuildPlan(request.project(), 2, 1, List.of(
            new BuildStage("base", "Base", Set.of(), Map.of(new Cell(0, 0, 0), new StateKey("minecraft:stone", Map.of())))));
        var withBaseline = new AgentRequest(request.project(), request.session(), 1, request.text(),
            request.context(), List.of(), Optional.of(baseline));
        assertThrows(Exception.class, () -> CODEC.decode(VALID, withBaseline, key -> true));
    }

    @Test void rejectsUndeclaredPaletteReferencesScriptsConflictsAndUnknownStates() {
        assertThrows(Exception.class, () -> CODEC.decode(VALID.replace("\"state\":\"stone\"","\"state\":\"missing\""), request(), key -> true));
        assertThrows(Exception.class, () -> CODEC.decode(VALID.replace("\"op\":\"block\"","\"op\":\"script\""), request(), key -> true));
        String conflict = VALID.replace("{\"key\":\"stone\"", "{\"key\":\"wood\",\"state\":{\"id\":\"minecraft:dirt\",\"properties\":{}}},{\"key\":\"stone\"")
            .replace("\"min\":[1,0,0]", "\"min\":[0,0,0]")
            .replace("\"max\":[2,0,0],\"state\":\"stone\"", "\"max\":[0,0,0],\"state\":\"wood\"");
        assertThrows(Exception.class, () -> CODEC.decode(conflict, request(), key -> true));
        assertThrows(Exception.class, () -> CODEC.decode(VALID, request(), key -> false));
    }

    @Test void reportsTheExactCellWhenTwoOperationsAssignDifferentStatesInOneStage() {
        String conflict = """
            {"message":"bad plan","plan":{"revision":2,"palette":[
              {"key":"stone","state":{"id":"minecraft:stone","properties":[]}},
              {"key":"dirt","state":{"id":"minecraft:dirt","properties":[]}}],
              "stages":[{"id":"base","title":"Base","after":[],"operations":[
                {"op":"block","position":[1,0,0],"state":"stone"},
                {"op":"fill","min":[0,0,0],"max":[2,0,0],"state":"dirt"}]}]}}
            """;
        Exception error = assertThrows(Exception.class, () -> CODEC.decode(conflict, request(), key -> true));
        assertTrue(error.getMessage().contains("Conflicting assignments within stage 'base' at [1,0,0]"), error.getMessage());
    }

    @Test void rejectsUnknownFieldsMalformedJsonAndOversizedFill() {
        assertThrows(Exception.class, () -> CODEC.decode("{\"message\":\"ok\",\"plan\":null,\"tool\":\"run\"}", request(), key -> true));
        assertThrows(Exception.class, () -> CODEC.decode("{bad}", request(), key -> true));
        String huge = VALID.replace("\"max\":[2,0,0]", "\"max\":[32768,0,0]");
        assertThrows(Exception.class, () -> CODEC.decode(huge, request(), key -> true));
    }

    @Test void outputSchemaIsStrictJsonSchemaRatherThanAChatDump() {
        JsonObject schema = CODEC.outputSchema();
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertEquals("object", schema.get("type").getAsString());
        assertTrue(schema.getAsJsonObject("properties").has("message"));
        assertTrue(schema.getAsJsonObject("properties").has("plan"));
        assertSchemaIsStrictAndSupported(schema);
    }

    @Test void closedPropertyEntryArrayDecodesBackToStateKeyProperties() throws Exception {
        String wire = VALID.replace("\"properties\":[]", "\"properties\":[{\"name\":\"axis\",\"value\":\"y\"}]");
        var response = CODEC.decode(wire, request(), key -> true);
        assertEquals(Map.of("axis", "y"), response.plan().orElseThrow().stages().getFirst()
            .targets().get(new Cell(0, 0, 0)).properties());
    }

    private static void assertSchemaIsStrictAndSupported(JsonElement element) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(PlanResponseCodecTest::assertSchemaIsStrictAndSupported);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            assertFalse(object.has("oneOf"), "oneOf is unsupported in strict structured output");
            if (object.has("type") && object.get("type").isJsonPrimitive()
                    && "object".equals(object.get("type").getAsString())) {
                assertTrue(object.has("additionalProperties") && object.get("additionalProperties").isJsonPrimitive()
                    && object.get("additionalProperties").getAsJsonPrimitive().isBoolean()
                    && !object.get("additionalProperties").getAsBoolean(), "object schemas must be closed");
                JsonObject properties = object.has("properties") ? object.getAsJsonObject("properties") : new JsonObject();
                Set<String> required = new HashSet<>();
                if (object.has("required")) object.getAsJsonArray("required").forEach(name -> required.add(name.getAsString()));
                assertEquals(properties.keySet(), required, "all object properties must be required");
            }
            object.entrySet().forEach(entry -> assertSchemaIsStrictAndSupported(entry.getValue()));
        }
    }

    private static AgentRequest request() {
        UUID session = UUID.randomUUID();
        Bounds bounds = new Bounds(new Cell(0, 0, 0), new Cell(2, 0, 0));
        WorldSnapshot snapshot = new WorldSnapshot(session, 1, bounds, Map.of(), Set.of(new Cell(0, 0, 0)));
        return new AgentRequest(UUID.randomUUID(), session, 1, "Build", snapshot, List.of());
    }
}

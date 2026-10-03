package dev.krul.builder.mcp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureReviewTest {
    @Test void rewardsStructuredPaletteDepthFurnitureAndDetails() {
        String plan = """
            {"palette":{"wood":{"id":"minecraft:spruce_planks"},"stone":{"id":"minecraft:polished_deepslate"},"glass":{"id":"minecraft:glass_pane"},"light":{"id":"minecraft:lantern"}},
             "stages":[{"id":"mass","operations":[{"op":"PLACE","position":{"x":0,"y":0,"z":0},"target":"wood"}]},{"id":"detail","operations":[{"op":"PLACE","position":{"x":1,"y":1,"z":1},"target":"stone"}]},{"id":"furniture","operations":[{"op":"PLACE","position":{"x":2,"y":2,"z":2},"target":"spruce_chair"},{"op":"PLACE","position":{"x":3,"y":2,"z":3},"target":"lantern"}]}]}
            """;
        var result = ArchitectureReview.review(JsonParser.parseString(plan).getAsJsonObject());
        assertTrue(result.score() >= 50);
        assertTrue(result.passed().contains("staged composition"));
    }

    @Test void rejectsFlatOneColorDump() {
        String plan = "{" +
            "\"palette\":{\"block\":{\"id\":\"minecraft:stone\"}}," +
            "\"stages\":[{\"id\":\"only\",\"operations\":[{\"op\":\"PLACE\",\"position\":{\"x\":0,\"y\":0,\"z\":0},\"target\":\"block\"}]}]}";
        var result = ArchitectureReview.review(JsonParser.parseString(plan).getAsJsonObject());
        assertFalse(result.publishable());
        assertTrue(result.warnings().stream().anyMatch(value -> value.contains("Palette")));
        assertTrue(result.warnings().stream().anyMatch(value -> value.contains("flat")));
    }
}

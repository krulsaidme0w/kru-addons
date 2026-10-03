package dev.krul.builder.mcp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureOpsTest {
    @Test void wallIsClosedAndDuplicateCellsAreCollapsed() {
        var input = JsonParser.parseString("""
            {"ops":[{"type":"wall","from":{"x":0,"y":0,"z":0},"to":{"x":4,"y":2,"z":4},"target":"frame"},{"type":"line","from":{"x":0,"y":0,"z":0},"to":{"x":4,"y":0,"z":0},"target":"frame"}]}
            """).getAsJsonObject();
        var result = ArchitectureOps.expand(input);
        assertEquals(48, result.get("count").getAsInt());
        assertEquals(48, result.getAsJsonArray("operations").size());
    }

    @Test void diagonalLineIsRejected() {
        var input = JsonParser.parseString("{\"ops\":[{\"type\":\"line\",\"from\":{\"x\":0,\"y\":0,\"z\":0},\"to\":{\"x\":1,\"y\":1,\"z\":0},\"target\":\"trim\"}]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ArchitectureOps.expand(input));
    }
    @Test void conflictingPrimitiveOverlapIsRejected() {
        var input = JsonParser.parseString("{\"ops\":[{\"type\":\"line\",\"from\":{\"x\":0,\"y\":0,\"z\":0},\"to\":{\"x\":2,\"y\":0,\"z\":0},\"target\":\"frame\"},{\"type\":\"line\",\"from\":{\"x\":1,\"y\":0,\"z\":0},\"to\":{\"x\":2,\"y\":0,\"z\":0},\"target\":\"glass\"}]} ").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ArchitectureOps.expand(input));
    }
}

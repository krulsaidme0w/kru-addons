package dev.krul.builder.mcp;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BuilderMcpToolsTest {
    @Test void exposesOnlyBoundedInspectionAndExplicitPairingTools() {
        var bridge = new BridgeClient(URI.create("http://127.0.0.1:47653"), Duration.ofSeconds(1));
        var tools = BuilderMcpServer.toolSpecifications(bridge);
        assertEquals(Set.of("minecraft_status", "read_selection", "capture_view", "pair_local_minecraft",
                "list_block_mods", "search_blocks", "describe_block"),
            tools.stream().map(tool -> tool.tool().name()).collect(Collectors.toSet()));
        assertTrue(tools.stream().noneMatch(tool -> tool.tool().name().contains("place") || tool.tool().name().contains("move") || tool.tool().name().contains("command")));
        assertTrue(tools.stream().filter(tool -> !tool.tool().name().equals("pair_local_minecraft"))
            .allMatch(tool -> Boolean.TRUE.equals(tool.tool().annotations().readOnlyHint())));
        assertFalse(Boolean.TRUE.equals(tools.stream().filter(tool -> tool.tool().name().equals("pair_local_minecraft"))
            .findFirst().orElseThrow().tool().annotations().readOnlyHint()));
    }

    @Test void catalogToolsStayPagedAndReturnOneExactPreview() {
        var tools = BuilderMcpServer.toolSpecifications(new BridgeClient(URI.create("http://127.0.0.1:47653"), Duration.ofSeconds(1)));
        var search = tools.stream().filter(tool -> tool.tool().name().equals("search_blocks")).findFirst().orElseThrow().tool();
        assertEquals(Boolean.FALSE, search.inputSchema().get("additionalProperties"));
        Map<?, ?> props = (Map<?, ?>) search.inputSchema().get("properties");
        assertEquals(128, ((Map<?, ?>) props.get("query")).get("maxLength"));
        assertTrue(tools.stream().filter(tool -> Set.of("list_block_mods", "search_blocks", "describe_block").contains(tool.tool().name()))
            .allMatch(tool -> Boolean.TRUE.equals(tool.tool().annotations().readOnlyHint())));
    }

    @Test void selectionSchemaIsClosedAndCapsPageSize() {
        var tool = BuilderMcpServer.toolSpecifications(new BridgeClient(URI.create("http://127.0.0.1:47653"), Duration.ofSeconds(1)))
            .stream().filter(value -> value.tool().name().equals("read_selection")).findFirst().orElseThrow().tool();
        assertEquals(Boolean.FALSE, tool.inputSchema().get("additionalProperties"));
        Map<?, ?> props = (Map<?, ?>) tool.inputSchema().get("properties");
        Map<?, ?> pageSize = (Map<?, ?>) props.get("pageSize");
        assertEquals(BridgeClient.MAX_PAGE_SIZE, pageSize.get("maximum"));
        assertTrue(((java.util.List<?>) tool.inputSchema().get("required")).contains("snapshotRevision"));
        Map<?, ?> bounds = (Map<?, ?>) props.get("bounds");
        Map<?, ?> possibleBounds = (Map<?, ?>) ((java.util.List<?>) bounds.get("anyOf")).get(0);
        Map<?, ?> positions = (Map<?, ?>) possibleBounds.get("properties");
        Map<?, ?> minimum = (Map<?, ?>) positions.get("min");
        Map<?, ?> coordinates = (Map<?, ?>) minimum.get("properties");
        assertFalse(coordinates.get("x").toString().contains("minimum"), "Minecraft coordinates can be negative");
    }
}

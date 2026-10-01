package dev.krul.builder.agent;

import com.google.gson.JsonParser;
import dev.krul.builder.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldSnapshotGeometryTest {
    @Test void encodesBoundedLayersAsDeterministicXRunsWithUnknownMarkers() throws Exception {
        var air = new StateKey("minecraft:air", Map.of());
        var stone = new StateKey("minecraft:stone", Map.of());
        var snapshot = new WorldSnapshot(UUID.randomUUID(), 3,
            new Bounds(new Cell(-1, 4, 2), new Cell(1, 5, 3)),
            Map.of(new Cell(-1, 4, 2), air, new Cell(0, 4, 2), air,
                new Cell(1, 4, 2), stone, new Cell(-1, 5, 3), stone),
            Set.of(new Cell(0, 5, 3)));

        var encoded = JsonParser.parseString(WorldSnapshotGeometry.encode(snapshot)).getAsJsonObject();

        assertEquals("-1", encoded.getAsJsonObject("bounds").getAsJsonObject("min").get("x").getAsString());
        assertEquals("0", encoded.getAsJsonArray("palette").get(0).getAsJsonObject().get("code").getAsString());
        assertEquals("minecraft:air", encoded.getAsJsonArray("palette").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("y=4 z=2: 2*0,1*1", encoded.getAsJsonArray("rows").get(0).getAsString());
        assertEquals("y=4 z=3: 3*.", encoded.getAsJsonArray("rows").get(1).getAsString());
        assertEquals("y=5 z=2: 3*.", encoded.getAsJsonArray("rows").get(2).getAsString());
        assertEquals("y=5 z=3: 1*1,1*?,1*.", encoded.getAsJsonArray("rows").get(3).getAsString());
    }

    @Test void stablePaletteOrderDoesNotDependOnMapIterationOrder() throws Exception {
        var dirt = new StateKey("minecraft:dirt", Map.of());
        var air = new StateKey("minecraft:air", Map.of());
        var bounds = new Bounds(new Cell(0, 0, 0), new Cell(1, 0, 0));
        var first = new WorldSnapshot(UUID.randomUUID(), 1, bounds, Map.of(new Cell(1, 0, 0), dirt,
            new Cell(0, 0, 0), air), Set.of());
        var second = new WorldSnapshot(UUID.randomUUID(), 1, bounds, Map.of(new Cell(0, 0, 0), air,
            new Cell(1, 0, 0), dirt), Set.of());
        assertEquals(WorldSnapshotGeometry.encode(first), WorldSnapshotGeometry.encode(second));
    }

    @Test void requestContextExplainsVoxelAxesAndRelativeProposalCoordinates() throws Exception {
        var bounds = new Bounds(new Cell(-4, 60, 8), new Cell(-3, 60, 8));
        var snapshot = new WorldSnapshot(UUID.randomUUID(), 1, bounds,
            Map.of(new Cell(-4, 60, 8), new StateKey("minecraft:stone", Map.of())),
            Set.of(new Cell(-3, 60, 8)));
        var request = new AgentRequest(UUID.randomUUID(), snapshot.session(), snapshot.revision(), "Build here", snapshot, java.util.List.of());

        String context = CodexProcess.contextMessage(request);

        assertTrue(context.contains("Proposal coordinates are RELATIVE to bounds.min"));
        assertTrue(context.contains("X cells run left-to-right"));
        assertTrue(context.contains("\"voxelMap\""));
        assertTrue(context.contains("\"unknownCellCount\":1"));
        assertFalse(context.contains("\"known\""));
    }
}

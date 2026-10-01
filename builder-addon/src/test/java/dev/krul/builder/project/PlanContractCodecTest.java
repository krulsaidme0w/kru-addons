package dev.krul.builder.project;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanContractCodecTest {
    private final PlanContractCodec codec = new PlanContractCodec();
    private final StateKey plank = new StateKey("minecraft:oak_planks", Map.of());
    private final PlanContract plan = new PlanContract(UUID.fromString("7bdb0812-63cb-4f3c-bcc8-a4c3bd097aac"), "deck", 2,
        Map.of("wood", plank), List.of(new PlanContract.Stage("deck", "Deck", List.of(),
            List.of(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood")))));

    @Test void roundTripsAllOperationKindsAndExactExpectedStates() {
        PlanContract all = new PlanContract(plan.id(), plan.title(), plan.revision(), plan.palette(), List.of(
            new PlanContract.Stage("place", "Place", List.of(), List.of(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood"))),
            new PlanContract.Stage("replace", "Replace", List.of("place"), List.of(new PlanOperation(PlanOperation.Kind.REPLACE, new Cell(0,0,0), plank, "wood"))),
            new PlanContract.Stage("remove", "Remove", List.of("replace"), List.of(new PlanOperation(PlanOperation.Kind.REMOVE, new Cell(0,0,0), plank, null)))));
        assertEquals(all, codec.decode(codec.encode(all)));
    }

    @Test void rejectsUnknownFieldsDuplicateCellsBadPreconditionsAndOversizePayloads() {
        String json = codec.encode(plan);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(json.replace("\"title\":\"deck\"", "\"title\":\"deck\",\"unknown\":true")));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(" ".repeat(PlanContractCodec.MAX_BYTES + 1)));
        assertThrows(IllegalArgumentException.class, () -> new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), plank, "wood"));
        PlanContract duplicate = new PlanContract(plan.id(), plan.title(), plan.revision(), plan.palette(), List.of(
            new PlanContract.Stage("s", "S", List.of(), List.of(
                new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood"),
                new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood")))));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(duplicate));
    }
}

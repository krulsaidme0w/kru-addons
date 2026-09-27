package dev.krul.cobblemon;

import dev.krul.cobblemon.discovery.DiscoveryRarityIndex;
import dev.krul.cobblemon.discovery.SpawnRarity;
import dev.krul.cobblemon.dungeon.DungeonObservation;
import dev.krul.cobblemon.dungeon.DungeonWorldProbe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DungeonDiscoveryLifecycleTest {
    @Test void emitsOneScanSummaryAtCompletionInsteadOfEveryBudgetSlice() {
        List<String> logs = new ArrayList<>();
        var lifecycle = new DungeonDiscovery.Lifecycle(1, true, ignored -> new FakeProbe(), logs::add, () -> 7L);
        lifecycle.activate(DiscoveryRarityIndex.empty());
        lifecycle.tick(20, "overworld");
        assertEquals(0, logs.stream().filter(line -> line.startsWith("DUNGEON_SCAN ")).count());
        lifecycle.tick(21, "overworld");
        assertEquals(1, logs.stream().filter(line -> line.startsWith("DUNGEON_SCAN ")).count());
    }
    @Test
    void injectedLifecycleDrivesActivationResumeDimensionResetAndDeactivation() {
        List<String> logs = new ArrayList<>();
        List<DiscoveryRarityIndex> indexes = new ArrayList<>();
        FakeProbe probe = new FakeProbe();
        DungeonDiscovery.Lifecycle lifecycle = new DungeonDiscovery.Lifecycle(1, true,
            index -> { indexes.add(index); return probe; }, logs::add, () -> 123L);
        DiscoveryRarityIndex rarity = DiscoveryRarityIndex.builder().add("ralts", SpawnRarity.RARE).build();

        lifecycle.activate(rarity);
        lifecycle.tick(19, "overworld");
        lifecycle.tick(20, "overworld");
        lifecycle.tick(21, "overworld");
        assertEquals(2, probe.calls);
        assertSame(rarity, indexes.get(0));
        assertTrue(logs.stream().anyMatch(line -> line.startsWith("DUNGEON_SESSION ")));

        lifecycle.tick(18, "dungeon");
        assertEquals("dungeon", lifecycle.dimension());
        assertEquals(0, lifecycle.index().summary().retainedObservations());
        lifecycle.deactivate();
        assertEquals("unavailable", lifecycle.dimension());
        assertEquals(0, lifecycle.index().summary().retainedObservations());
    }

    @Test
    void crosshairFlagIsPassedToProbeAndFalseProducesNoCrosshairEvidence() {
        List<String> logs = new ArrayList<>();
        FakeProbe probe = new FakeProbe();
        DungeonDiscovery.Lifecycle lifecycle = new DungeonDiscovery.Lifecycle(1, false,
            ignored -> probe, logs::add, () -> 7L);
        lifecycle.activate(DiscoveryRarityIndex.empty());
        lifecycle.tick(20, "overworld");
        assertFalse(probe.crosshairRequested);
        assertEquals(0, lifecycle.index().summary().retainedObservations());

        lifecycle = new DungeonDiscovery.Lifecycle(1, true, ignored -> probe, logs::add, () -> 7L);
        lifecycle.activate(DiscoveryRarityIndex.empty());
        lifecycle.tick(20, "overworld");
        assertTrue(probe.crosshairRequested);
        assertEquals(1, lifecycle.index().summary().retainedObservations());
        assertTrue(logs.stream().anyMatch(line -> line.startsWith("DUNGEON_CROSSHAIR ")));
    }

    @Test
    void disabledVerboseLogsExplicitlyDiscardEvidence() {
        List<String> disabledLogs = new ArrayList<>();
        FakeProbe probe = new FakeProbe();
        DungeonDiscovery.Lifecycle disabled = new DungeonDiscovery.Lifecycle(1, true,
            ignored -> probe, disabledLogs::add, () -> 7L, false);
        disabled.activate(DiscoveryRarityIndex.empty());
        disabled.tick(20, "overworld");
        assertTrue(disabledLogs.isEmpty());

        List<String> enabledLogs = new ArrayList<>();
        DungeonDiscovery.Lifecycle enabled = new DungeonDiscovery.Lifecycle(1, true,
            ignored -> new FakeProbe(), enabledLogs::add, () -> 7L, true);
        enabled.activate(DiscoveryRarityIndex.empty());
        enabled.tick(20, "overworld");
        enabled.tick(21, "overworld");
        assertTrue(enabledLogs.stream().anyMatch(line -> line.startsWith("DUNGEON_CROSSHAIR ")));
    }

    private static final class FakeProbe implements DungeonDiscovery.Probe {
        int calls;
        boolean crosshairRequested;
        private final DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();

        @Override
        public DungeonWorldProbe.BudgetedResult scan(String dimension, long now, DungeonWorldProbe.ScanCursor ignored, int budget, boolean crosshair) {
            calls++;
            crosshairRequested = crosshair;
            DungeonObservation observation = new DungeonObservation(DungeonObservation.Kind.CROSSHAIR, "overworld",
                "crosshair", "minecraft:stone", "Block", "Stone", null, java.util.Map.of(), now);
            boolean complete = calls > 1;
            return new DungeonWorldProbe.BudgetedResult(
                new DungeonWorldProbe.ProbeResult(dimension, 1, crosshair ? List.of(observation) : List.of(), complete),
                cursor, complete);
        }
    }
}


package dev.krul.cobblemon.dungeon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPerformanceTest {
    @Test void aggregatesPhaseCostsWithoutLoggingEveryFrameAndResetsAfterReport() {
        var perf = new DungeonPerformance();
        assertTrue(perf.report(0).isEmpty());
        perf.record(DungeonPerformance.Phase.SELECT, 2_000_000);
        perf.record(DungeonPerformance.Phase.SELECT, 4_000_000);
        perf.record(DungeonPerformance.Phase.RENDER_2D, 8_000_000);
        assertTrue(perf.report(4_999_999_999L).isEmpty());
        String report = perf.report(5_000_000_000L).orElseThrow();
        assertTrue(report.contains("SELECT_count=2 SELECT_totalMs=6.000 SELECT_maxMs=4.000"));
        assertTrue(report.contains("RENDER_2D_count=1 RENDER_2D_totalMs=8.000"));
        assertTrue(perf.report(5_000_000_001L).isEmpty());
        String next = perf.report(10_000_000_000L).orElseThrow();
        assertTrue(next.contains("SELECT_count=0 SELECT_totalMs=0.000"));
        perf.clear();
        assertTrue(perf.report(20_000_000_000L).isEmpty());
    }
}


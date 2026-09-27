package dev.krul.cobblemon.battle.matchup;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect;

class MatchupNativePaneTest {
    // Shapes mirror the fork: public primitive geometry accessors, private reflow method.
    public static class Companion {
        int width = 153, height = 46;
        double x = 500, y = 300;
        public int getBattleLogWidth() { return width; }
        public int getBattleLogHeight() { return height; }
        public double getBattleLogX() { return x; }
        public double getBattleLogY() { return y; }
        public void setBattleLogWidth(int value) { width = value; }
        public void setBattleLogHeight(int value) { height = value; }
        public void setBattleLogX(double value) { x = value; }
        public void setBattleLogY(double value) { y = value; }
    }
    public static class Pane {
        public final Companion Companion;
        Pane() { this(new Companion()); }
        Pane(Companion companion) { Companion = companion; }
        final List<String> lines = new ArrayList<>();
        private void correctBattleText() {
            lines.clear();
            String message = "a".repeat(350);
            int width = Companion.width - 27;
            for (int i = 0; i < message.length(); i += width) lines.add(message.substring(i, Math.min(message.length(), i + width)));
        }
    }
    /** Mirrors the installed fork's getters and conditional setters (20px edge guard). */
    public static class BoundedCompanion extends Companion {
        @Override public double getBattleLogX() { return Math.max(4, Math.min(x, 800-width-20)); }
        @Override public double getBattleLogY() { return Math.max(4, Math.min(y, 600-height-20)); }
        @Override public void setBattleLogWidth(int value) {
            if (getBattleLogX()+value <= 780) width=value;
        }
        @Override public void setBattleLogHeight(int value) {
            if (getBattleLogY()+value <= 580) height=value;
        }
    }
    @Test void initialGeometryIsAcceptedByNativeGuardAndRendersAllThirtySixCells() throws Exception {
        var adapter = new MatchupNativePane(new Pane(new BoundedCompanion()));
        Rect wanted = new MatchupPaneGeometry().update(true, adapter.read(), 800, 600, 6, 6);
        adapter.apply(wanted);
        Rect actual = adapter.read();
        assertEquals(wanted, actual, "Native setters must accept both dimensions, not leave a skinny log");
        assertEquals(actual.width(), actual.height(), "Default is square");
        assertTrue(actual.height() <= 227, "About 70% of the preceding 302px height, allowing grid minimum");
        var layout = MatchupPanelLayout.fit(actual, 800, 600, 6, 6);
        assertEquals(6, layout.visibleColumns());
        assertEquals(6, layout.visibleRows());
        assertArrayEquals(new int[]{5,5}, layout.hit(layout.cell(5,5).x()+12, layout.cell(5,5).y()+12));
    }
    @Test void returningFromCompactWidthReflowsMessagesForTheOriginalHistoryWidth() throws Exception {
        Class<?> bridge = assertDoesNotThrow(() -> Class.forName(getClass().getPackageName() + ".MatchupNativePane"));
        Pane pane = new Pane();
        Object adapter = bridge.getConstructor(Object.class).newInstance(pane);
        Rect original = (Rect) bridge.getMethod("read").invoke(adapter);
        assertEquals(new Rect(500, 300, 153, 46), original);
        var apply = bridge.getMethod("apply", Rect.class);
        apply.invoke(adapter, new Rect(500, 250, 228, 102));
        pane.correctBattleText(); // Message arrives while Matchups uses the compact width.
        assertEquals(201, pane.lines.getFirst().length());
        apply.invoke(adapter, original);
        assertEquals(original, bridge.getMethod("read").invoke(adapter));
        assertEquals(126, pane.lines.getFirst().length(), "Restore must reflow, not only write width");
        assertEquals(350, pane.lines.stream().mapToInt(String::length).sum(), "No message content may be lost");
    }
}


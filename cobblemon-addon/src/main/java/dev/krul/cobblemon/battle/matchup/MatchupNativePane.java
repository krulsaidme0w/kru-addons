package dev.krul.cobblemon.battle.matchup;

import java.lang.reflect.Method;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.Rect;

/** Exact optional GUI boundary. Native wrapping must be rebuilt after changing its width. */
public final class MatchupNativePane {
    private final Object pane, companion;
    private final Method x, y, width, height, setX, setY, setWidth, setHeight, reflow;

    public MatchupNativePane(Object pane) throws ReflectiveOperationException {
        this.pane = pane;
        companion = pane.getClass().getField("Companion").get(pane);
        Class<?> type = companion.getClass();
        x = type.getMethod("getBattleLogX");
        y = type.getMethod("getBattleLogY");
        width = type.getMethod("getBattleLogWidth");
        height = type.getMethod("getBattleLogHeight");
        setX = type.getMethod("setBattleLogX", double.class);
        setY = type.getMethod("setBattleLogY", double.class);
        setWidth = type.getMethod("setBattleLogWidth", int.class);
        setHeight = type.getMethod("setBattleLogHeight", int.class);
        reflow = pane.getClass().getDeclaredMethod("correctBattleText");
        if (!reflow.trySetAccessible()) throw new IllegalAccessException("Native log reflow unavailable");
    }
    public Rect read() throws ReflectiveOperationException {
        return new Rect(((Number) x.invoke(companion)).intValue(), ((Number) y.invoke(companion)).intValue(),
            ((Number) width.invoke(companion)).intValue(), ((Number) height.invoke(companion)).intValue());
    }
    public void apply(Rect rect) throws ReflectiveOperationException {
        boolean changedWidth = ((Number) width.invoke(companion)).intValue() != rect.width();
        setX.invoke(companion, (double) rect.x());
        setY.invoke(companion, (double) rect.y());
        setWidth.invoke(companion, rect.width());
        setHeight.invoke(companion, rect.height());
        if (changedWidth) reflow.invoke(pane);
    }
}


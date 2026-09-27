package dev.krul.cobblemon;

import dev.krul.cobblemon.gui.TypeChartContent;
import dev.krul.cobblemon.gui.overlay.OverlayModule;

/**
 * Toggle for the standalone Cobblemon type-chart surface. Keeping this as a
 * module makes the chart independently discoverable without coupling it to
 * the Strategy tab or any automation.
 */
public final class TypeChart extends OverlayModule {
    public TypeChart() {
        super("type-chart", "Floating type chart over the world, PC and battle screens.",
            new TypeChartContent(), 20, 30);
    }

}

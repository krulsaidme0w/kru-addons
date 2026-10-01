package dev.krul.builder.blueprint;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.PlanValidator;
import java.util.*;

/** Immutable, Minecraft-independent final-stage projection for local plan previews. */
public final class PlanPreviewModel {
    public record CellView(Cell relativePosition, Cell worldPosition, StateKey state, String stageId) {
        public CellView { Objects.requireNonNull(relativePosition); Objects.requireNonNull(worldPosition); Objects.requireNonNull(state); Objects.requireNonNull(stageId); }
    }
    public record Projection(UUID project, long revision, Bounds bounds, List<CellView> cells) {
        public Projection { Objects.requireNonNull(project); Objects.requireNonNull(bounds); cells = List.copyOf(cells); }
    }
    private PlanPreviewModel() {}

    /** Later stages replace earlier assignments at the same relative cell. */
    public static Projection project(BuildPlan plan, Bounds bounds) {
        Objects.requireNonNull(plan); Objects.requireNonNull(bounds);
        try { if (bounds.volume() > PlanValidator.MAX_TARGETS) throw new IllegalArgumentException("Preview bounds exceed the cell limit"); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Preview bounds exceed the cell limit", overflow); }
        var combined = new LinkedHashMap<Cell, CellView>();
        for (var stage : plan.stages()) for (var target : stage.targets().entrySet()) {
            Cell relative = target.getKey();
            Cell absolute = new Cell(Math.addExact(bounds.min().x(), relative.x()),
                Math.addExact(bounds.min().y(), relative.y()), Math.addExact(bounds.min().z(), relative.z()));
            if (!bounds.contains(absolute)) throw new IllegalArgumentException("Plan target lies outside source bounds: " + relative);
            combined.put(relative, new CellView(relative, absolute, target.getValue(), stage.id()));
            if (combined.size() > PlanValidator.MAX_TARGETS) throw new IllegalArgumentException("Preview target count exceeds the cell limit");
        }
        var cells = new ArrayList<>(combined.values());
        cells.sort(Comparator.comparingInt((CellView cell) -> cell.relativePosition().y())
            .thenComparingInt(cell -> cell.relativePosition().z()).thenComparingInt(cell -> cell.relativePosition().x()));
        return new Projection(plan.project(), plan.revision(), bounds, cells);
    }
}

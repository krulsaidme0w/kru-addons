package dev.krul.builder.blueprint;

import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.util.*;

/** Pure, bounded comparison between a validated build plan and client-visible evidence. */
public final class BlueprintDiff {
    public enum Kind { MATCHING, PLACE, REMOVE, REPLACE, UNKNOWN }
    public record Difference(Cell relative, Cell absolute, StateKey target, StateKey actual, Kind kind, String stage) {}
    public record Selection(String stage, Integer layer) {
        public static final Selection ALL = new Selection(null, null);
        public Selection {
            if (stage != null && stage.isBlank()) throw new IllegalArgumentException("Stage must not be blank");
            if (layer != null && layer < 0) throw new IllegalArgumentException("Layer must be nonnegative");
        }
    }

    private BlueprintDiff() {}

    public static Progress compare(BuildPlan plan, WorldSnapshot snapshot) {
        List<Difference> differences = cells(plan, snapshot, Selection.ALL);
        int matching = 0, place = 0, replace = 0, unknown = 0;
        for (var difference : differences) switch (difference.kind()) {
            case MATCHING -> matching++;
            case PLACE -> place++;
            case REMOVE, REPLACE -> replace++;
            case UNKNOWN -> unknown++;
        }
        return new Progress(matching, place, replace, unknown);
    }

    public static List<Difference> cells(BuildPlan plan, WorldSnapshot snapshot, Selection selection) {
        Objects.requireNonNull(plan); Objects.requireNonNull(snapshot); Objects.requireNonNull(selection);
        if (plan.revision() < 1 || plan.contextRevision() < 1 || plan.stages().isEmpty()) {
            throw new IllegalArgumentException("Plan must have positive revisions and stages");
        }
        long count = 0;
        var targets = new TreeMap<Cell, BuildStage>(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z));
        var states = new HashMap<Cell, StateKey>();
        for (var stage : plan.stages()) {
            count += stage.targets().size();
            if (count > PlanValidator.MAX_TARGETS) throw new IllegalArgumentException("Plan exceeds target limit");
            for (var entry : stage.targets().entrySet()) {
                StateKey previous = states.putIfAbsent(entry.getKey(), entry.getValue());
                if (previous != null && !previous.equals(entry.getValue())) throw new IllegalArgumentException("Conflicting target " + entry.getKey());
                targets.putIfAbsent(entry.getKey(), stage);
            }
        }
        if (selection.stage() != null && plan.stages().stream().noneMatch(s -> s.id().equals(selection.stage()))) {
            throw new IllegalArgumentException("Unknown stage: " + selection.stage());
        }
        if (selection.stage() != null) {
            var selected = plan.stages().stream().filter(s -> s.id().equals(selection.stage())).findFirst().orElseThrow();
            targets.clear();
            selected.targets().keySet().forEach(cell -> targets.put(cell, selected));
        }
        var result = new ArrayList<Difference>();
        Cell origin = snapshot.bounds().min();
        for (var entry : targets.entrySet()) {
            Cell relative = entry.getKey(); BuildStage stage = entry.getValue();
            if (selection.layer() != null && relative.y() != selection.layer()) continue;
            if (!containsRelative(snapshot.bounds(), relative)) {
                result.add(new Difference(relative, null, states.get(relative), null, Kind.UNKNOWN, stage.id()));
                continue;
            }
            Cell absolute;
            try { absolute = new Cell(Math.addExact(origin.x(), relative.x()), Math.addExact(origin.y(), relative.y()), Math.addExact(origin.z(), relative.z())); }
            catch (ArithmeticException overflow) { throw new IllegalArgumentException("Target coordinate overflow", overflow); }
            StateKey actual = snapshot.known().get(absolute), target = states.get(relative);
            Kind kind;
            if (actual == null) kind = Kind.UNKNOWN;
            else if (actual.equals(target)) kind = Kind.MATCHING;
            else if (Progress.isAir(target)) kind = Kind.REMOVE;
            else if (Progress.isAir(actual)) kind = Kind.PLACE;
            else kind = Kind.REPLACE;
            result.add(new Difference(relative, absolute, target, actual, kind, stage.id()));
        }
        return List.copyOf(result);
    }

    private static boolean containsRelative(Bounds bounds, Cell cell) {
        return cell.x() >= 0 && cell.y() >= 0 && cell.z() >= 0
            && (long) cell.x() <= (long) bounds.max().x() - bounds.min().x()
            && (long) cell.y() <= (long) bounds.max().y() - bounds.min().y()
            && (long) cell.z() <= (long) bounds.max().z() - bounds.min().z();
    }
}

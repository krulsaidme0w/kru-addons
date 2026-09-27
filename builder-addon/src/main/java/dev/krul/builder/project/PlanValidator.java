package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;
import java.util.function.Predicate;

public final class PlanValidator {
    public static final int MAX_TARGETS = 32768;
    public enum Code { DUPLICATE_STAGE, MISSING_DEPENDENCY, CYCLIC_DEPENDENCY,
        CONFLICTING_TARGET, UNKNOWN_STATE, OUT_OF_BOUNDS, TOO_MANY_TARGETS,
        INVALID_PLAN, INCOMPLETE_PAIR, UNSUPPORTED_NBT,
        NO_REQUEST, WRONG_PROJECT, STALE_CONTEXT, STALE_REVISION, WRONG_WORLD,
        CHANGED_BOUNDS, INCOMPLETE_CONTEXT, REVIEW_REQUIRED }
    public record Diagnostic(Code code, String message) {
        @Override public String toString() { return code + ": " + message; }
    }

    /** Production callers supply registry rules; a predicate suffices for pure model tests. */
    public interface StateRules extends Predicate<StateKey> {
        List<Diagnostic> validateTargets(Map<Cell, StateKey> targets);
    }

    private PlanValidator() {}

    public static List<Diagnostic> diagnose(BuildPlan plan, Bounds bounds, Predicate<StateKey> stateExists) {
        Objects.requireNonNull(stateExists, "stateExists");
        var errors = new ArrayList<Diagnostic>();
        if (plan.revision() < 1 || plan.contextRevision() < 1 || plan.stages().isEmpty()) {
            errors.add(new Diagnostic(Code.INVALID_PLAN, "Positive revisions and at least one stage are required"));
        }
        long count = 0;
        for (var stage : plan.stages()) count += stage.targets().size();
        if (count > MAX_TARGETS || plan.stages().size() > MAX_TARGETS) {
            return List.of(new Diagnostic(Code.TOO_MANY_TARGETS, "Maximum " + MAX_TARGETS + " target entries/stages"));
        }
        var stages = new LinkedHashMap<String, BuildStage>();
        var targets = new HashMap<Cell, StateKey>();
        var checkedStates = new HashSet<StateKey>();
        for (var stage : plan.stages()) {
            if (stage.id().isBlank()) errors.add(new Diagnostic(Code.INVALID_PLAN, "Stage ID must not be blank"));
            if (stages.putIfAbsent(stage.id(), stage) != null) {
                errors.add(new Diagnostic(Code.DUPLICATE_STAGE, stage.id()));
            }
            stage.targets().forEach((cell, state) -> {
                if (!containsRelative(bounds, cell)) errors.add(new Diagnostic(Code.OUT_OF_BOUNDS, cell.toString()));
                var previous = targets.putIfAbsent(cell, state);
                if (previous != null && !previous.equals(state)) {
                    errors.add(new Diagnostic(Code.CONFLICTING_TARGET, cell.toString()));
                }
                if (checkedStates.add(state) && !stateExists.test(state)) {
                    errors.add(new Diagnostic(Code.UNKNOWN_STATE, state.toString()));
                }
            });
        }
        // Kahn traversal avoids stack overflow on long dependency chains.
        var degrees = new HashMap<String, Integer>();
        var dependents = new HashMap<String, List<String>>();
        for (var stage : stages.values()) {
            degrees.put(stage.id(), stage.after().size());
            for (var dependency : stage.after()) {
                if (!stages.containsKey(dependency)) {
                    errors.add(new Diagnostic(Code.MISSING_DEPENDENCY, stage.id() + " after " + dependency));
                } else dependents.computeIfAbsent(dependency, id -> new ArrayList<>()).add(stage.id());
            }
        }
        var ready = new ArrayDeque<String>();
        degrees.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            var id = ready.remove(); visited++;
            for (var dependent : dependents.getOrDefault(id, List.of())) {
                if (degrees.merge(dependent, -1, Integer::sum) == 0) ready.add(dependent);
            }
        }
        if (visited != stages.size() && errors.stream().noneMatch(e -> e.code() == Code.MISSING_DEPENDENCY)) {
            errors.add(new Diagnostic(Code.CYCLIC_DEPENDENCY, "Stage dependencies contain a cycle"));
        }
        if (errors.isEmpty() && stateExists instanceof StateRules rules) errors.addAll(rules.validateTargets(Map.copyOf(targets)));
        return List.copyOf(errors);
    }

    static boolean containsRelative(Bounds bounds, Cell cell) {
        return cell.x() >= 0 && cell.y() >= 0 && cell.z() >= 0
            && cell.x() <= (long) bounds.max().x() - bounds.min().x()
            && cell.y() <= (long) bounds.max().y() - bounds.min().y()
            && cell.z() <= (long) bounds.max().z() - bounds.min().z();
    }

    /** Compatibility facade; structured consumers should use diagnose. */
    public static List<String> validate(BuildPlan plan, Bounds bounds, Predicate<StateKey> stateExists) {
        return diagnose(plan, bounds, stateExists).stream().map(Diagnostic::toString).toList();
    }
}

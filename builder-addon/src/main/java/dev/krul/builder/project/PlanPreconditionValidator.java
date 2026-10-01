package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;
import java.util.function.Predicate;

/** Validates explicit plan transitions against one exact immutable world snapshot. */
public final class PlanPreconditionValidator {
    public record Diagnostic(String code, Cell relative, String message) {}
    public record Result(Optional<BuildPlan> previewPlan, List<Diagnostic> diagnostics,
                         int placeCount, int replaceCount, int removeCount) {
        public Result { previewPlan = Objects.requireNonNull(previewPlan); diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty() && previewPlan.isPresent(); }
    }

    private static final StateKey AIR = new StateKey("minecraft:air", Map.of());
    private final Predicate<StateKey> stateExists, isAir, unsupportedBlockEntity;

    public PlanPreconditionValidator(Predicate<StateKey> stateExists, Predicate<StateKey> isAir,
                                     Predicate<StateKey> unsupportedBlockEntity) {
        this.stateExists = Objects.requireNonNull(stateExists); this.isAir = Objects.requireNonNull(isAir);
        this.unsupportedBlockEntity = Objects.requireNonNull(unsupportedBlockEntity);
    }

    public Result validate(PlanContract contract, WorldSnapshot snapshot, UUID project) {
        Objects.requireNonNull(contract); Objects.requireNonNull(snapshot); Objects.requireNonNull(project);
        var errors = new ArrayList<Diagnostic>();
        if (snapshot.revision() < 1 || snapshot.bounds().volume() > PlanValidator.MAX_TARGETS) {
            return invalid(errors, "INVALID_SNAPSHOT", null, "Snapshot is missing or exceeds the plan cell limit");
        }
        Map<Cell, StateKey> projected = new HashMap<>();
        Map<Cell, String> lastStage = new HashMap<>();
        Map<Cell, String> finalOwner = new HashMap<>();
        Map<Cell, StateKey> finalAssignments = new HashMap<>();
        List<BuildStage> stages = new ArrayList<>();
        Set<String> stageIds = new HashSet<>();
        int places = 0, replacements = 0, removals = 0, operationCount = 0;
        for (PlanContract.Stage stage : contract.stages()) {
            if (!stageIds.add(stage.id())) { errors.add(diag("DUPLICATE_STAGE", null, "Duplicate stage " + stage.id())); continue; }
            for (String dependency : stage.after()) if (!stageIds.contains(dependency) || dependency.equals(stage.id()))
                errors.add(diag("INVALID_DEPENDENCY", null, "Stage " + stage.id() + " must depend only on prior stages"));
            Set<Cell> withinStage = new HashSet<>();
            for (PlanOperation operation : stage.operations()) {
                if (++operationCount > PlanValidator.MAX_TARGETS) {
                    errors.add(diag("TOO_MANY_OPERATIONS", operation.position(), "Maximum " + PlanValidator.MAX_TARGETS + " operations")); break;
                }
                Cell relative = operation.position();
                if (!inside(snapshot.bounds(), relative)) { errors.add(diag("OUT_OF_BOUNDS", relative, "Cell is outside selected bounds")); continue; }
                if (!withinStage.add(relative)) { errors.add(diag("DUPLICATE_CELL", relative, "Stage assigns this cell more than once")); continue; }
                String priorWriter = lastStage.get(relative);
                if (priorWriter != null && !stage.after().contains(priorWriter)) {
                    errors.add(diag("MISSING_STAGE_ORDER", relative, "Stage must depend on prior writer '" + priorWriter + "'")); continue;
                }
                Cell absolute;
                try { absolute = new Cell(Math.addExact(snapshot.bounds().min().x(), relative.x()),
                    Math.addExact(snapshot.bounds().min().y(), relative.y()), Math.addExact(snapshot.bounds().min().z(), relative.z())); }
                catch (ArithmeticException overflow) { errors.add(diag("COORDINATE_OVERFLOW", relative, "Absolute position overflow")); continue; }
                StateKey current = projected.getOrDefault(relative, snapshot.known().get(absolute));
                if (current == null || snapshot.unknown().contains(absolute)) {
                    errors.add(diag("UNKNOWN_CELL", relative, "Cell state is unknown or unloaded")); continue;
                }
                StateKey target = operation.target() == null ? AIR : contract.palette().get(operation.target());
                if (operation.target() != null && target == null) { errors.add(diag("UNKNOWN_PALETTE_KEY", relative, operation.target())); continue; }
                boolean valid = switch (operation.kind()) {
                    case PLACE -> {
                        if (!isAir.test(current)) { errors.add(diag("PLACE_NOT_AIR", relative, "PLACE requires known air; found " + current.id())); yield false; }
                        yield validateState(target, relative, errors);
                    }
                    case REPLACE -> {
                        if (!current.equals(operation.expected())) { errors.add(diag("EXPECTED_STATE_MISMATCH", relative, "Expected " + operation.expected() + " but found " + current)); yield false; }
                        if (isAir.test(current)) { errors.add(diag("REPLACE_AIR", relative, "Use PLACE for an air cell")); yield false; }
                        if (unsupportedBlockEntity.test(current)) { errors.add(diag("UNSUPPORTED_BLOCK_ENTITY", relative, "Existing block entity data is not represented by full block states")); yield false; }
                        if (current.equals(target)) { errors.add(diag("NO_OP", relative, "Replacement state is unchanged")); yield false; }
                        yield validateState(target, relative, errors);
                    }
                    case REMOVE -> {
                        if (!current.equals(operation.expected())) { errors.add(diag("EXPECTED_STATE_MISMATCH", relative, "Expected " + operation.expected() + " but found " + current)); yield false; }
                        if (isAir.test(current)) { errors.add(diag("REMOVE_AIR", relative, "Cell is already air")); yield false; }
                        if (unsupportedBlockEntity.test(current)) { errors.add(diag("UNSUPPORTED_BLOCK_ENTITY", relative, "Existing block entity data is not represented by full block states")); yield false; }
                        yield validateState(AIR, relative, errors);
                    }
                };
                if (!valid) continue;
                projected.put(relative, target); finalAssignments.put(relative, target); finalOwner.put(relative, stage.id());
                lastStage.put(relative, stage.id());
                switch (operation.kind()) { case PLACE -> places++; case REPLACE -> replacements++; case REMOVE -> removals++; }
                if (finalAssignments.size() > PlanValidator.MAX_TARGETS) errors.add(diag("TOO_MANY_TARGETS", relative, "Maximum " + PlanValidator.MAX_TARGETS + " final cell assignments"));
            }
        }
        if (!errors.isEmpty()) return new Result(Optional.empty(), errors, places, replacements, removals);
        for (PlanContract.Stage stage : contract.stages()) {
            Map<Cell, StateKey> visibleTargets = new LinkedHashMap<>();
            finalAssignments.forEach((cell, state) -> { if (stage.id().equals(finalOwner.get(cell))) visibleTargets.put(cell, state); });
            stages.add(new BuildStage(stage.id(), stage.title(), new HashSet<>(stage.after()), visibleTargets));
        }
        BuildPlan preview = new BuildPlan(project, contract.revision(), snapshot.revision(), stages);
        List<String> existingErrors = PlanValidator.validate(preview, snapshot.bounds(), stateExists);
        if (!existingErrors.isEmpty()) return new Result(Optional.empty(), existingErrors.stream()
            .map(message -> diag("INVALID_PREVIEW_PLAN", null, message)).toList(), places, replacements, removals);
        return new Result(Optional.of(preview), List.of(), places, replacements, removals);
    }

    private boolean validateState(StateKey state, Cell cell, List<Diagnostic> errors) {
        if (!stateExists.test(state)) { errors.add(diag("UNKNOWN_STATE", cell, "Block state is not present in the active registry: " + state)); return false; }
        if (unsupportedBlockEntity.test(state)) { errors.add(diag("UNSUPPORTED_BLOCK_ENTITY", cell, "Target block entity requirements are not represented by full block states")); return false; }
        return true;
    }
    private static boolean inside(Bounds bounds, Cell relative) {
        return relative.x() >= 0 && relative.y() >= 0 && relative.z() >= 0
            && (long) relative.x() <= (long) bounds.max().x() - bounds.min().x()
            && (long) relative.y() <= (long) bounds.max().y() - bounds.min().y()
            && (long) relative.z() <= (long) bounds.max().z() - bounds.min().z();
    }
    private static Diagnostic diag(String code, Cell cell, String message) { return new Diagnostic(code, cell, message); }
    private static Result invalid(List<Diagnostic> errors, String code, Cell cell, String message) {
        errors.add(diag(code, cell, message)); return new Result(Optional.empty(), errors, 0, 0, 0);
    }
}

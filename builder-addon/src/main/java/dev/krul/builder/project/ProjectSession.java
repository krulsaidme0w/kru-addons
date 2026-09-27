package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;
import java.util.function.Predicate;
import static dev.krul.builder.project.PlanValidator.Code.*;

/** Client-thread-owned request/review state. Create a new session after a world/selection change. */
public final class ProjectSession {
    private final UUID project;
    private final UUID world;
    private final Bounds bounds;
    private final Predicate<StateKey> stateExists;
    private WorldSnapshot baseline;
    private long highestRevision;
    private boolean requesting = true;
    private BuildPlan accepted;
    private BuildPlan draft;

    public ProjectSession(UUID project, WorldSnapshot baseline, Predicate<StateKey> stateExists) {
        this.project = Objects.requireNonNull(project);
        this.baseline = Objects.requireNonNull(baseline);
        this.stateExists = Objects.requireNonNull(stateExists);
        world = baseline.session(); bounds = baseline.bounds();
        if (baseline.revision() < 1 || !complete(baseline)) {
            throw new IllegalArgumentException("Request requires complete known context with a positive revision");
        }
    }

    /** Each request needs a new scan so delayed responses cannot reuse a cancelled request baseline. */
    public List<String> beginRequest(WorldSnapshot baseline) {
        var errors = contextErrors(baseline);
        if (!errors.isEmpty()) return errors;
        this.baseline = baseline;
        requesting = true;
        draft = null;
        return List.of();
    }

    public List<String> propose(BuildPlan plan) {
        if (!requesting) return error(NO_REQUEST, "Request was cancelled or accepted");
        if (!project.equals(plan.project())) return error(WRONG_PROJECT, "Proposal belongs to another project");
        if (plan.contextRevision() != baseline.revision()) return error(STALE_CONTEXT, "Proposal must reference the request baseline");
        if (plan.revision() <= highestRevision) return error(STALE_REVISION, "Proposal revision must advance");
        var errors = PlanValidator.validate(plan, bounds, stateExists);
        if (!errors.isEmpty()) return errors;
        highestRevision = plan.revision();
        draft = plan;
        return List.of();
    }

    public List<String> accept(long revision, WorldSnapshot freshSnapshot) {
        if (!requesting || draft == null) return error(NO_REQUEST, "No proposal to accept");
        if (draft.revision() != revision) return error(STALE_REVISION, "Review the current draft revision");
        var errors = contextErrors(freshSnapshot);
        if (!errors.isEmpty()) return errors;
        if (!baseline.known().equals(freshSnapshot.known())) {
            return error(REVIEW_REQUIRED, "World content changed; request and review a new proposal");
        }
        errors = PlanValidator.validate(draft, bounds, stateExists);
        if (!errors.isEmpty()) return errors;
        accepted = draft;
        draft = null;
        requesting = false;
        return List.of();
    }

    private List<String> contextErrors(WorldSnapshot snapshot) {
        if (!world.equals(snapshot.session())) return error(WRONG_WORLD, "Context belongs to another world session");
        if (!bounds.equals(snapshot.bounds())) return error(CHANGED_BOUNDS, "Selection changed");
        if (snapshot.revision() <= baseline.revision()) return error(STALE_CONTEXT, "A new complete scan is required");
        if (!complete(snapshot)) return error(INCOMPLETE_CONTEXT, "Unknown or omitted cells cannot authorize acceptance");
        return List.of();
    }

    private static boolean complete(WorldSnapshot snapshot) {
        try {
            return snapshot.unknown().isEmpty() && snapshot.bounds().volume() <= PlanValidator.MAX_TARGETS
                && snapshot.known().size() == snapshot.bounds().volume();
        } catch (ArithmeticException overflow) { return false; }
    }

    private static List<String> error(PlanValidator.Code code, String message) {
        return List.of(new PlanValidator.Diagnostic(code, message).toString());
    }

    public void cancelRequest() { requesting = false; draft = null; }
    public Optional<BuildPlan> accepted() { return Optional.ofNullable(accepted); }
    public Optional<BuildPlan> draft() { return Optional.ofNullable(draft); }

    /** Installs an explicitly reopened plan after validating it against the current selection and registry. */
    public List<String> restoreAccepted(BuildPlan plan) {
        Objects.requireNonNull(plan);
        if (!project.equals(plan.project())) return error(WRONG_PROJECT, "Saved plan belongs to another project");
        var errors = PlanValidator.validate(plan, bounds, stateExists);
        if (!errors.isEmpty()) return errors;
        accepted = plan; highestRevision = Math.max(highestRevision, plan.revision());
        draft = null; requesting = false;
        return List.of();
    }

    public Progress progress(WorldSnapshot snapshot) {
        if (accepted == null) return new Progress(0, 0, 0, 0);
        if (!world.equals(snapshot.session()) || !bounds.equals(snapshot.bounds())) {
            return new Progress(0, 0, 0, Progress.targets(accepted).size());
        }
        return Progress.measure(accepted, snapshot);
    }
}

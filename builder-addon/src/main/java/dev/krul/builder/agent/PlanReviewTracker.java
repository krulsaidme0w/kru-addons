package dev.krul.builder.agent;

import dev.krul.builder.project.BuildPlan;
import java.util.Objects;

/** Per-turn inspection budget and proof that the final proposal is exactly the inspected revision. */
public final class PlanReviewTracker {
    private int calls;
    private BuildPlan inspected;
    private boolean succeeded;
    private String failure = "Codex did not inspect the proposed plan";

    public synchronized void reset() {
        calls = 0; inspected = null; succeeded = false; failure = "Codex did not inspect the proposed plan";
    }
    public synchronized boolean beginCall() {
        if (++calls > 2) { succeeded = false; failure = "Plan inspection limit reached; only a draft and one revised plan may be inspected"; return false; }
        succeeded = false;
        return true;
    }
    public synchronized boolean canInspect(BuildPlan plan) {
        Objects.requireNonNull(plan);
        if (calls == 2 && inspected != null && plan.revision() <= inspected.revision()) {
            failure = "The second inspection must use a higher revision than the inspected draft";
            return false;
        }
        return true;
    }
    public synchronized void complete(BuildPlan plan) { inspected = Objects.requireNonNull(plan); succeeded = true; failure = ""; }
    public synchronized void fail(String reason) {
        succeeded = false; failure = reason == null || reason.isBlank() ? "Plan inspection failed" : reason;
    }
    public synchronized BuilderDynamicTools.Review review(BuildPlan finalPlan) {
        boolean exact = succeeded && Objects.equals(inspected, finalPlan);
        return new BuilderDynamicTools.Review(calls > 0, exact,
            exact ? "" : succeeded ? "Final proposal differs from the visually inspected plan" : failure);
    }
}

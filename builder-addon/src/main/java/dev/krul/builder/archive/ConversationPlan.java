package dev.krul.builder.archive;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.project.BuildPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ConversationPlan(BuildPlan plan, Lifecycle lifecycle, UUID contextSession, long contextRevision,
                               Optional<Bounds> sourceBounds, List<String> diagnostics,
                               boolean visualReviewed, Optional<String> visualReviewFailure) {
    public enum Lifecycle { PROPOSED, REVISED, REJECTED, SAVED }

    public ConversationPlan {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(contextSession, "contextSession");
        if (contextRevision < 0 || plan.contextRevision() != contextRevision)
            throw new IllegalArgumentException("Plan context revision does not match archive metadata");
        sourceBounds = sourceBounds == null ? Optional.empty() : sourceBounds;
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (diagnostics.size() > 256 || diagnostics.stream().anyMatch(s -> s == null || s.length() > 4096))
            throw new IllegalArgumentException("Plan diagnostics exceed limit");
        visualReviewFailure = visualReviewFailure == null ? Optional.empty() : visualReviewFailure;
        visualReviewFailure.ifPresent(value -> {
            if (value.length() > 4096) throw new IllegalArgumentException("Visual review failure exceeds limit");
        });
        if (visualReviewed && visualReviewFailure.isPresent())
            throw new IllegalArgumentException("A reviewed plan cannot contain a review failure");
    }
}

package dev.krul.builder.agent;

import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.BuildPlan;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AgentRequest(UUID project, UUID session, long contextRevision, String text,
                           WorldSnapshot context, List<Path> images, Optional<BuildPlan> acceptedPlan) {
    public AgentRequest {
        Objects.requireNonNull(project); Objects.requireNonNull(session); Objects.requireNonNull(text);
        Objects.requireNonNull(context); Objects.requireNonNull(images); Objects.requireNonNull(acceptedPlan);
        if (contextRevision < 0) throw new IllegalArgumentException("contextRevision must be nonnegative");
        if (!session.equals(context.session()) || contextRevision != context.revision())
            throw new IllegalArgumentException("Request identity must match its immutable context");
        if (acceptedPlan.isPresent() && !project.equals(acceptedPlan.get().project()))
            throw new IllegalArgumentException("Accepted plan belongs to another project");
        images = List.copyOf(images);
    }

    public AgentRequest(UUID project, UUID session, long contextRevision, String text,
                        WorldSnapshot context, List<Path> images) {
        this(project, session, contextRevision, text, context, images, Optional.empty());
    }
}

package dev.krul.builder.project;

import java.util.*;

public record BuildPlan(UUID project, long revision, long contextRevision, List<BuildStage> stages) {
    public BuildPlan {
        Objects.requireNonNull(project, "project");
        stages = List.copyOf(stages);
    }
}

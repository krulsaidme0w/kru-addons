package dev.krul.builder.archive;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ConversationArchive(UUID id, String title, long createdAtEpochMillis, long updatedAtEpochMillis,
                                  List<ConversationEntry> entries, List<ConversationPlan> plans) {
    public static final int MAX_ENTRIES = 10_000;
    public static final int MAX_PLANS = 512;

    public ConversationArchive {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (title.isBlank() || title.length() > 256) throw new IllegalArgumentException("Invalid conversation title");
        if (createdAtEpochMillis < 0 || updatedAtEpochMillis < createdAtEpochMillis)
            throw new IllegalArgumentException("Invalid conversation timestamps");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        if (entries.size() > MAX_ENTRIES || plans.size() > MAX_PLANS)
            throw new IllegalArgumentException("Conversation history exceeds limit");
        if (entries.stream().anyMatch(Objects::isNull) || plans.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Conversation history contains null entries");
        if (plans.stream().anyMatch(plan -> !id.equals(plan.plan().project())))
            throw new IllegalArgumentException("Plan belongs to another conversation");
        var planRevisions = plans.stream().map(plan -> plan.plan().revision()).collect(java.util.stream.Collectors.toSet());
        if (entries.stream().flatMap(entry -> entry.planRevision().stream()).anyMatch(revision -> !planRevisions.contains(revision)))
            throw new IllegalArgumentException("Conversation timeline references a missing plan revision");
    }
}

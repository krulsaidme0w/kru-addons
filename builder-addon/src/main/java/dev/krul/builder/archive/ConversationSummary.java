package dev.krul.builder.archive;

import java.util.Objects;
import java.util.UUID;

public record ConversationSummary(UUID id, String title, long updatedAtEpochMillis, int messageCount, boolean hasPlans) {
    public ConversationSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (title.isBlank() || title.length() > 256 || updatedAtEpochMillis < 0 || messageCount < 0)
            throw new IllegalArgumentException("Invalid conversation summary");
    }

    public static ConversationSummary from(ConversationArchive archive) {
        return new ConversationSummary(archive.id(), archive.title(), archive.updatedAtEpochMillis(),
            archive.entries().size(), !archive.plans().isEmpty());
    }
}

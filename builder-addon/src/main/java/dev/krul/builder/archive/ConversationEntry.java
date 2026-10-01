package dev.krul.builder.archive;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ConversationEntry(UUID id, Kind kind, long createdAtEpochMillis, String text,
                                Optional<BuilderDiagnostic> diagnostic, Optional<Long> planRevision) {
    public enum Kind { USER, AGENT, FAILURE, SYSTEM }

    public ConversationEntry(UUID id, Kind kind, long createdAtEpochMillis, String text,
                             Optional<BuilderDiagnostic> diagnostic) {
        this(id, kind, createdAtEpochMillis, text, diagnostic, Optional.empty());
    }

    public ConversationEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (createdAtEpochMillis < 0) throw new IllegalArgumentException("Entry time must be nonnegative");
        Objects.requireNonNull(text, "text");
        if (text.length() > 65_536) throw new IllegalArgumentException("Entry text exceeds limit");
        diagnostic = diagnostic == null ? Optional.empty() : diagnostic;
        planRevision = planRevision == null ? Optional.empty() : planRevision;
        if (planRevision.isPresent() && (kind != Kind.SYSTEM || planRevision.orElseThrow() < 1))
            throw new IllegalArgumentException("Only system timeline entries may reference a positive plan revision");
        if ((kind == Kind.FAILURE) != diagnostic.isPresent())
            throw new IllegalArgumentException("Only failure entries carry a diagnostic");
    }
}

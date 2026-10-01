package dev.krul.builder.archive;

import dev.krul.builder.project.ProjectStore;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Idempotently imports the former single-project file without activating it in a world. */
public final class LegacyProjectMigration {
    public enum Result { IMPORTED, ALREADY_IMPORTED, ABSENT }
    private static final String IMPORT_MARKER = "Imported a saved Builder plan. It is archived only and must be revalidated against a fresh scan before use.";
    private static final String IMPORT_NOTE = "Imported from the legacy project store; requires fresh world validation";
    private LegacyProjectMigration() {}

    public static Result migrate(ProjectStore legacy, ConversationArchiveStore archives, UUID project,
                                 long nowEpochMillis) throws IOException {
        Optional<dev.krul.builder.project.BuildPlan> old = legacy.load(project);
        if (old.isEmpty()) return Result.ABSENT;
        if (!old.orElseThrow().project().equals(project)) throw new IOException("Legacy plan belongs to another project");
        Optional<ConversationArchive> existing = archives.load(project);
        if (existing.filter(archive -> archive.entries().stream().anyMatch(entry -> IMPORT_MARKER.equals(entry.text()))
                && archive.plans().stream().anyMatch(item -> item.plan().equals(old.orElseThrow())
                    && item.lifecycle() == ConversationPlan.Lifecycle.SAVED && item.diagnostics().contains(IMPORT_NOTE))).isPresent())
            return Result.ALREADY_IMPORTED;
        var plan = new ConversationPlan(old.orElseThrow(), ConversationPlan.Lifecycle.SAVED, project,
            old.orElseThrow().contextRevision(), Optional.empty(), List.of(IMPORT_NOTE),
            false, Optional.empty());
        var entry = new ConversationEntry(UUID.randomUUID(), ConversationEntry.Kind.SYSTEM, nowEpochMillis,
            IMPORT_MARKER, Optional.empty(), Optional.of(old.orElseThrow().revision()));
        var prior = existing.orElse(null);
        var entries = new java.util.ArrayList<ConversationEntry>();
        if (prior != null) entries.addAll(prior.entries());
        entries.add(entry);
        var plans = new java.util.ArrayList<ConversationPlan>();
        if (prior != null) plans.addAll(prior.plans());
        plans.add(plan);
        long created = prior == null ? nowEpochMillis : prior.createdAtEpochMillis();
        long updated = Math.max(nowEpochMillis, created);
        var archive = new ConversationArchive(project, prior == null ? "Recovered project" : prior.title(),
            created, Math.max(updated, prior == null ? 0 : prior.updatedAtEpochMillis()), entries, plans);
        archives.save(archive);
        return Result.IMPORTED;
    }
}

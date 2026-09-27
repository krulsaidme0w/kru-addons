package dev.krul.builder.ui;

import dev.krul.builder.agent.AgentClient;
import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.Progress;
import dev.krul.builder.visual.AttachmentPolicy;

import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable render model; widgets never own service or world state. */
public record BuilderUiState(Phase phase, AgentClient.Status connection, String status,
                             List<Message> conversation, Optional<WorldSnapshot> snapshot,
                             List<AttachmentPolicy.Attachment> attachments,
                             Map<UUID, Thumbnail> thumbnails,
                             Optional<Prepared> prepared, Optional<BuildPlan> draft,
                             Optional<BuildPlan> accepted, Progress progress,
                             boolean collapsed, boolean settingsExpanded,
                             String selectedStage, Integer selectedLayer,
                             String executable,
                             URI authorizationUrl, String userCode,
                             boolean nonAtomicSave, List<String> cleanupWarnings,
                             boolean cloudConsent) {
    public enum Phase { DISCONNECTED, READY, PREPARING, WAITING, FAILED, CANCELLED }
    public record Message(String role, String text) {}
    public record Prepared(String text, long contextRevision, UUID session,
                           List<AttachmentPolicy.Attachment> attachments) {
        public Prepared { attachments = List.copyOf(attachments); }
    }
    public record Thumbnail(int width, int height, byte[] rgba) {
        public Thumbnail { rgba = rgba.clone(); }
        @Override public byte[] rgba() { return rgba.clone(); }
    }

    public BuilderUiState {
        conversation = List.copyOf(conversation);
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        attachments = List.copyOf(attachments);
        thumbnails = Map.copyOf(thumbnails);
        prepared = prepared == null ? Optional.empty() : prepared;
        draft = draft == null ? Optional.empty() : draft;
        accepted = accepted == null ? Optional.empty() : accepted;
        cleanupWarnings = List.copyOf(cleanupWarnings);
    }
}

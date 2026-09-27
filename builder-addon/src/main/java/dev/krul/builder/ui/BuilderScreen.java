package dev.krul.builder.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WTexture;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.renderer.Texture;
import dev.krul.builder.project.BuildPlan;

import java.util.*;

/** Meteor-themed chat window. All state and actions are delegated to BuilderController. */
public final class BuilderScreen extends WindowScreen {
    private final BuilderController controller;
    private final Map<UUID, Texture> textures = new HashMap<>();
    private final Map<UUID, Integer> textureHashes = new HashMap<>();
    private WLabel status, stageSummary, planSummary, progressSummary, saveSummary;
    private WView transcript, attachmentStrip;
    private WHorizontalList actions;
    private WTextBox composer;
    private WSection connectionSection;
    private WView connectionDetails;
    private int transcriptHash, attachmentHash, actionHash, settingsHash;

    public BuilderScreen(GuiTheme theme, BuilderController controller) {
        super(theme, "Builder");
        this.controller = Objects.requireNonNull(controller);
        window.view.maxHeight = Math.max(theme.scale(190), height - theme.scale(72));
        onClosed(this::disposeTextures);
        enterAction = this::prepare;
    }

    @Override public void initWidgets() {
        var state = controller.state();
        add(theme.horizontalSeparator("Collaborative building"));
        status = add(theme.label(state.status())).expandX().widget();
        planSummary = add(theme.label(planLine(state))).expandX().widget();
        progressSummary = add(theme.label(progressLine(state))).expandX().widget();
        saveSummary = add(theme.label(saveLine(state))).expandX().widget();
        stageSummary = add(theme.label(stageLine(state))).expandX().widget();
        add(theme.horizontalSeparator("Attachments — reviewed locally before sending"));
        attachmentStrip = theme.view(); attachmentStrip.maxHeight = theme.scale(82);
        add(attachmentStrip).expandX();
        add(theme.horizontalSeparator("Conversation"));
        transcript = theme.view(); transcript.maxHeight = theme.scale(116);
        add(transcript).expandX();
        composer = theme.textBox("", "Describe the build or revision…");
        composer.action = this::prepare;
        add(composer).expandX();
        actions = theme.horizontalList(); add(actions).expandX();
        connectionSection = theme.section("Connection and executable", false);
        connectionDetails = theme.view(); connectionDetails.maxHeight = theme.scale(90);
        connectionSection.add(connectionDetails).expandX();
        connectionSection.add(theme.button("Reconnect / check sign-in")).expandX().widget().action = controller::reconnect;
        connectionSection.add(theme.button("Sign in with Codex")).expandX().widget().action = controller::login;
        add(connectionSection).expandX();
        add(theme.button("Reopen saved project")).expandX().widget().action = controller::reopenProject;
        refreshDynamic(state);
    }

    @Override public void tick() {
        super.tick();
        var state = controller.state();
        status.set(state.status());
        planSummary.set(planLine(state)); progressSummary.set(progressLine(state)); stageSummary.set(stageLine(state));
        saveSummary.set(saveLine(state));
        refreshDynamic(state);
    }

    private void refreshDynamic(BuilderUiState state) {
        int nextTranscriptHash = state.conversation().hashCode();
        if (nextTranscriptHash != transcriptHash) {
            transcript.clear();
            if (state.conversation().isEmpty()) transcript.add(theme.label("Your project discussion appears here.")).expandX();
            for (var message : state.conversation()) transcript.add(theme.label(message.role() + ": " + message.text(), 280)).expandX();
            transcriptHash = nextTranscriptHash;
        }
        int nextAttachmentsHash = Objects.hash(state.attachments().stream().map(a -> a.id()).toList(), state.thumbnails().keySet());
        if (nextAttachmentsHash != attachmentHash) {
            attachmentStrip.clear();
            WHorizontalList row = theme.horizontalList();
            for (var attachment : state.attachments()) {
                var thumbnail = state.thumbnails().get(attachment.id());
                if (thumbnail != null) {
                    int hash = Arrays.hashCode(thumbnail.rgba());
                    Texture texture = textures.get(attachment.id());
                    if (texture == null || !Objects.equals(textureHashes.get(attachment.id()), hash)) {
                        if (texture != null) disposeTexture(texture);
                        texture = new Texture(thumbnail.width(), thumbnail.height(), thumbnail.rgba(), Texture.Format.RGBA,
                            Texture.Filter.Linear, Texture.Filter.Linear);
                        textures.put(attachment.id(), texture); textureHashes.put(attachment.id(), hash);
                    }
                    double ratio = (double) thumbnail.width() / thumbnail.height();
                    row.add(theme.texture(Math.min(88, 58 * ratio), Math.min(58, 88 / ratio), 0, texture)).centerY();
                } else row.add(theme.label("Preparing preview…"));
                row.add(theme.label(attachment.width() + "×" + attachment.height()));
                row.add(theme.button("Remove")).widget().action = () -> controller.removeAttachment(attachment.id());
            }
            if (state.attachments().isEmpty()) row.add(theme.label("No image will be sent until a screenshot is prepared."));
            attachmentStrip.add(row).expandX();
            textures.keySet().removeIf(id -> {
                if (state.attachments().stream().anyMatch(a -> a.id().equals(id))) return false;
                Texture texture = textures.remove(id); disposeTexture(texture); textureHashes.remove(id); return true;
            });
            attachmentHash = nextAttachmentsHash;
        }
        int nextActionHash = Objects.hash(state.phase(), state.prepared().isPresent(), state.thumbnails().keySet(), state.attachments().stream().map(a -> a.id()).toList(), state.draft().map(BuildPlan::revision).orElse(-1L), state.accepted().map(BuildPlan::revision).orElse(-1L));
        if (nextActionHash != actionHash) {
            actions.clear();
            boolean previewReady = state.prepared().isPresent() && state.prepared().orElseThrow().attachments().stream()
                .allMatch(attachment -> state.thumbnails().containsKey(attachment.id()));
            if (previewReady) actions.add(theme.button("Confirm and send to Codex")).widget().action = controller::confirmSend;
            else if (state.prepared().isPresent()) actions.add(theme.label("Preparing screenshot preview locally…"));
            else if (state.connection().connection() == dev.krul.builder.agent.AgentClient.Connection.READY
                    && state.connection().login() == dev.krul.builder.agent.AgentClient.Login.AUTHENTICATED
                    && state.phase() != BuilderUiState.Phase.PREPARING && state.phase() != BuilderUiState.Phase.WAITING)
                actions.add(theme.button("Prepare for review")).widget().action = this::prepare;
            else if (state.phase() != BuilderUiState.Phase.PREPARING && state.phase() != BuilderUiState.Phase.WAITING)
                actions.add(theme.button("Connect / sign in")).widget().action = controller::login;
            if (state.phase() == BuilderUiState.Phase.PREPARING || state.phase() == BuilderUiState.Phase.WAITING)
                actions.add(theme.button("Cancel request")).widget().action = controller::cancel;
            if (state.draft().isPresent()) {
                long revision = state.draft().orElseThrow().revision();
                actions.add(theme.button("Accept revision " + revision)).widget().action = () -> controller.accept(revision);
                actions.add(theme.button("Reject draft")).widget().action = controller::reject;
            }
            if (state.accepted().isPresent() || state.draft().isPresent()) {
                actions.add(theme.button("‹ Stage")).widget().action = () -> controller.selectStage(-1);
                actions.add(theme.button("Stage ›")).widget().action = () -> controller.selectStage(1);
                actions.add(theme.button("Layer −")).widget().action = () -> controller.selectLayer(-1);
                actions.add(theme.button("Layer +")).widget().action = () -> controller.selectLayer(1);
                actions.add(theme.button("All layers")).widget().action = controller::showAllLayers;
            }
            actions.add(theme.button("Collapse to HUD")).widget().action = () -> {
                controller.toggleCollapsed(); client.setScreen(null);
            };
            actionHash = nextActionHash;
        }
        int nextSettingsHash = Objects.hash(state.connection(), state.authorizationUrl(), state.userCode(), state.executable());
        if (nextSettingsHash != settingsHash) {
            connectionDetails.clear();
            connectionDetails.add(theme.label("Executable: " + state.executable() + " (edit in Builder module settings)", 280)).expandX();
            connectionDetails.add(theme.label(state.connection().connection() + " · " + state.connection().login() + " · " + state.connection().detail(), 280)).expandX();
            if (state.authorizationUrl() != null) connectionDetails.add(theme.label("Open: " + state.authorizationUrl(), 280)).expandX();
            if (state.userCode() != null) connectionDetails.add(theme.label("Device code: " + state.userCode(), true)).expandX();
            settingsHash = nextSettingsHash;
        }
    }

    private void prepare() {
        String text = composer.get();
        controller.send(text);
        composer.set("");
        composer.setFocused(false);
    }

    private static String planLine(BuilderUiState state) {
        String draft = state.draft().map(p -> "Proposed r" + p.revision()).orElse("No proposal");
        String accepted = state.accepted().map(p -> "Accepted r" + p.revision()).orElse("No accepted plan");
        return draft + "  ·  " + accepted;
    }
    private static String progressLine(BuilderUiState state) {
        return "World progress: matching " + state.progress().matching() + ", place " + state.progress().place()
            + ", replace " + state.progress().replace() + ", unknown " + state.progress().unknown();
    }
    private static String stageLine(BuilderUiState state) {
        return "Preview: " + (state.selectedStage() == null ? "all stages" : state.selectedStage())
            + " · layer " + (state.selectedLayer() == null ? "all" : state.selectedLayer());
    }
    private static String saveLine(BuilderUiState state) {
        if (!state.cleanupWarnings().isEmpty()) return "Plan saved; cleanup warnings: " + String.join(" · ", state.cleanupWarnings());
        return state.nonAtomicSave() ? "Plan saved with a non-atomic filesystem fallback" : "";
    }

    private void disposeTextures() {
        textures.values().forEach(this::disposeTexture); textures.clear(); textureHashes.clear();
    }
    private void disposeTexture(Texture texture) {
        if (texture == null) return;
        if (RenderSystem.isOnRenderThread()) texture.dispose(); else RenderSystem.recordRenderCall(texture::dispose);
    }
}

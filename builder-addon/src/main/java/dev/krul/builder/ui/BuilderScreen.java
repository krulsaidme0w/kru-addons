package dev.krul.builder.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.krul.builder.agent.AgentClient;
import dev.krul.builder.project.BuildPlan;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Compact Meteor-themed chat window. Actions and session state stay in BuilderController. */
public final class BuilderScreen extends WindowScreen {
    private final BuilderController controller;
    private final java.util.function.Supplier<WWidget> boundsEditor;
    private final BooleanSupplier overlayVisible;
    private final Runnable toggleOverlay;
    private final Map<UUID, Texture> textures = new HashMap<>();
    private final Map<UUID, Integer> textureHashes = new HashMap<>();
    private BuilderWrappedLabel summary, statusLabel;
    private WView content, attachmentStrip;
    private WVerticalList settingsPanel;
    private WVerticalList conversationList;
    private WView conversationPane;
    private WHorizontalList workspace;
    private boolean forceConversationPane;
    private boolean paneLayoutNarrow;
    private WWidget boundsEditorWidget;
    private WVerticalList stageActions, referenceControls, attachmentRows;
    private WHorizontalList primaryAction, imageRow;
    private WButton settingsButton, overlayButton;
    private WTextBox composer, referencePathBox;
    private int attachmentHash, actionHash, contentHash, settingsContentHash;
    private double textWrapWidth = -1;
    private Boolean settingsLayout;
    private String savedComposerDraft = "";
    private String savedReferencePath = "";
    private boolean showReferencePath;

    public BuilderScreen(GuiTheme theme, BuilderController controller, java.util.function.Supplier<WWidget> boundsEditor,
                         BooleanSupplier overlayVisible, Runnable toggleOverlay) {
        super(theme, "Builder");
        this.controller = Objects.requireNonNull(controller);
        this.boundsEditor = Objects.requireNonNull(boundsEditor);
        this.overlayVisible = Objects.requireNonNull(overlayVisible);
        this.toggleOverlay = Objects.requireNonNull(toggleOverlay);
        onClosed(this::disposeTextures);
    }

    @Override protected void init() {
        super.init();
        refreshViewportHeight();
    }

    @Override public void initWidgets() {
        var state = controller.state();
        buildLayout(state);
    }

    private void buildLayout(BuilderUiState state) {
        if (composer != null) savedComposerDraft = composer.get();
        if (referencePathBox != null) savedReferencePath = referencePathBox.get();
        window.clear();
        summary = statusLabel = null;
        content = attachmentStrip = null;
        settingsPanel = null;
        conversationList = null; conversationPane = null; workspace = null;
        stageActions = referenceControls = attachmentRows = null;
        primaryAction = imageRow = null;
        composer = referencePathBox = null;

        WHorizontalList header = theme.horizontalList();
        settingsButton = header.add(theme.button(state.settingsExpanded() ? "Back to chat" : "Settings")).widget();
        settingsButton.action = controller::toggleSettings;
        overlayButton = header.add(theme.button(BuilderOverlayVisibility.buttonLabel(overlayVisible.getAsBoolean()))).widget();
        overlayButton.action = toggleOverlay;
        header.add(theme.button("Collapse")).widget().action = () -> {
            controller.toggleCollapsed(); client.setScreen(null);
        };
        BuilderViewport.ChatLayout chatLayout = BuilderViewport.chatLayout(Utils.getWindowWidth(), Utils.getWindowHeight(), theme.scale(1));
        boolean narrow = !chatLayout.sidebarVisible();
        paneLayoutNarrow = narrow;
        if (narrow) header.add(theme.button("Chats")).widget().action = () -> {
            forceConversationPane = !forceConversationPane; buildLayout(controller.state());
        };
        add(header).expandX();

        if (state.settingsExpanded()) {
            if (boundsEditorWidget == null) boundsEditorWidget = boundsEditor.get();
            add(boundsEditorWidget).expandX();
            settingsPanel = theme.verticalList();
            add(settingsPanel).expandX();
        } else {
            summary = add(wrappedLabel(summaryLine(state))).expandX().widget();
            statusLabel = add(wrappedLabel(statusLine(state))).expandX().widget();
            workspace = theme.horizontalList();
            content = theme.view();
            content.maxHeight = chatLayout.transcriptHeight();
            if (!narrow || forceConversationPane) {
                conversationPane = theme.view();
                conversationPane.maxHeight = content.maxHeight;
                conversationPane.minWidth = chatLayout.sidebarWidth();
                conversationList = theme.verticalList();
                conversationPane.add(conversationList).expandX();
                workspace.add(conversationPane);
            }
            workspace.add(content).expandX();
            add(workspace).expandX();

            imageRow = theme.horizontalList();
            rebuildImageRow();
            add(imageRow).expandX();
            referencePathBox = theme.textBox("", "Path to PNG reference…");
            ComposerInput.configure(referencePathBox);
            referencePathBox.set(savedReferencePath);
            referenceControls = theme.verticalList();
            add(referenceControls).expandX();

            attachmentStrip = theme.view();
            add(attachmentStrip).expandX();
            attachmentStrip.maxHeight = theme.scale(96);

            composer = theme.textBox("", "Describe the build or revision…");
            ComposerInput.configure(composer);
            composer.set(savedComposerDraft);
            add(composer).expandX();
            primaryAction = theme.horizontalList();
            add(primaryAction).expandX();
            stageActions = theme.verticalList();
            add(stageActions).expandX();
            renderReferencePath();
        }

        settingsLayout = state.settingsExpanded();
        attachmentHash = actionHash = contentHash = settingsContentHash = Integer.MIN_VALUE;
        refreshDynamic(state);
    }

    @Override public void tick() {
        super.tick();
        var state = controller.state();
        refreshViewportHeight();
        boolean narrowNow = !BuilderViewport.chatLayout(Utils.getWindowWidth(), Utils.getWindowHeight(), theme.scale(1)).sidebarVisible();
        if (!state.settingsExpanded() && paneLayoutNarrow != narrowNow) buildLayout(state);
        if (settingsLayout == null || settingsLayout != state.settingsExpanded()) buildLayout(state);
        refreshTextWidth();
        if (summary != null) summary.set(summaryLine(state));
        if (statusLabel != null) statusLabel.set(statusLine(state));
        settingsButton.set(state.settingsExpanded() ? "Back to chat" : "Settings");
        overlayButton.set(BuilderOverlayVisibility.buttonLabel(overlayVisible.getAsBoolean()));
        refreshDynamic(state);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (composer != null && ComposerInput.submitOnEnter(composer, keyCode, controller::submit)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void refreshDynamic(BuilderUiState state) {
        if (settingsPanel != null) {
            int nextSettingsHash = Objects.hash(state.phase(), state.status(), state.connection(), state.executable(),
                state.authorizationUrl(), state.userCode(), textWrapWidth);
            if (nextSettingsHash != settingsContentHash) {
                settingsPanel.clear();
                renderSettings(state);
                settingsContentHash = nextSettingsHash;
            }
            return;
        }

        int nextAttachmentsHash = Objects.hash(state.attachments().stream().map(a -> a.id()).toList(), state.thumbnails().keySet());
        if (nextAttachmentsHash != attachmentHash) {
            attachmentStrip.clear();
            attachmentRows = theme.verticalList();
            if (!state.attachments().isEmpty()) attachmentStrip.add(attachmentRows).expandX();
            for (int start = 0; start < state.attachments().size(); start += 2) {
                WHorizontalList row = theme.horizontalList();
                for (int i = start; i < Math.min(start + 2, state.attachments().size()); i++) {
                    var attachment = state.attachments().get(i);
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
                        row.add(theme.texture(Math.min(64, 48 * ratio), Math.min(48, 64 / ratio), 0, texture)).centerY();
                    } else row.add(theme.label("Preparing preview…"));
                    row.add(theme.label(attachment.width() + "×" + attachment.height()));
                    row.add(theme.button("Remove")).widget().action = () -> controller.removeAttachment(attachment.id());
                }
                attachmentRows.add(row).expandX();
            }
            Set<UUID> visibleIds = state.attachments().stream().map(a -> a.id()).collect(java.util.stream.Collectors.toSet());
            MapCleanup.removeWhere(textures, id -> !visibleIds.contains(id), this::disposeTexture);
            textureHashes.keySet().removeIf(id -> !visibleIds.contains(id));
            attachmentHash = nextAttachmentsHash;
        }

        int nextContentHash = Objects.hash(state.settingsExpanded(), state.authorizationUrl(), state.userCode(),
            state.connection(), state.cloudConsent(), state.phase(), state.status(), state.conversation(),
            state.draft(), state.accepted(), state.selectedStage(), state.selectedLayer(), state.conversations(),
            state.selectedConversation(), state.planHistory(), state.archiveReady(), state.archiveError(), textWrapWidth);
        if (nextContentHash != contentHash) {
            content.clear();
            renderChatContent(state);
            contentHash = nextContentHash;
        }

        int nextActionHash = Objects.hash(state.phase(), state.cloudConsent(), state.connection(), state.prepared(),
            state.thumbnails().keySet(), state.draft().map(BuildPlan::revision).orElse(-1L),
            state.accepted().map(BuildPlan::revision).orElse(-1L), state.selectedStage(), state.selectedLayer());
        if (nextActionHash != actionHash) {
            primaryAction.clear();
            BuilderUiPresentation.PrimaryAction action = primaryAction(state);
            switch (action) {
                case CONNECT -> primaryAction.add(theme.button("Connect / sign in")).expandX().widget().action = controller::connect;
                case SEND -> primaryAction.add(theme.button("Send")).expandX().widget().action = this::prepare;
                case CANCEL -> primaryAction.add(theme.button("Cancel request")).expandX().widget().action = controller::cancel;
            }
            stageActions.clear();
            if (state.draft().isPresent()) {
                long revision = state.draft().orElseThrow().revision();
                WHorizontalList draftActions = theme.horizontalList();
                draftActions.add(theme.button("Save plan")).widget().action = () -> controller.accept(revision);
                draftActions.add(theme.button("Discard")).widget().action = controller::reject;
                stageActions.add(draftActions).expandX();
            }
            if (state.accepted().isPresent() || state.draft().isPresent()) {
                WHorizontalList stage = theme.horizontalList();
                stage.add(theme.button("‹ Stage")).widget().action = () -> controller.selectStage(-1);
                stage.add(theme.button("Stage ›")).widget().action = () -> controller.selectStage(1);
                stage.add(theme.button("Layer −")).widget().action = () -> controller.selectLayer(-1);
                stage.add(theme.button("Layer +")).widget().action = () -> controller.selectLayer(1);
                stage.add(theme.button("All layers")).widget().action = controller::showAllLayers;
                stageActions.add(stage).expandX();
            }
            actionHash = nextActionHash;
        }
    }

    private BuilderUiPresentation.PrimaryAction primaryAction(BuilderUiState state) {
        return BuilderUiPresentation.primaryAction(state.phase(),
            state.connection().connection() == AgentClient.Connection.READY
                && state.connection().login() == AgentClient.Login.AUTHENTICATED,
            state.prepared().isPresent());
    }

    private void renderChatContent(BuilderUiState state) {
        if (conversationList != null) {
            conversationList.clear();
            conversationList.add(theme.button("+ New chat")).expandX().widget().action = controller::newConversation;
            for (var chat : state.conversations()) {
                String title = chat.title().length() > 24 ? chat.title().substring(0, 23) + "…" : chat.title();
                WButton row = conversationList.add(theme.button((chat.id().equals(state.selectedConversation()) ? "● " : "  ") + title))
                    .expandX().widget();
                row.tooltip = chat.title();
                row.action = () -> controller.selectConversation(chat.id());
            }
            if (!state.archiveError().isBlank()) conversationList.add(wrappedLabel(state.archiveError())).expandX();
        }
        if (!state.archiveError().isBlank()) content.add(wrappedLabel("Archive warning: " + state.archiveError())).expandX();
        if (state.conversation().isEmpty()) content.add(theme.label("Start a conversation about this selection.")).expandX();
        for (var message : state.conversation()) content.add(wrappedLabel(message.role() + ": " + message.text())).expandX();
        if (state.accepted().isPresent() || state.draft().isPresent()) {
            content.add(wrappedLabel(planLine(state))).expandX();
        }
        if (state.accepted().isPresent()) {
            content.add(wrappedLabel(progressLine(state))).expandX();
            content.add(wrappedLabel(stageLine(state))).expandX();
        }
        if (!state.cleanupWarnings().isEmpty() || state.nonAtomicSave()) content.add(wrappedLabel(saveLine(state))).expandX();
        addSignInDetails(content, state);
    }

    private void renderSettings(BuilderUiState state) {
        settingsPanel.add(wrappedLabel(statusLine(state))).expandX();
        settingsPanel.add(theme.button("Reopen saved project")).expandX().widget().action = controller::reopenProject;
        settingsPanel.add(wrappedLabel("Connection: " + state.connection().connection() + " · " + state.connection().login())).expandX();
        settingsPanel.add(wrappedLabel("Executable: " + state.executable() + " (edit in Builder module settings)")).expandX();
        settingsPanel.add(theme.button("Reconnect / sign in")).expandX().widget().action = controller::connect;
        addSignInDetails(settingsPanel, state);
    }

    private void addSignInDetails(WVerticalList target, BuilderUiState state) {
        BuilderUiPresentation.SignInActions available = BuilderUiPresentation.signInActions(state.authorizationUrl(), state.userCode());
        if (!available.openPage() && !available.copyCode()) return;
        if (available.openPage()) target.add(wrappedLabel("Sign-in URL: " + state.authorizationUrl())).expandX();
        if (available.copyCode()) target.add(wrappedLabel("Device code: " + state.userCode())).expandX();

        WHorizontalList actions = theme.horizontalList();
        if (available.openPage()) {
            URI authorizationUrl = state.authorizationUrl();
            actions.add(theme.button("Open sign-in page")).widget().action = () -> openSignInPage(authorizationUrl);
        }
        if (available.copyCode()) {
            String userCode = state.userCode();
            actions.add(theme.button("Copy code")).widget().action = () -> client.keyboard.setClipboard(userCode);
        }
        target.add(actions).expandX();
    }

    private void openSignInPage(URI authorizationUrl) {
        try {
            Util.getOperatingSystem().open(authorizationUrl);
        } catch (RuntimeException failure) {
            controller.showNotice("Could not open the sign-in page");
        }
    }

    private void renderReferencePath() {
        if (referenceControls == null) return;
        referenceControls.clear();
        rebuildImageRow();
        if (showReferencePath) {
            referenceControls.add(referencePathBox).expandX();
            referenceControls.add(theme.button("Add reference image")).expandX().widget().action = this::addReference;
        }
    }

    private void rebuildImageRow() {
        if (imageRow == null) return;
        imageRow.clear();
        imageRow.add(theme.button(showReferencePath ? "− Image" : "+ Image")).widget().action = () -> {
            showReferencePath = !showReferencePath;
            renderReferencePath();
        };
        imageRow.add(theme.button("Take screenshot")).widget().action = controller::captureScreenshot;
    }

    private void prepare() {
        controller.submit(composer.get());
        composer.setFocused(false);
    }

    private void addReference() {
        String path = referencePathBox.get().strip();
        if (path.isEmpty()) { controller.showNotice("Choose a PNG path before adding a reference"); return; }
        try { controller.addReference(Path.of(path)); }
        catch (RuntimeException invalid) { controller.showNotice("Reference path is invalid"); }
    }

    private BuilderWrappedLabel wrappedLabel(String text) {
        return BuilderTextLayout.label(theme, text, currentTextWrapWidth());
    }
    private double currentTextWrapWidth() { return BuilderTextLayout.wrapWidth(theme, Utils.getWindowWidth()); }
    private void refreshTextWidth() {
        double width = currentTextWrapWidth();
        if (Double.compare(width, textWrapWidth) == 0) return;
        textWrapWidth = width;
        if (summary != null) summary.setMaxWidth(width);
        if (statusLabel != null) statusLabel.setMaxWidth(width);
        if (content != null) content.maxHeight = BuilderViewport.chatLayout(Utils.getWindowWidth(), Utils.getWindowHeight(), theme.scale(1)).transcriptHeight();
        if (conversationPane != null && content != null) conversationPane.maxHeight = content.maxHeight;
        contentHash = Integer.MIN_VALUE;
        settingsContentHash = Integer.MIN_VALUE;
    }

    private void refreshViewportHeight() {
        double height = BuilderViewport.maxWindowViewHeight(Utils.getWindowHeight(), theme.scale(48));
        if (Double.compare(height, window.view.maxHeight) == 0) return;
        window.view.maxHeight = height;
        window.invalidate();
    }

    private static String statusLine(BuilderUiState state) {
        return BuilderUiPresentation.statusSummary(state.status(), state.phase() == BuilderUiState.Phase.FAILED);
    }
    private static String summaryLine(BuilderUiState state) {
        String blocks = state.snapshot().map(s -> s.bounds().volume() + " blocks").orElse("No selection");
        return blocks + "  ·  " + state.connection().connection() + " / " + state.connection().login();
    }
    private static String planLine(BuilderUiState state) {
        String draft = state.draft().map(p -> "Proposal r" + p.revision()).orElse("No proposal");
        String accepted = state.accepted().map(p -> "Accepted r" + p.revision()).orElse("No accepted plan");
        return draft + " · " + accepted;
    }
    private static String progressLine(BuilderUiState state) {
        var p = state.progress();
        return "World progress: matching " + p.matching() + ", place " + p.place() + ", replace " + p.replace() + ", unknown " + p.unknown();
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

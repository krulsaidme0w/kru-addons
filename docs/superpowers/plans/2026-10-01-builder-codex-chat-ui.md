# Builder Codex-Like Chat UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Present saved Builder conversations in a minimal Codex-like workspace with inline plan previews and readable persistent diagnostics.

**Architecture:** Keep UI widgets as projections of immutable `BuilderUiState`; route all state changes through `BuilderController`. Build a compact two-pane layout with a collapsible conversation list, scrollable transcript, pinned composer, inline plan artifacts, and settings outside the primary chat surface.

**Tech Stack:** Java 21, Meteor `WindowScreen`/GuiTheme widgets, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-codex-chat-ui-design.md`

## Global Constraints

- Depends on `docs/superpowers/plans/2026-10-01-builder-chat-archive-and-errors.md` and `docs/superpowers/plans/2026-10-01-builder-agent-world-reasoning.md`; execute after both provide stable UI-state/archive/preview interfaces.
- Keep all transcript entries and essential error causes/remedies visible; do not add an “Open Details” gate.
- Never place blocks or imply the preview is a live screenshot; plan activation only controls the read-only overlay.
- Preserve explicit image/screenshot consent and do not archive images/auth data.
- Respect current Meteor scaling, scrolling, and small viewport constraints; do not stage unrelated pre-existing worktree changes.

---

## File Structure and Interfaces

- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java` to render sidebar, header/status, transcript, plan cards, and pinned composer.
- Modify `ui/BuilderUiState.java` only if the archive/diagnostic and preview projects did not already add the required immutable projections.
- Modify `ui/BuilderController.java` only by calling the archive interfaces from plan 1; no widget may write archive files directly.
- Extend `ui/BuilderViewport.java` with pure layout decisions `(windowWidth, windowHeight, scale) → sidebarVisible/sidebarWidth/transcriptHeight/composerHeight`.
- Extend/add `ui/BuilderTextLayout.java` and `BuilderWrappedLabel.java` for long diagnostics and plan explanations.
- Add `ui/ConversationListModel.java` only if sorting/selection projection is too complex for `BuilderUiState`; keep it pure and Minecraft-independent.
- Tests: `BuilderViewportTest`, `BuilderTextLayoutTest`, `BuilderUiPresentationTest`, and UI widget contract tests under `builder-addon/src/test/java/dev/krul/builder/ui`.

## Tasks

### Task 1: Pure Codex-like layout model

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderViewport.java`.
- Test: `builder-addon/src/test/java/dev/krul/builder/ui/BuilderViewportTest.java`.

**Interfaces:**
- Consumes: viewport width/height and Meteor scale.
- Produces: stable sidebar visibility/width, transcript viewport height, and composer minimum height.

- [x] Add tests for 854×480, 1191×628, and 1920×1080 at scale 1 and 2; verify transcript/composer minimum sizes and narrow sidebar fallback.
- [x] Run focused viewport tests.
- [x] Implement pure layout calculations with bounded sidebar width and narrow-window fallback.
- [x] Run viewport tests and `git diff --check`.
- [ ] Commit only viewport model/test paths.

### Task 2: Conversation sidebar and selection actions

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java` and `BuilderController.java` only via the APIs from the archive plan.
- Tests: new `BuilderScreenConversationContractTest.java` and `BuilderControllerTest.java`.

**Interfaces:**
- Consumes: `BuilderUiState.conversations()`, `selectedConversationId()`, `createConversation()`, and `selectConversation(UUID)` from plan 1.
- Produces: New chat action and selected/recent-sorted conversation rows.

- [ ] Add tests that empty state exposes one New chat action, each row shows title/recent update, selecting a row invokes its exact UUID, and creating a chat does not overwrite the prior chat.
- [ ] Run focused tests before building widgets.
- [x] Add compact left sidebar with New chat and a scrollable conversation list; mark the selected item with a distinct symbol and title.
- [x] On narrow viewports, collapse the sidebar behind a navigation toggle; keep conversation list separate from settings.
- [ ] Add an overflow/delete action only if it can be kept out of the primary chat flow; require one clear confirmation before calling `ConversationArchiveStore.delete(UUID)`.
- [x] Ensure opening/selecting a chat does not trigger a world write, screenshot send, or automatic plan activation.
- [ ] Run sidebar/controller tests and manually resize the game window (logic tests pass; in-game resize check not run).
- [ ] Commit only screen/controller contract tests.

### Task 3: Transcript, progress, and pinned composer

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`, `BuilderUiState.java` only if needed, and `ComposerInput.java` only for composer focus/keyboard behavior.
- Tests: `BuilderTextLayoutTest.java`, new `BuilderScreenTranscriptContractTest.java`.

**Interfaces:**
- Consumes: immutable ordered archive entries and one current in-flight progress item.
- Produces: chronological scrollable transcript and composer/send action that remains visible at every supported viewport size.

- [x] Test multiline archive text and existing Enter/send behavior; progress remains transient instead of permanent spam.
- [ ] Run focused UI model tests before implementation.
- [ ] Replace remaining stacked controls with message cards and pin composer independently of transcript scrolling (composer is below transcript, but Meteor window scrolling is shared).
- [ ] Keep attachment/screenshot controls optional and visually secondary; preserve current explicit consent and “Take screenshot” behavior.
- [ ] Maintain scroll-to-latest only when the user is already near the bottom; do not jump when reviewing older messages.
- [ ] Run transcript/layout tests and manually inspect a long conversation.
- [ ] Commit only screen/composer/test paths.

### Task 4: Inline plan artifact and version history

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java` and UI presentation model; consume archive and preview data without duplicating it.
- Tests: `BuilderUiPresentationTest.java` and new plan-artifact contract tests.

**Interfaces:**
- Consumes: `ConversationPlan` including source bounds, `PlanPreviewModel.ContactSheet`, stage/cell counts, validation diagnostics, and `visualReviewed` metadata from plans 1 and 2.
- Produces: inline plan artifact with preview image, plan summary, truthfully labeled review state, and one save/use-overlay action.

- [ ] Add complete plan-artifact state/presentation tests (archive model coverage exists; no widget screenshot test yet).
- [ ] Assert visual-review failure shows its cause and cannot be labeled “reviewed”; assert stale-context plans never show as ready-to-use.
- [ ] Render one compact contact-sheet thumbnail, the selected revision summary, stage/cell counts, and validation status inline in the transcript.
- [ ] Provide only the existing safe plan save/use-overlay action; do not add place/build-world controls or a second confirmation dialog. Regenerate historical contact sheets locally from stored plan/source bounds when possible; mark them unavailable if required block models are missing.
- [ ] Keep previous revisions reachable from the conversation rather than replacing the prior plan message.
- [ ] Run presentation/artifact tests and verify texture cleanup on chat switch/screen close.
- [ ] Commit exact UI and test paths.

### Task 5: Persistent inline diagnostics and settings separation

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`, `BuilderUiPresentation.java`, and only the required widget helpers.
- Tests: `BuilderUiPresentationTest.java`, `BuilderTextLayoutTest.java`, and error-widget contract tests.

**Interfaces:**
- Consumes: `BuilderDiagnostic` from the archive plan plus small connection readiness state.
- Produces: visible failure card containing operation, cause, sanitized detail, and next action; compact connection indicator.

- [ ] Test storage, model/tool, transport, invalid-proposal, scan, and cancellation diagnostics at narrow and wide wrap widths.
- [ ] Run focused tests and confirm no diagnostic relies on a transient status field or hidden details button.
- [ ] Render each failure inline with a clear label, full safe detail, and next step; allow scrolling for long content while preserving the composer.
- [ ] Keep repeated connection status compact; move bounds, executable, overlay, and privacy configuration into Settings without removing existing controls.
- [ ] Verify color is not the only distinction between error, progress, stale plan, and success states.
- [ ] Run UI presentation/text tests and manually inspect long errors.
- [ ] Commit exact screen/presentation/helper/test paths.

### Task 6: End-to-end UI verification

**Files:**
- Tests only unless a targeted UI fix is required.

**Interfaces:**
- Consumes: complete archive, agent preview, diagnostics, and UI integrations.
- Produces: verified Codex-like conversation flow at desktop and small game-window sizes.

- [ ] Run `./gradlew -p builder-addon test` and `./gradlew -p builder-addon remapJar`; record test summary and archive path.
- [ ] Manually inspect 854×480, 1191×628, and 1920×1080 with two saved chats, a long error, a long agent answer, and a plan contact sheet.
- [ ] Confirm UI state survives screen close/reopen; conversation selection persists; switching world does not clear history; stale plan is visibly gated pending scan.
- [ ] Confirm composer stays usable and all error causes remain visible without “Open Details”.
- [ ] Confirm no screenshot or auth/device code is added to archive files and no world block changes occur.
- [ ] Review `git status --short` and `git diff --check`; stage/commit only task-scoped files.

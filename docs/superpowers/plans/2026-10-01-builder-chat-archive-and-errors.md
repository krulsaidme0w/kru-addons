# Builder Chat Archive and Errors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist independent Builder conversations and plan revisions locally, and preserve actionable sanitized diagnostics in each conversation.

**Architecture:** Add immutable archive records and a bounded, atomic JSON store under the existing Builder data root. Make the controller select a conversation/project session and serialize every durable transition before dispatch or reporting success. Keep transcript persistence separate from world scan validity; reopening never bypasses fresh plan validation.

**Tech Stack:** Java 21, Gson, Fabric/Meteor client module, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-chat-archive-and-errors-design.md`

## Global Constraints

- Store only local text, plans, and sanitized diagnostics; never archive screenshots, auth/device codes, tokens, environment values, or raw stderr.
- Never send world mutation requests or treat an archived plan as valid for a new world without a fresh snapshot.
- Limit archive JSON to 16 MiB per conversation and reject symbolic links; use atomic replace with a recoverable fallback.
- Keep I/O off the Minecraft client thread and serialize writes for each conversation.
- Do not stage unrelated pre-existing worktree changes; use exact paths for every Git operation.

---

## File Structure and Interfaces

- Create `builder-addon/src/main/java/dev/krul/builder/archive/ConversationArchive.java`: immutable aggregate with UUID, title, created/updated epoch millis, ordered messages, and plan revisions.
- Create `ConversationEntry.java`: immutable `(UUID id, Kind kind, long createdAtEpochMillis, String text, Optional<BuilderDiagnostic> diagnostic)`; kinds are `USER`, `AGENT`, `FAILURE`, `SYSTEM`.
- Create `ConversationPlan.java`: immutable `(BuildPlan plan, Lifecycle lifecycle, UUID contextSession, long contextRevision, Optional<Bounds> sourceBounds, List<String> diagnostics, boolean visualReviewed, String visualReviewFailure)`; lifecycle is `PROPOSED`, `REVISED`, `REJECTED`, `SAVED`. Missing legacy bounds use `Optional.empty()` and require a fresh scan before preview or use.
- Create `BuilderDiagnostic.java`: immutable `(Category category, String operation, String cause, String detail, String nextAction, long occurredAtEpochMillis, int occurrences)` with categories `SCAN_CONTEXT`, `AUTH_TRANSPORT`, `MODEL_TOOL`, `INVALID_PROPOSAL`, `LOCAL_STORAGE`, `CANCELLED`.
- Create `ConversationSummary.java`: immutable list-row projection `(UUID id, String title, long updatedAtEpochMillis, int messageCount, boolean hasPlans)`.
- Create `ConversationArchiveCodec.java`: `String encode(ConversationArchive)` and `ConversationArchive decode(String)`; reject unknown schema versions, invalid required fields, and oversized records.
- Create `ConversationArchiveStore.java`: `ListResult list() throws IOException`, `Optional<ConversationArchive> load(UUID id) throws IOException`, `SaveResult save(ConversationArchive archive) throws IOException`, `void delete(UUID id) throws IOException`, `Optional<UUID> selected() throws IOException`, and `void select(UUID id) throws IOException`. `ListResult` contains valid summaries plus per-file `LoadIssue`s so one corrupt archive does not hide the others. `SaveResult` contains atomic-fallback and cleanup-warning fields.
- Modify `BuilderController.java` to own selected archive state and serialize immutable archive snapshots on `ioExecutor`; each durable controller transition calls one internal `persistArchive(...)` boundary.
- Modify `BuilderModule.java` to construct the archive store under `runDirectory/kru-builder/conversations`, select/create a conversation, and construct `ProjectSession` using the selected conversation UUID.
- Extend `BuilderUiState.java` with immutable conversation summaries, selected ID, archive load/save error, and the currently selected conversation’s structured entries/plans.
- Keep `ProjectStore` as a migration reader for the legacy single accepted plan; do not repurpose its existing file format.

## Tasks

### Task 1: Archive domain and codec

**Files:**
- Create the four archive domain/codec files listed above.
- Test: `builder-addon/src/test/java/dev/krul/builder/archive/ConversationArchiveCodecTest.java`

**Interfaces:**
- Consumes: `BuildPlan` and existing `ProjectCodec` conventions.
- Produces: immutable records and a deterministic versioned JSON codec.

- [x] Add tests for an empty conversation, ordered user/agent/failure entries, a plan revision with validation diagnostics/source bounds, and a saved plan round-trip.
- [x] Run `./gradlew -p builder-addon test --tests dev.krul.builder.archive.ConversationArchiveCodecTest`; verify the new types/tests fail to compile before implementation.
- [x] Implement the six archive model/codec files with defensive copies, null/range checks, bounded strings, and explicit lifecycle/category enums.
- [x] Implement deterministic schema-versioned JSON encode/decode; keep full `BuildPlan` encoding in a focused private helper and reject unrecognized schema versions.
- [x] Run the focused test and `git diff --check`.
- [x] Commit only the six archive model/codec files and codec test with `git add` on their exact paths.

### Task 2: Safe local archive store

**Files:**
- Create `ConversationArchiveStore.java` and `ConversationSummary.java` if not created in Task 1.
- Test: `builder-addon/src/test/java/dev/krul/builder/archive/ConversationArchiveStoreTest.java`

**Interfaces:**
- Consumes: `ConversationArchiveCodec`.
- Produces: bounded load/list/save/delete/select operations rooted only at the constructor-provided directory.

- [x] Test create/list/load, selected ID persistence, update timestamp ordering, explicit delete, missing-file behavior, malformed JSON isolation, over-16-MiB rejection, and symlink rejection.
- [x] Test atomic-move fallback with injected move/delete operations, including restore from a backup after failed replacement.
- [x] Run the focused store test and verify the failures correspond to missing store behavior.
- [x] Implement per-conversation files named by validated UUID plus one separately versioned selected-conversation index file; discover archives by validated direct-child UUID filenames and never resolve a caller-supplied path fragment.
- [x] Serialize one aggregate to a forced temporary file, atomically replace where possible, and return cleanup/fallback warnings without masking the primary failure.
- [x] Ensure one corrupt conversation does not prevent listing/loading other valid conversation records; expose its ID and failure to the caller.
- [x] Run focused codec/store tests and `git diff --check`.
- [x] Commit only the store, summary, and tests.

### Task 3: Structured diagnostics and failure mapping

**Files:**
- Create/modify `BuilderDiagnostic.java`, `AgentEvent.java`, and `BuilderUiPresentation.java`.
- Test: `builder-addon/src/test/java/dev/krul/builder/archive/BuilderDiagnosticTest.java` and focused controller tests.

**Interfaces:**
- Consumes: current exception strings and `AgentEvent.Failed`.
- Produces: deterministic mapping from operation and exception to `BuilderDiagnostic`, including a credential-safe user-facing line.

- [x] Add tests for scan failure, Codex transport/model failure, invalid proposal coordinates, storage failure, cancellation, and credential/token redaction.
- [x] Run focused tests and confirm failures before adding the mapping.
- [x] Add stable diagnostic categories and operation labels; retain the sanitized underlying cause and a specific next action rather than a generic “Request failed”.
- [x] Keep existing provider-response sanitization and redact URLs/device codes/token-shaped values before the diagnostic enters the archive.
- [ ] Make repeated identical diagnostics coalesce only when operation/category/cause match; increment the occurrence count without deleting the first timestamp.
- [x] Run diagnostic/controller tests and verify no raw authorization values can appear in `toString()` or encoded JSON.
- [ ] Commit exact diagnostic/event/presentation/test paths.

### Task 4: Controller-owned conversations and autosave ordering

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/ui/BuilderController.java` and `BuilderUiState.java`.
- Modify `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`.
- Test: `builder-addon/src/test/java/dev/krul/builder/ui/BuilderControllerTest.java` and `builder-addon/src/test/java/dev/krul/builder/minecraft/BuilderModuleConversationTest.java`.

**Interfaces:**
- Consumes: `ConversationArchiveStore`, archive records, diagnostics, current snapshot/session suppliers.
- Produces: `createConversation()`, `selectConversation(UUID id)`, `state()` with archive list/current record, and ordered asynchronous persistence.

- [x] Add controller tests proving user input is persisted before `AgentClient.start`, agent completion follows the request in order, and the correct conversation ID is used for each turn.
- [x] Add tests proving selection changes cancel/invalidate callbacks from the prior generation but do not erase its transcript; world/selection changes preserve the selected archive.
- [x] Add tests for proposal/revision lifecycle, rejection retention, save only after fresh snapshot validation, and visible unsaved state after a store failure.
- [x] Run focused controller tests before implementation.
- [x] Replace the fixed Builder project UUID in conversation-session creation with the selected conversation UUID; retain one active immutable archive snapshot in the controller.
- [x] Serialize archive writes through a controller-owned future chain on `ioExecutor`; capture immutable snapshots on the client thread and return success/failure on `clientExecutor` only if the conversation generation still matches.
- [x] Append user text before dispatch; append assistant text, proposal outcome, and structured failure after the corresponding agent event.
- [x] Keep accepted-plan activation gated by the existing fresh-snapshot `ProjectSession.accept` path; append `SAVED` only after that succeeds and archive save confirms success.
- [x] Make create/select update the persisted selected UUID; prevent late reads/writes from switching the currently selected conversation.
- [ ] Run `./gradlew -p builder-addon test --tests dev.krul.builder.ui.BuilderControllerTest --tests dev.krul.builder.minecraft.BuilderModuleConversationTest` (module-level test not yet added; full Builder suite passes).
- [ ] Commit only controller/module/UI-state changes and their tests.

### Task 5: Legacy project migration and archive recovery

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java` and archive store only if needed.
- Test: `builder-addon/src/test/java/dev/krul/builder/archive/LegacyProjectMigrationTest.java`.

**Interfaces:**
- Consumes: legacy `ProjectStore.load(UUID)` and new archive store.
- Produces: one idempotent import of the prior accepted plan into a migration conversation, marked untrusted until current-world validation.

- [x] Test absent legacy file, valid file, repeated startup, corrupt legacy file, and a plan whose old bounds no longer match.
- [x] Run the migration test and verify the old plan does not activate on import.
- [x] Import the legacy fixed project UUID as the migration conversation UUID so the stored `BuildPlan.project()` remains unchanged; archive presence is the idempotency marker, and the legacy file is preserved.
- [x] Reopen legacy plan only through `ProjectSession.restoreAccepted` after a fresh scan; retain diagnostics if the plan is stale or invalid.
- [x] Run archive tests; module behavior is covered by controller/storage integration rather than a dedicated module fixture.
- [ ] Commit migration code/tests with exact paths.

### Task 6: Archive regression verification

**Files:**
- Tests only unless a narrowly scoped fix is required.

**Interfaces:**
- Consumes: complete archive/controller/migration implementation.
- Produces: verified local persistence and error-reporting behavior.

- [x] Run `./gradlew -p builder-addon test` and inspect the complete summary.
- [x] Run `./gradlew -p builder-addon remapJar` and verify the packaged archive exists.
- [ ] Review `git status --short` and `git diff --check`; confirm no unrelated pre-existing files were staged or modified by this task.
- [ ] Perform an in-game check: create two conversations, send a request, close/reopen the screen, switch selection/world, and confirm both histories persist while an old plan is marked stale (not run in this environment).
- [ ] Record exact commands/results and commit only intended remaining archive files.

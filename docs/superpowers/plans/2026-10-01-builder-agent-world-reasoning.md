# Builder Agent World Reasoning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Builder’s agent to reason from exact world bounds and local blocks, inspect its own plan visually once, and return a coherent, validated plan without changing the world.

**Architecture:** Extend the current immutable `AgentRequest`/snapshot and read-only dynamic-tool boundary. Add a bounded local plan-inspection tool that validates and renders a contact sheet from exact Minecraft states, then let Codex perform at most one critique/revision pass before the existing final schema decode. Persist the final visual-review outcome through the conversation archive.

**Tech Stack:** Java 21, Gson, Minecraft/Yarn client rendering, Fabric/Meteor, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-agent-world-reasoning-design.md`

## Global Constraints

- Depends on `docs/superpowers/plans/2026-10-01-builder-chat-archive-and-errors.md`; implement after its archive IDs/plan-history/diagnostic interfaces exist.
- Tools are read-only, bounded to the active request generation and selected bounds, and cannot move the player/camera or mutate blocks.
- Preserve exact block registry IDs, properties, unknown-cell semantics, and current block-entity/NBT rejection rules.
- Critique/revision count is at most one; tool calls and preview bytes have hard caps.
- Never claim visual review succeeded when rendering failed; return `visualReviewed=false` plus the failure reason.
- Do not stage unrelated pre-existing worktree changes; use exact paths for every Git operation.

---

## File Structure and Interfaces

- Modify `agent/CodexProcess.java` context instructions and active dynamic-tool routing.
- Modify `agent/BuilderDynamicTools.java` definitions/dispatch and add `inspect_build_plan` with strict bounded JSON arguments.
- Create `blueprint/PlanPreviewModel.java`: `Input(BuildPlan plan, Bounds bounds, WorldSnapshot snapshot, Optional<String> stageId)`, pure deterministic `Projection project(Input)`, and immutable `ContactSheet(byte[] pngBytes, int width, int height, Map<String, StateKey> palette)` with defensive byte/map copies.
- Create `minecraft/PlanPreviewRenderer.java`: client-render-thread-only renderer that resolves exact `StateKey`s and produces one compact isometric/top/side contact sheet without touching world/camera state.
- Create `agent/PlanInspectionTool.java`: `JsonObject inspect(JsonObject arguments, AgentRequest request, BooleanSupplier stillCurrent)`; validate with the same `PlanValidator`/`MinecraftPlanRules`, then call `PlanPreviewRenderer.render(PlanPreviewModel.Input, stillCurrent)` and produce structured diagnostics or image results.
- Change the proposal event to `AgentEvent.Proposal(BuildPlan plan, boolean visualReviewed, String reviewFailure)` and copy those exact review fields to the archived `ConversationPlan`.
- Modify `AgentRequest` only if a separate iteration budget/configuration is necessary; default maximum review revisions to one, not caller-controlled arbitrary values.
- Tests: pure projection/validation/tool/protocol tests under `builder-addon/src/test/java/dev/krul/builder/{blueprint,agent,minecraft}`.

## Tasks

### Task 1: World-context and design-quality rubric

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java`.
- Test: `builder-addon/src/test/java/dev/krul/builder/agent/WorldSnapshotGeometryTest.java` and `CodexProcessTest.java`.

**Interfaces:**
- Consumes: immutable `AgentRequest.context()`, accepted plan, and local catalog tool names.
- Produces: bounded context that clearly distinguishes known, unknown, and uncaptured cells and gives compact design guidance.

- [x] Add tests asserting context includes exact min/max, relative-coordinate formula, unknown vs uncaptured rules, and no claim that the entire world was scanned.
- [x] Add prompt-contract tests for coherent style, silhouette/proportions, usable circulation, supports, limited palette roles, exact registry lookup, and one visual critique pass.
- [x] Run the focused tests to capture current missing guidance.
- [x] Update `contextMessage` with concise rubric and explicit block lookup workflow; avoid embedding the full registry or lengthy style catalog.
- [x] Keep block-entity/NBT constraints and “do not invent unknowns” language intact.
- [x] Run focused geometry and Codex prompt tests.
- [ ] Commit only context guidance and tests.

### Task 2: Pure plan preview projection and caps

**Files:**
- Create `builder-addon/src/main/java/dev/krul/builder/blueprint/PlanPreviewModel.java`.
- Test: `builder-addon/src/test/java/dev/krul/builder/blueprint/PlanPreviewModelTest.java`.

**Interfaces:**
- Consumes: `BuildPlan`, `Bounds`, `WorldSnapshot`, chosen contact-sheet dimensions.
- Produces: deterministic projection layers for isometric, top, and side views plus exact palette-to-state legend and difference markers.

- [x] Test ordered stage projection, relative/absolute coordinates, later-stage replacement, off-bounds rejection, and target/bounds caps.
- [ ] Add further shape fixtures (tower, overhang, removals, unknown surroundings, empty plan) and exact property legend mapping.
- [x] Run focused tests before implementation.
- [x] Implement pure coordinate projection without Minecraft rendering imports; cap bounds and targets with `PlanValidator.MAX_TARGETS`.
- [x] Represent exact target state IDs/properties in projection; absent cells are not converted to air.
- [x] Run projection tests and `git diff --check`.
- [ ] Commit only model and tests.

### Task 3: Client-side contact-sheet rendering

**Files:**
- Create `builder-addon/src/main/java/dev/krul/builder/minecraft/PlanPreviewRenderer.java`.
- Reuse `BlockPreviewRenderer.java` rendering-state restoration patterns; do not modify the world framebuffer.
- Test: `builder-addon/src/test/java/dev/krul/builder/minecraft/PlanPreviewRendererTest.java` plus renderer contract tests.

**Interfaces:**
- Consumes: pure `PlanPreviewModel`, exact `BlockState`/baked models, render-thread scheduler.
- Produces: `CompletableFuture<PlanPreviewModel.ContactSheet>` with a single bounded PNG and width/height. The public method is `render(PlanPreviewModel.Input input, BooleanSupplier stillCurrent)`; it completes exceptionally with a sanitized render failure and restores framebuffer/render state in all paths.

- [ ] Add fake-renderer contract tests for render-thread dispatch, maximum image dimensions/bytes, missing model, invalid state, cancellation, and cleanup on thrown render errors.
- [ ] Run renderer contract tests before implementation.
- [x] Implement bounded isometric schematic rasterization using inventory-model appearance cues only; renderer reads no world/camera and reuses the private framebuffer block preview path.
- [x] Resolve/cache block models only for the active inspection call; do not retain world/chunk objects or unbounded per-plan images.
- [x] Enforce one PNG response and explicit byte/dimension caps; fail with a structured reason if exceeded.
- [ ] Confirm a render failure leaves the regular game framebuffer and camera unchanged in a manual client test.
- [x] Run raster tests and existing `BlockPreviewRendererTest`; live GL path remains untested.
- [ ] Commit renderer and contract tests.

### Task 4: Read-only plan-inspection dynamic tool

**Files:**
- Create `builder-addon/src/main/java/dev/krul/builder/agent/PlanInspectionTool.java`.
- Modify `agent/BuilderDynamicTools.java` and `minecraft/ClientBlockCatalogTools.java` or add a dedicated client-thread plan tool adapter.
- Tests: `BuilderDynamicToolsTest.java`, new `PlanInspectionToolTest.java`.

**Interfaces:**
- Consumes: active request UUID/session/context revision, strict plan JSON, `PlanValidator`, `PlanPreviewRenderer`.
- Produces: `inspect_build_plan` result with exact validation text and, only on success, a preview image and review metadata.

- [ ] Test full client-side malformed/oversized plan, dependency, registry and successful image integration cases (Minecraft registry/render fixture unavailable).
- [x] Test that invalid inputs are rejected by strict tool argument validation; proposal decoding and preview generation both fail closed.
- [x] Run focused tool tests and verify invalid arguments are explicit.
- [x] Define strict `inspect_build_plan` schema and reject oversized plan JSON before decode/allocation.
- [x] Route rendering to the Minecraft client thread and bind it to the active request/call generation.
- [x] Return a plan summary plus one bounded PNG `inputImage`; aggregate tool response is capped.
- [x] Ensure cancellation invalidates pending render callbacks and blocks stale result return.
- [x] Run dynamic-tool, review-budget, projection and raster tests; live renderer integration remains unverified.
- [ ] Commit exact tool/adapter/test paths.

### Task 5: One-pass Codex critique/revision protocol

**Files:**
- Modify `builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java`, `AgentEvent.java`, `PlanResponseCodec.java`, and archive plan metadata introduced by the archive plan.
- Tests: `CodexProcessTest.java`, `PlanResponseCodecTest.java`, `BuilderControllerTest.java`.

**Interfaces:**
- Consumes: validated `inspect_build_plan` tool result and one hard-coded review budget.
- Produces: final plan plus `visualReviewed` and optional review failure; never more than one critique/revision cycle.

- [ ] Extend live protocol fixtures through draft → inspect image → revised draft → final output (not currently simulated end-to-end).
- [x] Add pure review-budget cases for no review, mismatched final plan, failed preview, one higher revision, and third-call rejection.
- [x] Run focused tracker/controller tests before implementation.
- [x] Update context rubric to require `inspect_build_plan` and at most one critique-driven revision.
- [x] Track at most two inspect calls; second successful inspection must have higher revision; reject a third call.
- [x] Emit final proposal and review metadata together and verify exact inspected plan equality.
- [x] Preserve final `PlanResponseCodec` validation and fresh acceptance checks.
- [x] Run focused tracker/codec/controller tests and full Builder suite.
- [ ] Commit protocol changes and tests.

### Task 6: Representative plan-quality and runtime verification

**Files:**
- Add fixtures under `builder-addon/src/test/resources/` only if current unit fixtures cannot express representative plans.
- Update Builder README with the read-only tool boundary and preview limitations.

**Interfaces:**
- Consumes: complete plan inspection and review loop.
- Produces: test evidence that several distinct build shapes can be proposed and truthfully inspected.

- [ ] Add full proposal fixtures for pavilion, tower, modded palette, occupied revision, out-of-bounds and unsupported block-entity plans.
- [x] Verify the pure review tracker only reports success for the exact final inspected plan; invalid input and render errors fail closed.
- [x] Run full `builder-addon` tests and `remapJar`.
- [ ] In-game verify rendered schematic and unchanged player/camera/world state (requires client smoke test).
- [ ] Record any render limitation in the archive/UI integration contract; do not silently downgrade visual review.
- [ ] Review `git status --short` and commit only scoped files.

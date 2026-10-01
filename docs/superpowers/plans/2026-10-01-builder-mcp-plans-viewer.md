# Builder MCP Plans and Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Codex create precise staged block replacements, validate them against a fresh world snapshot, and show the result in Builder and a local 3D viewer with plan history.

**Architecture:** Define a versioned, coordinate-relative plan contract with exact palette states and preconditions. The MCP server validates through the Minecraft bridge, archives each proposal locally, and sends only valid plans to the existing Builder overlay. A local viewer renders bounded world/plan data and historical plan revisions without copying Codex chat or mutating the world.

**Tech Stack:** Java 21, Gson, existing `BuildPlan`/`PlanResponseCodec`/`ProjectSession`/`BlueprintRenderer`, TypeScript + Three.js bundled into static assets served locally by the MCP companion.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-local-mcp-world-design.md`

## Global Constraints

- Only relative coordinates anchored at selection minimum; resolve and validate against exact bounds/revision.
- Cap plans at 32,768 final cell assignments (`PlanValidator.MAX_TARGETS`) and 16 MiB encoded JSON (`PlanResponseCodec.MAX_RESPONSE_BYTES`).
- `PLACE` requires known air; `REPLACE` and `REMOVE` require exact expected current state.
- Unknown cells, unsupported block entities/NBT, conflicts, invalid states and stale snapshots fail closed.
- Plan publishing changes preview state only; it never edits world blocks or moves the player.
- Viewer and archive bind to loopback and never mirror private Codex transcript storage.

## Review Focus

- A world changing after plan inspection must invalidate publication — test revision mismatch.
- A replace operation with wrong expected state must be rejected at the exact cell — test state mismatch diagnostics.
- Stage order and overlap must compose deterministically — test add/replace/remove across dependent stages.
- Container/sign/custom block-entity data must not be silently destroyed — test all flagged block-entity cells fail closed.
- Archived plans must not reappear in another world/selection — test identity mismatch and explicit preview-only restore.

---

### Task 1: Define strict plan operation model and codec

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/project/PlanOperation.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/project/PlanContract.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/project/PlanContractCodec.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/project/PlanContractCodecTest.java`

**Interfaces:**
- Produces: versioned plan with `palette`, ordered `stages`, and operations `PLACE`, `REPLACE`, `REMOVE` using relative `Cell`, exact `StateKey`, `expected`, and `target` fields.
- Produces: strict bounded decoder rejecting unknown fields, duplicate/conflicting operations, oversized payloads, and malformed states.

- [ ] Write round-trip and rejection tests for each operation kind, required preconditions, duplicate cells, unknown keys, and payload limits.
- [ ] Run focused codec tests and confirm failure.
- [ ] Implement strict codec with limits taken from `PlanValidator` and preserve existing project archive compatibility.
- [ ] Rerun tests and confirm pass.
- [ ] Commit as `feat(builder): define strict staged plan operations`.

### Task 2: Validate against live snapshot and publish preview

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/MinecraftPlanRules.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/project/PlanPreconditionValidator.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`
- Modify: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BuilderMcpServer.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/project/PlanPreconditionValidatorTest.java`
- Test: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/PlanToolsTest.java`

**Interfaces:**
- Consumes: Task 1 plan contract and bridge/catalog protocol.
- Produces: `inspect_plan(plan, snapshotRevision) -> validation + projected final assignments`; `publish_plan(plan, validationToken)` succeeds only for exact current plan/revision and updates existing hologram preview.
- Produces: plan status/report with matched, place, replace, remove, and unknown counts.

- [ ] Test known-air PLACE, exact REPLACE/REMOVE preconditions, existing or block-entity-capable inventory/sign blocks, out-of-bounds, overlap, invalid block state, and stale revision.
- [ ] Run focused tests and confirm failure.
- [ ] Implement fail-closed validator and bind validation token to plan hash + world generation + snapshot revision.
- [ ] Connect publication to the existing overlay; assert no method in the tool path calls world mutation or movement APIs.
- [ ] Run addon and MCP test suites and inspect a valid/invalid plan through MCP Inspector.
- [ ] Commit as `feat(builder): validate and preview MCP plans`.

### Task 3: Archive plan revisions independently from chat

**Files:**
- Create: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/archive/PlanArchive.java`
- Create: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/archive/PlanArchiveStore.java`
- Test: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/archive/PlanArchiveStoreTest.java`

**Interfaces:**
- Produces: bounded versioned plan records under the companion's app-owned data directory (`~/.kru-builder/plan-archive`), with optional user label, creation time, world/dimension fingerprint, bounds, snapshot reference, validation report, and preview paths.
- Produces: list/load/delete-by-ID operations; load returns archived plan for inspection only and cannot publish without fresh validation.

- [ ] Test atomic persistence, corrupt record isolation, size/path/symlink limits, world identity mismatch, and preview-only restore.
- [ ] Run focused store tests and confirm failure.
- [ ] Implement plan-only archive under the companion's app-owned data directory; keep it separate from Codex profile/config and from the Minecraft instance path.
- [ ] Rerun tests and confirm pass.
- [ ] Commit as `feat(builder): persist versioned plan history`.

### Task 4: Build the local interactive plan viewer

**Files:**
- Create: `builder-mcp-server/viewer/package.json`
- Create: `builder-mcp-server/viewer/src/main.ts`
- Create: `builder-mcp-server/viewer/src/voxel-scene.ts`
- Create: `builder-mcp-server/viewer/src/plan-timeline.ts`
- Create: `builder-mcp-server/viewer/index.html`
- Modify: `builder-mcp-server/build.gradle`
- Test: `builder-mcp-server/viewer/test/voxel-scene.test.ts`
- Test: `builder-mcp-server/viewer/test/plan-timeline.test.ts`

**Interfaces:**
- Produces: loopback-served viewer with rotatable bounded voxel scene, current block snapshot, distinct PLACE/REPLACE/REMOVE overlay, stage/layer filter, exact IDs on hover, warnings, and plan revision timeline.
- Produces: scene fallback metadata; it must identify approximate/icon rendering when full modded model geometry is unavailable. Model thumbnails come from the game-rendered MCP catalog.

- [ ] Write tests for coordinate transforms, stage filters, plan diffs, unknown-cell rendering, and history-world mismatch.
- [ ] Run `cd builder-mcp-server/viewer && npm test` and confirm failures.
- [ ] Implement a bundled Three.js viewer with bounded render work; do not load remote assets/CDNs at runtime.
- [ ] Build static UI into MCP server resources and serve only same-origin loopback routes.
- [ ] Run viewer tests/build plus MCP tests; confirm pass and test manual camera/layer controls.
- [ ] Commit as `feat(builder): add local plan and region viewer`.

### Task 5: End-to-end plan history and stale-state checks

**Files:**
- Test: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/PlanWorkflowEndToEndTest.java`
- Modify: `builder-addon/README.md`

- [ ] Test inspect → archive → publish → world changes → status stale → historical load remains preview-only.
- [ ] Run addon tests, MCP tests, viewer tests, `remapJar`, and `git diff --check`; confirm pass.
- [ ] Document plan history location, stale behavior, viewer URL, and that execution is not implemented.
- [ ] Commit as `test(builder): verify MCP plan and viewer workflow`.

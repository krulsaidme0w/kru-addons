# KRU Builder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete one small, client-only collaborative building session: select an area, discuss it with Codex and images, approve a staged blueprint, and build it manually.

**Architecture:** A third independent Meteor addon owns immutable world snapshots, versioned plans and local progress. A constrained Codex subprocess proposes validated data; it never controls the player or modifies the world. Minecraft adapters and UI stay outside the pure model and transport tests.

**Tech Stack:** Minecraft 1.21.1, Fabric Loader >=0.16.14, Meteor Client 0.5.8, Java 21, Loom 1.7.4, Yarn 1.21.1+build.3, JUnit 5.10.2, Codex App Server JSONL/stdio.

**Spec:** `docs/superpowers/specs/2026-09-27-builder-agent-design.md`

## Global Constraints

- Independent `builder-addon` Gradle root, namespace `dev.krul.builder`, artifact `kru-builder`; initial version `0.1.0-alpha.1`.
- Official external Meteor 0.5.8; no Cobblemon, Academy, sibling-addon or Litematica dependencies.
- No game installation, main-branch mutation, release publication or changes to the legacy repository.
- No world writes, movement, inventory clicks, game commands, autonomous camera movement or agent shell/file tools.
- Current Meteor theme/components; explicit input ownership. No browser UI or hardcoded recreation of Meteor.
- Full block-state equality. Unknown chunks never mean air or completed work.
- Model/provider errors never silently select another provider or retry a paid request.
- Personal paths, world context, images, histories, credentials and logs stay outside version control.
- Existing licensing/provenance must survive any legacy source transfer. A source read or build is not live compatibility proof.
- First transport runs Codex on the Minecraft OS, with configurable local executable; no automatic Windows-to-WSL fallback.
- User-approved isolated Builder Codex state and separate official login; never copy credentials or inherit the ordinary Codex profile. No global environment/config changes.

## Execution and file conventions

Work on `feat/builder-agent`. The user has requested a new branch; a separate
worktree was offered but not yet confirmed. Do not create one without that choice.
Baseline distribution tests: 14 passed before feature work.

Java file paths below are relative to `builder-addon/src/main/java/dev/krul/builder/`.
Test paths mirror them under `builder-addon/src/test/java/dev/krul/builder/`.
Each named type lives in its own file, except nested records explicitly identified below.
Pure packages contain no Minecraft or Meteor imports.

Use a red/green test cycle for every task. Commit only that task's reviewed paths.
Do not merge/push main. Before implementation, read the named execution/TDD skills.
Before UI implementation, read impeccable and its required native-UI design guidance;
the spec's Meteor style is binding, not an invitation to invent a web design system.

## Task 1: Independent addon and bounded world model

**Files:** create Gradle wrapper/build files, `LICENSE`, `NOTICE`, `THIRD_PARTY.md`,
`src/main/resources/fabric.mod.json`, `BuilderAddon.java`, `BuilderCategory.java`,
`model/Cell.java`, `model/Bounds.java`, `model/StateKey.java`, `model/WorldSnapshot.java`.
Tests: `BuilderAddonContractTest.java`, `model/BoundsTest.java`, `model/StateKeyTest.java`.

**Interfaces:**

```java
record Cell(int x, int y, int z) {}
record Bounds(Cell min, Cell max) {
    long volume(); // inclusive endpoints, checked long arithmetic
    boolean contains(Cell cell);
}
record StateKey(String id, Map<String, String> properties) {}
record WorldSnapshot(UUID session, long revision, Bounds bounds,
                     Map<Cell, StateKey> known, Set<Cell> unknown) {}
```

- [ ] Bootstrap test execution using the exact existing Tools build versions and
  verified wrapper bytes, adapting names/metadata only. No Tools feature sources.
  Use Meteor entrypoint `dev.krul.builder.BuilderAddon`, client environment,
  GPL-3.0-or-later, authors, exact MC/Meteor dependencies and no bundled JARs.
- [ ] Write tests before model implementation:

```java
assertEquals(8L, new Bounds(new Cell(0,0,0), new Cell(1,1,1)).volume());
assertThrows(IllegalArgumentException.class,
    () -> new Bounds(new Cell(2,0,0), new Cell(1,1,1)));
assertNotEquals(new StateKey("minecraft:oak_stairs", Map.of("facing", "east")),
                new StateKey("minecraft:oak_stairs", Map.of("facing", "west")));
```

- [ ] Run `bash ./gradlew test` in `builder-addon`; confirm tests fail for missing
  implementations, then implement immutable defensive copies, disjoint known/unknown
  sets, bounds checks, qualified registry ID syntax and checked volume arithmetic.
- [ ] Run tests and `bash ./gradlew build`; verify metadata/entrypoint with an
  archive contract test. This task registers an empty Builder category, not a fake
  functional building module. Commit `feat(builder): add independent addon and world model`.

## Task 2: Bounded client selection and authoritative snapshots

**Files:** `context/Selection.java`, `context/ScanCursor.java`,
`context/WorldReader.java`, `minecraft/ClientWorldReader.java`,
`minecraft/BuilderModule.java`; tests `context/ScanCursorTest.java`.

**Interfaces:** `Selection.setFirst(Cell)`, `setSecond(Cell)`, `bounds()` returns
`Optional<Bounds>`; `WorldReader.read(Cell)` returns `Optional<StateKey>` (empty
means unavailable); `ScanCursor.step(int budget)` returns `Optional<WorldSnapshot>`.
Constructor: `ScanCursor(UUID session, long revision, Bounds bounds, WorldReader reader)`.

- [ ] Write failing scan tests with a fake `WorldReader`; check exact read budget,
  completion only after full pass, unknown preservation and out-of-bounds rejection.

```java
var scan = new ScanCursor(UUID.randomUUID(), 1,
    new Bounds(new Cell(0,0,0), new Cell(1,0,0)), p -> Optional.empty());
assertTrue(scan.step(1).isEmpty());
assertEquals(2, scan.step(1).orElseThrow().unknown().size());
```

- [ ] Run `bash ./gradlew test --tests '*ScanCursorTest'` and record the red result.
- [ ] Implement scanning with a 2,048-read maximum per tick and an initial
  32,768-cell selection cap. Budget exhaustion retains prior complete snapshot.
  Adapter checks loaded chunks without loading/generating them and serializes all
  block properties. Read only on client thread. Epoch changes abandon the cursor.
- [ ] Add Meteor module actions to mark looked-at first/second positions and clear
  selection; never click/use a block or intercept unrelated input. Render bounds.
  Report incomplete selection, oversize selection and unknown counts explicitly.
- [ ] Run context tests/build. Commit `feat(builder): select and scan bounded client regions`.

## Task 3: Validated plan, explicit acceptance and local progress

**Files:** `project/BuildPlan.java`, `project/BuildStage.java`,
`project/PlanValidator.java`, `project/ProjectSession.java`, `project/Progress.java`;
tests `project/PlanValidatorTest.java`, `project/ProjectSessionTest.java`.

**Interfaces:**

```java
record BuildStage(String id, String title, Set<String> after, Map<Cell,StateKey> targets) {}
record BuildPlan(UUID project, long revision, long contextRevision, List<BuildStage> stages) {}
record Progress(int matching, int place, int replace, int unknown) {}
// All coordinates are relative to the selected bounds.min; adapter translates once.
// PlanValidator.validate(plan, bounds, stateExists) returns List<String> errors.
// ProjectSession.propose(plan), accept(revision, freshSnapshot), cancelRequest(),
// accepted(): Optional<BuildPlan>, draft(): Optional<BuildPlan>.
```

- [ ] Write failing tests for duplicate stage IDs, cyclic dependencies, conflicting
  targets, missing registry states, out-of-bounds cells and stale context revisions.
  Test proposal does not change accepted revision and unknown targets prevent completion.

```java
assertTrue(session.accepted().isEmpty());
session.propose(validPlan);
assertTrue(session.accepted().isEmpty());
assertEquals(validPlan, session.draft().orElseThrow());
```

- [ ] Run `bash ./gradlew test --tests '*project.*'` and confirm red.
- [ ] Implement typed validation errors, defensive copies, bounded target count and
  full-state progress. Acceptance requires fresh complete context; changed baseline
  requests renewed review rather than silently rebasing. New draft replaces only
  draft; rejection retains accepted plan. Air targets mean manual removal, never a command.
- [ ] Add paired door/bed checks through a registry-aware adapter; reject unsupported
  NBT-dependent requirements explicitly. Material counts describe placement items
  where a mapping is known; otherwise show block counts, not invented item totals.
- [ ] Run tests/build. Commit `feat(builder): validate and version staged building plans`.

## Task 4: Persist projects without private repository state

**Files:** `project/ProjectStore.java`, `project/ProjectCodec.java`;
test `project/ProjectStoreTest.java`; update `.gitignore`.

**Interfaces:** `ProjectStore(Path root)`, `save(BuildPlan plan)`,
`load(UUID project)` returns `Optional<BuildPlan>`; codec has explicit schema version 1.

- [ ] Write roundtrip and corrupt-file tests using `@TempDir`; ensure unknown schema
  versions fail with an error, a failed save retains previous contents and UUID-based
  filenames cannot escape root.

```java
var store = new ProjectStore(tempDir);
store.save(plan);
assertEquals(plan, store.load(plan.project()).orElseThrow());
```

- [ ] Run `bash ./gradlew test --tests '*ProjectStoreTest'` red, then implement
  temp-file/atomic replace with explicit non-atomic fallback notification. No secret,
  absolute user path or image bytes inside exported blueprint JSON.
- [ ] Keep runtime files in instance-local `kru-builder/`; ignore `**/kru-builder/`.
  Background save gets immutable plans; surface failure to UI. Explicit reopen is
  required after changing worlds. No automatic project-to-world binding by coordinates.
- [ ] Run persistence tests/build; commit `feat(builder): persist versioned local projects`.

## Task 5: Constrained Codex protocol and process lifecycle

**Files:** `agent/AgentRequest.java`, `agent/AgentEvent.java`, `agent/AgentClient.java`,
`agent/CodexProcess.java`, `agent/CodexProtocol.java`, `agent/PlanResponseCodec.java`;
tests `agent/CodexProtocolTest.java`, `agent/CodexProcessTest.java`.
Add `agent/CodexIsolation.java` and its focused tests for child-process policy,
owned state directory, version/effective-config checks and safe argument creation.

**Interfaces:** `AgentRequest(UUID project, UUID session, long contextRevision,
String text, WorldSnapshot context, List<Path> images)`;
`AgentClient.start(AgentRequest, Consumer<AgentEvent>)`, `cancel()`, `close()`.
`AgentEvent` sealed variants: Status, TextDelta, Proposal(BuildPlan), Failed, Completed.
The request may additionally carry an immutable optional accepted-plan baseline;
retain the listed convenience constructor. A discussion response may contain a
message without a plan. Do not display raw structured plan JSON as chat prose.

- [ ] Read local CLI help/schema and current official App Server docs using OpenAI
  Docs. Generate schema only in an ignored temporary directory. Confirm handshake,
  image input, output schema, cancellation and disabling shell/file tools before
  writing production transport. If tools cannot be constrained, stop this task and
  report the exact missing capability; do not rely on a prompt as a sandbox.
- [ ] Use an addon-owned isolated Codex state directory and empty cwd with the
  supported child-process configuration mechanism. Do not reassign parent shell
  environment variables or read/copy the user's auth/config. Disable hooks,
  plugins, apps, shell/file/network tools and skill discovery using verified keys
  before thread creation; explicitly use empty environments for every turn.
  Fail closed on incompatible versions or effective system/managed configuration.
  Test startup arguments, isolated login/status, forbidden inherited config,
  credential-free diagnostics and refusal before any model request. No real login
  or paid requests in automated validation; dedicated login remains a user action.
- [ ] Write protocol tests against recorded synthetic frames, never personal rollout
  files: split JSONL frames, interleaved notifications, EOF, oversized line, bad JSON,
  wrong request/session IDs, malformed proposals and cancellation races.

```java
// Fake process streams supply stdout/stderr and record stdin.
client.start(request, events::add);
client.cancel();
fake.emitLateProposal();
assertTrue(events.stream().noneMatch(e -> e instanceof AgentEvent.Proposal));
```

- [ ] Run `bash ./gradlew test --tests '*agent.*'` red. Implement stdio process
  ownership, bounded queues, separate stderr diagnostics, explicit startup timeout
  (15 seconds) and user-visible request timeout (180 seconds). No automatic retry.
  Cancel gracefully then terminate only the process owned by this client.
- [ ] Send text plus allowed images and bounded snapshot; process structured plan
  JSON as data only. Wire operations are `block(position,state)` and
  `fill(min,max,state)` with inclusive relative integer coordinates. Expand fills
  locally after checked volume validation; cap at 4,096 operations and 32,768
  distinct targets. Reject overlapping conflicting assignments, arbitrary scripts
  and references to undeclared palette entries. Limit response lines to 4 MiB and
  total response to 16 MiB.
  Ignore unsolicited tools and reject unsupported proposal fields. Validate every
  plan through Task 3 before exposing a proposal.
- [ ] Expose normal Codex login/status through its supported protocol; display
  account errors without reading credential stores. Use default configured model
  only if required image capability is available; otherwise report incompatibility.
- [ ] Run fake-process tests/build; commit `feat(builder): add constrained Codex session transport`.

## Task 6: Visual attachments and textured blueprint

**Files:** `visual/AttachmentPolicy.java`, `minecraft/WorldCapture.java`,
`blueprint/BlueprintRenderer.java`, `blueprint/BlueprintDiff.java`;
tests `visual/AttachmentPolicyTest.java`, `blueprint/BlueprintDiffTest.java`;
update `THIRD_PARTY.md` for every copied legacy source and adaptation.

**Interfaces:** `WorldCapture.capture()` returns `CompletableFuture<Path>`;
`BlueprintDiff.compare(BuildPlan, WorldSnapshot)` returns `Progress`;
renderer takes an immutable validated plan and selected stage/layer.

- [ ] Write failing tests for full-state differences, unknown cells, layer filtering,
  explicit air/removal targets, attachment count/dimensions/byte limits.

```java
assertNotEquals(eastFacingStair, westFacingStair);
assertEquals(1, BlueprintDiff.compare(oneTargetPlan, unknownSnapshot).unknown());
```

- [ ] Run relevant tests red. Audit legacy Blueprint source/license before copying
  selected classes. Remove old ID-only matching; preserve textured ghost/outline
  distinction. Scan/progress stays pure and bounded; renderer never mutates the world.
- [ ] Implement a world-only capture hook before HUD, asynchronous encoding, maximum
  image edge 1,280 pixels, 4 attachments and 8 MiB combined encoded input. Refuse
  invalid/unsupported images; don't silently omit requested context. Keep temporary
  images alive until request finishes, then release them; crash cleanup is bounded
  to addon-owned temporary files. Draft thumbnails are inspectable/removable.
- [ ] Run tests/build; inspect ghost layers, replacement outlines, image capture and
  resource-pack appearance in a separate test client. Clearly mark live checks not
  possible in the current environment. Commit `feat(builder): add visual context and blueprint preview`.

## Task 7: Meteor chat and session integration

**Files:** `ui/BuilderScreen.java`, `ui/BuilderHud.java`, `ui/BuilderController.java`,
`ui/BuilderUiState.java`; update `minecraft/BuilderModule.java`;
test `ui/BuilderControllerTest.java`.

**Interfaces:** Controller consumes selection/snapshot service, AgentClient,
ProjectSession, WorldCapture and ProjectStore. Actions: `send(String)`,
`cancel()`, `accept(long)`, `reject()`, `worldChanged(UUID)`, `close()`.
UI reads immutable state; it does not directly touch subprocesses or parse responses.

- [ ] Read applicable impeccable instructions. Use native Meteor widgets/current
  theme; create no HTML/mock frontend. Main window contains conversation, attachment
  thumbnails and current-stage card. Connection/executable settings are collapsible.
- [ ] Write failing controller tests with fake services: no send before privacy
  consent, send uses freshly captured data, removed image not sent, double-send
  disabled, cancel discards late events, world change clears live snapshot/draft,
  provider failure preserves accepted plan.

```java
controller.worldChanged(newSession);
fakeAgent.deliver(oldSessionProposal);
assertTrue(controller.state().draft().isEmpty());
```

- [ ] Run `bash ./gradlew test --tests '*BuilderControllerTest'` red, then implement
  ownership/version guards and state transitions. Keep network/image work off client
  thread. Explicitly distinguish Disconnected, Ready, Preparing, Waiting, Failed
  and Cancelled; no empty progress indicator presented as success.
- [ ] Add Send/Cancel, Preview/Accept/Reject, stage/layer controls, collapse and
  project reopen. Show proposed-vs-accepted revision and stale-world reason.
  Escape releases typing focus/closes the screen without cancelling accepted work.
  HUD collapse never captures movement or server-chat input. No forced hotkey conflict.
- [ ] Run tests/build and focused live checks for scaling, text focus, thumbnail
  removal, collapse and changing dimensions. Commit `feat(builder): integrate in-game collaborative building UI`.

## Task 8: Packaging, regressions and review

**Files:** modify `distribution/release.py`, `distribution/verify_artifacts.py`,
their tests, `distribution/dependencies.json`, `.github/workflows/build.yml`,
`README.md`, `AGENTS.md`; create `builder-addon/README.md`,
`docs/builder-runtime-checklist.md`.

- [x] Write failing Python tests for builder artifact/metadata, cross-addon imports,
  bundled Meteor/Cobblemon rejection and `builder-v0.1.0-alpha.1` tag routing.

```python
self.assertEqual(("builder-addon", "0.1.0-alpha.1"),
                 release.parse_tag("builder-v0.1.0-alpha.1"))
```

- [x] Run `python3 -m unittest discover -s distribution -v` red. Extend explicit
  addon registries and CI matrix to three independent builds; retain old rejection
  tests, pinned actions and draft-only release workflow. Include source/license
  notices and SHA256 assets. No live auth/model requests in CI.
- [x] Document runtime setup, local-vs-cloud disclosure, exact limits, unavailable
  states, privacy controls, native-OS CLI requirement, experimental status and no
  server permissions/world edits. Mark incomplete live checks honestly.
- [ ] Run all three Gradle builds, distribution tests, builder packaging, diff check,
  workflow lint and private-path/secret scan of changed files. Inspect actual JAR
  contents and metadata. Require independent code review before integration.
- [x] Commit `build: package and verify independent KRU Builder addon`. Report tests
  separately from manual/runtime evidence. Stop before installing, merging main or
  publishing a release; present the feature branch and remaining live checks.

Task 8 execution note: all three Gradle builds, 19 distribution tests, Builder
packaging, artifact inspection, YAML parsing/structure checks, diff whitespace
check and changed-file private-path/token scan passed. `actionlint` is unavailable
in the environment; no live runtime checks were performed, and the checklist
below remains unchecked until tested in Minecraft.

## Self-review and checkpoints

All spec areas map to Tasks 1–8: independent boundaries (1/8), world facts (2),
revision/validation/progress (3), persistence (4), constrained Codex (5), images
and ghosts (6), native UI/consent/lifecycle (7), verification/distribution (8).
Protocol constraints are a mandatory feasibility gate, not a claim already proved.
Review each task before its dependent task starts. First execution checkpoint:
Tasks 1–3, with no AI calls, installation or world mutations.

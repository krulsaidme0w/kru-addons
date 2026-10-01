# Builder Selection-Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Builder's embedded Codex/chat workflow and make the active module a minimal bounded-selection, scan, overlay, and explicitly paired MCP client.

**Architecture:** `BuilderModule` owns only bounds, scan, bridge, pairing, and preview lifecycle. `BuilderScreen` is a thin Meteor window over those operations; it does not depend on an agent/controller/archive. Keep the shared immutable plan contract/validator and external MCP plan archive/viewer.

**Tech Stack:** Java 21, Fabric Loom, Minecraft 1.21.1/Yarn, Meteor Client 0.5.8, JUnit 5; separate Gradle roots for `builder-addon` and `builder-mcp-server`.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-selection-only-design.md`

## Global Constraints

- Bounds remain scoped to the existing world context and scans read only loaded client-world cells, with a 32,768-cell cap and 2,048 reads per tick.
- Activation does not launch Codex, authenticate, reopen projects, or transmit data.
- Pairing requires an explicit Allow action; MCP plans render as preview only.
- Do not add world placement, movement, server commands, implicit pairing, or cloud transmission.
- Delete only confirmed Builder `conversations`, `projects`, and `attachments` data; preserve `codex-profile`, Minecraft data, and MCP plan history.
- Do not install or replace a mod as part of implementation.

---

## File Map

- `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java` — selection/scan lifecycle, bridge startup, bounds editor, activation screen; remove embedded agent/archive/session lifecycle.
- `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java` — replace chat workspace with bounds, scan status/action, overlay toggle, and MCP pairing controls.
- `builder-addon/src/main/java/dev/krul/builder/ui/BuilderPanelPresentation.java` — new pure presentation helper for scan/pairing/status labels, independently unit tested.
- `builder-addon/src/test/java/dev/krul/builder/ui/BuilderPanelPresentationTest.java` — minimal-screen/status contract tests.
- `builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java` — update addon package contract; no in-addon Codex/Conversation controller types.
- `builder-addon/src/main/java/dev/krul/builder/{agent,archive,visual}` and related tests — remove dead Codex transport, chat archive/migration, and image attachment implementation after consumers are removed. Keep MCP catalog/data-source code under `minecraft/catalog` and `agent.BuilderDynamicTools` only if a remaining production consumer is proven; tests alone do not justify retaining legacy agent code.
- `builder-addon/src/main/java/dev/krul/builder/project` — retain `BuildPlan`, `BuildStage`, `PlanContract`, `PlanContractCodec`, `PlanOperation`, `PlanPreconditionValidator`, `PlanValidator`, and `Progress` only where referenced by MCP/preview; remove `ProjectSession`, `ProjectStore`, `ProjectCodec` if no remaining production consumer.
- `builder-addon/README.md`, `README.md`, `AGENTS.md`, `docs/builder-runtime-checklist.md` — replace obsolete in-game Codex instructions with selection/MCP operation and update Builder policy.
- `builder-addon/src/test/java` — delete tests for removed Codex/archive/attachment/session flows; retain and run scanner, bounds, pairing, bridge, catalog, plan validation, preview, MCP server/history tests.

## Task 1: Lock the minimal panel contract with tests

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderPanelPresentation.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/ui/BuilderPanelPresentationTest.java`

**Interfaces:**
- Produces `BuilderPanelPresentation.scanLine(String scanStatus)` and
  `BuilderPanelPresentation.pairingLine(boolean paired, boolean pending)` as
  pure display helpers. These are only labels; no lifecycle state is stored.

- [ ] **Step 1: Write failing label tests**

```java
package dev.krul.builder.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BuilderPanelPresentationTest {
    @Test void exposesScanProgressVerbatim() {
        assertEquals("Scanning · 12/20", BuilderPanelPresentation.scanLine("Scanning · 12/20"));
    }

    @Test void distinguishesPairingStates() {
        assertEquals("MCP: paired", BuilderPanelPresentation.pairingLine(true, false));
        assertEquals("MCP: pairing request", BuilderPanelPresentation.pairingLine(false, true));
        assertEquals("MCP: not paired", BuilderPanelPresentation.pairingLine(false, false));
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails because the helper is absent**

Run: `cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.ui.BuilderPanelPresentationTest`

Expected: compilation fails with unresolved `BuilderPanelPresentation`.

- [ ] **Step 3: Implement the two pure helper methods**

`scanLine` returns the supplied non-null scan status unchanged. `pairingLine`
returns `MCP: paired` when `paired`, else `MCP: pairing request` when pending,
else `MCP: not paired`.

- [ ] **Step 4: Run focused test and commit**

Run the same Gradle test command; expect 2 passing tests. Commit the two files as
`test(builder): define minimal panel status contract`.

## Task 2: Replace the chat window with the selector and detach runtime lifecycle

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderPanelPresentation.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/ui/BuilderPanelPresentationTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java`

**Interfaces:**
- `BuilderScreen` receives a supplier for the existing bounds editor, scan-status
  supplier, scan action, overlay getter/toggle, pairing challenge/state and
  Allow/Reject/Revoke callbacks. It has no `BuilderController` or `AgentClient`.
- `BuilderModule` remains the owner of `ScanSession`, `BuilderBridgeServer`,
  `BridgePairing`, and the currently published MCP preview.

- [ ] **Step 1: Add failing contract assertions for the removed UI/runtime dependency**

Update `BuilderAddonContractTest` to assert the packaged addon has no class
`dev.krul.builder.ui.BuilderController` and no class
`dev.krul.builder.agent.CodexProcess`, and add a presentation test asserting
failure/scan text is not replaced with a Codex/chat action label. Run the focused
tests and expect the contract to fail before removal.

- [ ] **Step 2: Refactor the screen to one vertical selection panel**

Delete conversation sidebar/workspace, prompt composer, image/reference rows,
Codex connect/sign-in, project restore, plan accept/discard/history, and stage
controls. Render title, `Overlay: ON/OFF`, the six-coordinate bounds editor,
scan status and `Scan / Rescan`, then MCP bridge status and the applicable
Allow/Reject or Revoke action. Refresh status labels from suppliers during
`tick`; keep window height bounded by the viewport. `Collapse` closes the
window without deactivating Builder. Applying bounds or marking either corner
must not start a scan; only `Scan / Rescan` starts one. Change bounds-editor
feedback from “Next send scans this area” to “Press Scan to read this area.”

- [ ] **Step 3: Remove BuilderModule's chat/Codex initialization**

Delete executable setting, `AgentClient`/`CodexProcess`, `BuilderController`,
archive/project-session setup, attachment policy, archive migration, and
conversation-selection callbacks. Initialize `WorldCapture` only if still
required by MCP screenshot capture; keep `BuilderBridgeDataSource`, dynamic
block catalog access, pairing, `freshSnapshot`, plan validation, and preview.
Do not create a Codex profile or read/write a conversation archive on
activation. `requestScan()` must be callable directly from the new screen and
continue to use `ScanSession.requestScan`.

- [ ] **Step 4: Make activation and keybind consistent with the approved flow**

When activated with a live world, open the minimal selector once after module
initialization; do not auto-scan. Retain a keybind named `open-builder` to reopen
the selector while active. `getWidget` opens the selector. Remove the old
`open-builder-chat` keybind. If activation occurs without a world, show the same
screen with “No world: selection unavailable”; entering a world refreshes the
screen state without starting a scan.

- [ ] **Step 5: Run UI and addon contract tests**

Run: `cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.ui.BuilderPanelPresentationTest --tests dev.krul.builder.BuilderAddonContractTest`

Expected: all focused tests pass; no `BuilderController`/`CodexProcess` class
is packaged.

- [ ] **Step 6: Commit the minimal panel/runtime separation**

Commit as `refactor(builder): make module a selection and MCP panel`.

## Task 3: Remove orphaned legacy implementation and repair documentation/contracts

**Files:**
- Delete legacy production packages/classes under `builder-addon/src/main/java/dev/krul/builder/agent`, `archive`, and `visual` when no MCP/preview production source imports them.
- Delete orphaned `ProjectSession`, `ProjectStore`, and `ProjectCodec` only after `rg` confirms they are not used by MCP/preview production source.
- Delete only tests whose subject is removed code; keep `BuilderDynamicToolsTest` only if the catalog tool has an active production caller.
- Modify: `builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java`
- Modify: `builder-addon/README.md`, `README.md`, `AGENTS.md`, `docs/builder-runtime-checklist.md`

**Interfaces:**
- The addon/MCP plan boundary stays `BuildPlan` + `PlanContractCodec` and existing
  validation APIs; do not change MCP JSON/schema contracts in this task.

- [ ] **Step 1: Enumerate production and test references before deleting files**

Run:

```sh
rg -n 'dev\.krul\.builder\.(agent|archive|visual)|ProjectSession|ProjectStore|ProjectCodec|BuilderController|BuilderUiState' builder-addon/src/main/java builder-mcp-server/src/main/java
```

Expected after Task 2: any hits must be mapped to a live caller or removed; test
references are inventoried separately before deletion.

- [ ] **Step 2: Delete the now-unreferenced embedded agent/archive/attachment code and tests**

Remove only files with zero production callers after updating remaining tests.
Retain BuildPlan/validation/preview types consumed by MCP. Do not remove local
MCP block-catalog or bridge classes because their names mention agents or plans.

- [ ] **Step 3: Add a static-source contract guarding against resurrection**

Extend `BuilderAddonContractTest` to inspect the JAR entries and assert there
are no `dev/krul/builder/agent/CodexProcess.class`,
`dev/krul/builder/archive/ConversationArchiveStore.class`,
`dev/krul/builder/ui/BuilderController.class`, or
`dev/krul/builder/visual/AttachmentPolicy.class`; assert the shared
`dev/krul/builder/project/BuildPlan.class` and bridge data-source classes remain.

- [ ] **Step 4: Rewrite addon quick start and privacy documentation**

Replace old Codex setup/send/privacy sections in `builder-addon/README.md` with
these user actions: enable Builder, set/apply bounds, press Scan, explicitly
approve the loopback MCP pairing request, use Codex's separately installed MCP
plugin to inspect/build a preview, and understand that preview never places
blocks. Keep the MCP plan-history storage path and current scan limits. Remove
claims about in-addon Codex login/profile, screenshots as chat attachments,
chat archives, project reopen, Send, and Save plan.

- [ ] **Step 5: Align root docs, agent instructions, and runtime checklist**

Replace the root README's Builder Codex transmission claims with a short pointer
to `builder-addon/README.md`. Replace only the Builder bullet in `AGENTS.md`
with the approved selection-only/MCP constraints; preserve unrelated rules.
Remove in-addon Codex-login/chat-input steps from
`docs/builder-runtime-checklist.md`, adding checks for activation without
auto-scan, explicit scan, pairing approval/rejection, MCP preview-only behavior,
and world-change invalidation.

- [ ] **Step 6: Run all tests in both Gradle roots and verify references**

Run:

```sh
cd builder-addon && ./gradlew --no-daemon clean build
cd ../builder-mcp-server && ./gradlew --no-daemon test shadowJar
cd .. && rg -n 'BuilderController|CodexProcess|ConversationArchiveStore|AttachmentPolicy|Connect / sign in|Describe the build|Reopen saved project' builder-addon/src/main builder-addon/README.md README.md AGENTS.md
```

Expected: both builds pass; final `rg` has no references to removed Builder
flows, except any explicit historical note that says the feature is removed.

- [ ] **Step 7: Commit legacy removal/docs**

Commit production/test deletion and docs as
`refactor(builder): remove embedded Codex conversation stack`.

## Task 4: Verify and remove only the agreed legacy runtime data

**Files:**
- No source files. Exact Minecraft instance `kru-builder` data paths only.

**Interfaces:**
- Inputs are the confirmed Minecraft run directory and its direct child
  `kru-builder`; no other directory is in scope.

- [ ] **Step 1: Resolve the actual installed instance's run directory read-only**

Inspect the existing launcher/Prism instance metadata and locate the Builder
data directories. Do not infer a path from `$HOME`, a broad search result, or a
symlink. If multiple live instances or multiple `kru-builder` directories are
present, stop and ask which one; do not clean all copies.

- [ ] **Step 2: Print and inventory exact cleanup candidates**

For the selected instance, resolve exactly
`<runDirectory>/kru-builder/conversations`, `projects`, and `attachments`;
list type, size, and relative names without following symlinks. Confirm the
resolved parent is the selected run directory and explicitly show that
`codex-profile` is excluded. If a candidate is a symlink or resolves outside
the expected parent, stop and report instead of deleting it.

- [ ] **Step 3: Delete only confirmed candidate directories**

After comparing the printed list to the approved spec, delete the three exact
directories if present. Do not use a recursive glob or a broad `rm -rf` target.
Report absent directories and the removed item/byte counts. Preserve all
siblings, especially `codex-profile`, world saves, configuration, and MCP plan
history.

- [ ] **Step 4: Verify postconditions**

Re-list the exact parent: `conversations`, `projects`, and `attachments` must be
absent; `codex-profile` and unrelated sibling entries must match the pre-delete
inventory. Record that this data cleanup is not recoverable from the deleted
files and report exact directories removed.

## Final verification checklist

- [ ] `builder-addon` clean build and tests pass.
- [ ] `builder-mcp-server` tests and shadow JAR build pass.
- [ ] Addon JAR contains no in-addon Codex/chat/archive/attachment runtime classes.
- [ ] Selection bounds, explicit scan progress/results, overlay, and MCP pairing
  are the only Builder window functions.
- [ ] Enabling Builder does not scan, authenticate, launch Codex, reopen plans,
  or send data.
- [ ] Old Builder conversation/project/attachment folders are removed only from
  the verified instance; `codex-profile` remains untouched.
- [ ] No mod installed/replaced and no live-client claim without runtime test.

# PC Organizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separate `PC Organizer` Meteor module with a transparent overlay over the live Cobblemon PC, explicit protected boxes, read-only preview, and exact-ACK one-Pokémon transfers.

**Architecture:** Reuse the existing authoritative PC reflection boundary and packet sender, but keep organizer state separate from UI Utils. A pure planner will turn an immutable PC snapshot plus organizer settings into a preview/transfer plan; a coordinator will send at most one UUID move at a time and require the next authoritative PC snapshot to confirm it. The overlay is contextual to the currently open PC and never changes generic UI Utils behavior.

**Tech Stack:** Java 21, Fabric/Loom, Meteor `Module`, existing overlay `OverlayContent`, Cobblemon reflection boundary, JUnit 5.

**Spec:** This plan implements the approved PC Organizer design from the conversation: separate module, explicit Main/Utility/protected boxes, AND filters, deterministic sorting, preview before apply, and safe exact-ACK moves.

## Global Constraints

- Work only in `<repository>/cobblemon-addon`.
- Do not modify or replace Meteor client, UI Utils generic container handling, or other installed mods.
- PC and party UUIDs, PC identity, connection, and world identity must be revalidated before every request.
- Preview mode is the default and sends no mutation packet.
- Apply sends one `MovePCPokemonPacket`-compatible request at a time and waits for authoritative destination ACK.
- Protected boxes are excluded as both move sources and destinations.
- Any ambiguous snapshot, duplicate UUID, changed PC identity, unexpected delta, or unavailable profile blocks the run without guessing.

---

### Task 1: Freeze the domain model and protected-box rules

**Files:**
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerSettings.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerFilter.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerSortKey.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerBoxPolicy.java`
- Create: `src/test/java/dev/krul/cobblemon/pcorganizer/PcOrganizerBoxPolicyTest.java`

**Interfaces:**
- `PcOrganizerBoxPolicy` consumes box count, names, explicit protected indexes, and optional role assignments; produces an immutable protected index set and user-readable explanations.
- `PcOrganizerSettings` consumes scope, filters, sort keys, and protected policy; produces validated settings with preview as the default run mode.

- [ ] Write failing tests proving explicit protected boxes exclude both source and destination, duplicate protected indexes are harmless, and no protected selection means no implicit protection.
- [ ] Run `./gradlew test --tests 'dev.krul.cobblemon.pcorganizer.PcOrganizerBoxPolicyTest' --console=plain` and verify the new tests fail because the domain classes do not exist.
- [ ] Implement the immutable settings, filter, sort-key, and box-policy records/enums without Minecraft dependencies.
- [ ] Run the focused test again and verify it passes.
- [ ] Add validation tests for empty scope, invalid box indexes, and preview as the default.
- [ ] Re-run the focused tests.

### Task 2: Build the pure snapshot, filtering, sorting, and preview planner

**Files:**
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerSnapshot.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerPlan.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerPlanner.java`
- Create: `src/test/java/dev/krul/cobblemon/pcorganizer/PcOrganizerPlannerTest.java`

**Interfaces:**
- `PcOrganizerSnapshot` stores authoritative PC UUID/store identity, box names, and immutable slot records containing UUID, species, form/aspects, types, shiny/radiant, rarity, ability, held item, level, IVs, and original position.
- `PcOrganizerPlanner.plan(snapshot, settings)` returns either a blocked reason or a deterministic `PcOrganizerPlan` containing unchanged protected slots, selected Pokémon, destination slots, and human-readable move previews.

- [ ] Write failing tests for AND filters, deterministic primary/secondary/tertiary sorting, stable original-slot tie-breaks, sparse boxes, and no moves when the layout already matches.
- [ ] Run the planner test and verify failure.
- [ ] Implement pure filtering and comparator composition; unknown facts must fail closed for an active filter and remain sortable only when explicitly selected as unknown-last.
- [ ] Implement destination allocation only in unprotected boxes and preserve every protected slot exactly.
- [ ] Run focused planner tests and verify pass.
- [ ] Add tests proving preview never generates a party move and never allocates into protected boxes.

### Task 3: Add authoritative PC reader and exact-ACK coordinator

**Files:**
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerReader.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerMutationPort.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerCoordinator.java`
- Modify: `src/main/java/dev/krul/cobblemon/pcui/NativePcAccess.java` only for a shared read-only authoritative snapshot helper if required.
- Create: `src/test/java/dev/krul/cobblemon/pcorganizer/PcOrganizerCoordinatorTest.java`

**Interfaces:**
- `PcOrganizerReader.read(screen)` produces an authoritative `PcOrganizerSnapshot` only when live PC identity, box list, slot UUIDs, and required profile facts are available.
- `PcOrganizerMutationPort.move(uuid, from, to)` sends the existing Cobblemon PC move packet.
- `PcOrganizerCoordinator.tick(snapshot, monotonicTick)` produces `PREVIEW`, `REQUESTED`, `WAITING_ACK`, `ACK`, `COMPLETE`, or `BLOCKED` and never repeats a pending semantic move.

- [ ] Write failing tests for preview producing no mutation, one request per tick/transaction, exact destination ACK, unexpected delta blocking, protected-slot preservation, and PC identity changes blocking.
- [ ] Run coordinator tests and verify failure.
- [ ] Implement the coordinator with a single pending move and bounded monotonic ACK deadline.
- [ ] Re-read the full authoritative layout after each ACK before planning the next move.
- [ ] Run focused coordinator tests and verify pass.
- [ ] Add a test for duplicate UUIDs and unavailable profile facts blocking before any request.

### Task 4: Create the standalone Meteor module and contextual overlay

**Files:**
- Create: `src/main/java/dev/krul/cobblemon/PcOrganizer.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerContent.java`
- Create: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerUiState.java`
- Modify: `src/main/java/dev/krul/cobblemon/CobblemonAddon.java` to register only the new standalone module.
- Create: `src/test/java/dev/krul/cobblemon/pcorganizer/PcOrganizerSurfaceTest.java`

**Interfaces:**
- `PcOrganizer` owns settings, lifecycle, PC-screen detection, overlay context, coordinator ticks, status/evidence, and the PC mutation lease.
- `PcOrganizerContent` renders the separate overlay: scope, protected boxes, collapsed AND filters, sort keys, counts, preview list, and Preview/Apply controls.
- The overlay must disappear or become blocked when the current screen is not the same live PC session.

- [ ] Write failing surface tests for separate module registration, UI Utils independence, Preview default, protected-box summary, and Apply disabled until a valid preview exists.
- [ ] Run surface tests and verify failure.
- [ ] Implement the module and overlay using existing `OverlayHost`/`OverlayContent` conventions, without adding buttons to `UiToolsContent`.
- [ ] Add explicit box-role controls: Main, Utility, Breeding, Special, Shinies, plus manual box toggles; show locked boxes and the exact reason each is protected.
- [ ] Add filter controls for species/type/form, shiny/radiant, rarity, IV preset, ability, and held item, with active filters summarized in one line.
- [ ] Add Preview and Apply confirmation; preview shows `source box/slot → destination box/slot`, protected count, touched boxes, and blocked reasons.
- [ ] Run surface tests and verify pass.

### Task 5: Runtime integration, verification, and handoff

**Files:**
- Modify: `src/main/java/dev/krul/cobblemon/pcorganizer/PcOrganizerContent.java` only for final copy/layout corrections.
- Modify: `src/main/java/dev/krul/cobblemon/PcOrganizer.java` only for lifecycle corrections found by tests.
- Test: `src/test/java/dev/krul/cobblemon/pcorganizer/*Test.java`

- [ ] Run all PC Organizer tests.
- [ ] Run `./gradlew test remapJar --offline --console=plain`.
- [ ] Inspect the remapped jar contents to confirm `PcOrganizer` is present and UI Utils classes were not modified by the feature.
- [ ] Do not install the jar until the user explicitly requests installation.
- [ ] Report exactly what is ready for in-game validation: PC screen detection, protected boxes, preview, and apply/ACK behavior.

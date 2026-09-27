# Independent addons implementation plan

> **For agentic workers:** Use executing-plans for local tasks and dispatching-parallel-agents for independent audit/port tasks. Read the spec before editing.

**Goal:** Build two independently installable Meteor addons with a server-neutral Cobblemon feature set.

**Architecture:** Official Meteor is an external dependency. Independent Gradle roots provide separate JARs. Cobblemon observations pass through configurable rules, while UI uses Meteor components.

**Tech Stack:** Java 21, Minecraft 1.21.1, Yarn 1.21.1+build.3, Fabric, Meteor 0.5.8, JUnit 5, Starscript supplied by Meteor.

**Spec:** ../specs/2026-09-20-independent-addons-design.md

## Global constraints

- Do not edit cobbhelperv2 or installed Minecraft mods.
- No cross-addon dependencies; no copied Meteor classes in addon artifacts.
- Academy-specific classifiers are opt-in data presets.
- PC Autosort is deferred; excluded legacy features are not migrated.

## 1. Independent build contract

Files: each addon's `settings.gradle`, `build.gradle`, `gradle.properties`, wrapper, `fabric.mod.json`, addon entrypoint; `distribution/verify_artifacts.py` and its unittest.

- [x] Test artifact validation with fixture ZIPs: reject bundled `meteordevelopment/meteorclient/*.class`, missing Meteor dependency, and dependency on the sibling addon; accept isolated addons.
- [x] Adapt official template entrypoint/build contract to pinned 1.21.1 coordinates. No local Meteor jar or source dependency.
- [x] Build both roots separately, validate remapped manifests and package boundaries.

## 2. Generic observation rules

Files: `cobblemon-addon/src/{main,test}/java/dev/krul/cobblemon/rules/`, preset resources.

- [x] Failing tests for exact matches, missing-field rejection, minimum level, name suffix, Starscript per-observation labels, invalid rules and independent contexts.
- [x] Implement immutable facts/rules, validated JSON load and compiled Starscript labels. JSON presets supply Academy entity names, models and dimensions.
- [x] Verify changing config changes classification without a code change; no preset means no dungeon match.

## 3. Preserve feature implementations

Files: migrated discovery/dungeon/pokemon/species/battle code and tests under `dev.krul.cobblemon`; own mixin and Type Chart window.

- [x] Copy only the audited dependency closure and tests; namespace mechanically, retain GPL notices.
- [x] Replace core registrations with addon initialization; remove screenshot hooks and excluded automation/UI dependencies.
- [x] Connect dungeon classification to configurable rules. Strip repeated Guardian and chest-opening assumptions from generic code.
- [x] Open Type Chart using Meteor window/bind; preserve read-only battle calculations and report unsupported ABI rather than claiming generic compatibility.
- [x] Run all migrated tests and remapJar; review artifacts and dependency graph.
- [x] Complete the shared local Meteor visual contract for Type Chart and Battle Matchups.
- [ ] Live input/render verification in Minecraft remains outstanding.

## 4. Tools upstream decision and integration

Files: `docs/tools-upstream-audit.md`, tools addon source after selection.

- [x] Audit original UI-Utils, Advanced and DupersUnited source, lifecycle, packet logic, actual conflicts and source availability.
- [x] Document smallest compatible adaptation and unresolved choices before large porting work.
- [x] Preserve source attribution and upstream behavior tests. UI-Utils behavior is adapted into Meteor lifecycle/modules; no Swing/global-static state or file export is copied.
- [x] Implement live Packet Inspector controls: ALL/C2S/S2C, capture type settings, substring search, movement/keepalive suppression, clear and unpaged rows.
- [x] Implement bounded GUI packet delay and HandledScreen controls with disconnect/off cleanup.

## 5. Handoff

- [x] Independent builds, artifact-boundary validation and focused code review.
- [x] Record exact completed/unfinished features and build/runtime evidence in `docs/implementation-status.md`.
- [ ] User reviews any required upstream-selection/backport decision; no implicit live install.

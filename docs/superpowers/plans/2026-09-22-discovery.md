# Discovery Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development, task-by-task. Preserve the existing uncommitted source tree; no commits or installation.

**Goal:** Deliver source-aware Pokémon Discovery and editable, profile-driven Discovery in the independent Cobblemon addon.

**Architecture:** Immutable, scoped observations feed both modules. Typed rules and transactional profile editing are independent of Minecraft. Existing Meteor widgets and overlay host own UI and input.

**Tech Stack:** Java 21, Minecraft 1.21.1, Meteor 0.5.8, Fabric, JUnit 5.

**Spec:** docs/superpowers/specs/2026-09-22-discovery-design.md

## Global Constraints

- Official external Meteor 0.5.8; Minecraft 1.21.1. No Academy hard dependency.
- Do not edit cobbhelperv2, tools-addon, unrelated modules, or installed mods.
- Missing facts never count as proof; provenance survives into UI.
- Server-specific identifiers and semantics belong in explicitly enabled profiles.
- Keep serialization keys discovery and dungeon-discovery; never force global HUD active.
- Unit tests and a build are not live runtime evidence.
- No commits: unborn repository contains inherited user files; baseline is /tmp/kru-discovery-baseline-wIFMwY/src.

### Task 1: Source-aware facts and correct optional CobbleNav adapter

**Files:** new observation domain under src/main/java/dev/krul/cobblemon/observation; modify discovery/CobblemonSpawnMapPacketReader.java; dedicated tests under src/test/java/dev/krul/cobblemon.

**Interfaces:** Produce immutable ObservationSnapshot, FactValue, FactSchema and PokemonFactReader. Snapshot exposes scalar facts for rule matching separately from provenance. Do not change existing observation constructors until consumers integrate in Task 3. Correct existing spawn map reader API without changing DiscoveryRarityRuntime call sites.

- [ ] Add failing tests for partial getter failure, missing ownership, scope identity, unknown/private values and actual getBucketName/getSpawnDataList/getRenderable packet shape. Run targeted JUnit tests and record the expected failure.
- [ ] Implement per-field reader isolation and immutable bounded typed scalars. Registry/reference is distinct from live; do not read private IV/EV/nature/ability defaults as live facts. Missing owner getter is unknown, not wild. Exact optional CobbleNav 2.2.5 ABI uses typed Minecraft payload accessor, no weighted-bucket fallback.
- [ ] Run targeted tests, optional installed-JAR ABI checks, then full suite once. Report API signatures and test output for downstream integration.

### Task 2: Version 2 rules and transactional profile editing

**Files:** rules/RuleProfile.java, rules/ProfileStore.java; new rules/RuleCategory.java, RuleExplanation.java, ProfileDraft.java, FactSchema adapter if needed; rules tests.

**Interfaces:** Preserve existing RuleProfile.parse/match and Match.target until Task 4 updates consumers. Add categories(), explain(Map), canonical JSON, dynamic category IDs; expose editable validated draft via ProfileDraft. Consume FactSchema from Task 1.

- [ ] Add failing tests: sixth arbitrary category, priority tie order, max, unknown fact/operator/category rejection, UNKNOWN explanation, atomic save, invalid draft preserving active/file, invalid new selection clearing active, confined paths and explicit v1 migration/backup.
- [ ] Implement version 2 categories and ordered rules with 128 KiB/64 category/128 rule/16 predicate bounds. Starscript receives scalar snapshots only. Version 1 import preserves semantics and creates the five legacy categories; confirmation required before saving migration.
- [ ] Implement Validate/Preview, Apply, Save, Reload as separate operations. Save checked draft via same-directory temporary file and atomic move; reject traversal and symlink escape. Preserve original v1 backup without overwriting it.
- [ ] Run targeted tests and full suite. Publish exact editor APIs in report.

### Task 3: Shared runtime and Pokémon Discovery

**Files:** Discovery.java, discovery classes, hud/CobblemonDiscoveryHud.java, observation runtime/providers, gui/discovery Pokémon views; CobblemonAddon.java only for runtime registration.

**Interfaces:** Consume ObservationSnapshot/FactValue/PokemonFactReader. Produce a shared tick-budgeted runtime with scoped cached snapshots and selected-object inspection for Task 4. Preserve old stable module key, show Pokémon Discovery title.

- [ ] Add regression tests for world/connection reset, finite scan budget, removal of unloaded highlights, combined appearance/rarity filtering, configurable scan radius/feed limits and HUD settings preservation.
- [ ] Replace repeated per-label world scans with shared immutable snapshots; registry cache and deeper reads only for selected object. Unknown wild/private fields remain explicitly unavailable.
- [ ] Provide Meteor-themed list/detail inspector with search, sorting, Copy/provenance and bounded native portrait where supported. Use existing input-owning overlay infrastructure; inspect its API before extending it. Narrow view uses Back; do not force global HUD or reposition existing element.
- [ ] Run targeted tests and full suite. Report shared runtime interface for generic consumer.

### Task 4: Generic Discovery, profile editor and integration

**Files:** DungeonDiscovery.java, dungeon observation/render integration, gui/discovery editor views, profiles resources/README, addon README and gradle.properties.

**Interfaces:** Consume shared runtime snapshots and RuleProfile/ProfileDraft APIs. Replace runtime enum target dispatch with category IDs; legacy enum can remain only in migration/test compatibility helpers. Keep dungeon-discovery key, display Discovery.

- [ ] Add failing tests for profile-free observations, arbitrary categories controlling rendering, crosshair-to-rule draft, configuration/lifetime preservation and profile migration retaining colors/toggles.
- [ ] Build Meteor Observations/Rules/Categories/Profile views, typed field picker, predicate editing, Preview explanations and separate Validate/Apply/Save/Reload controls. New category must work without Java edits. No profile means inspector only, no automatic classification.
- [ ] Adapt render cache to category styles, eliminate hard-coded five target settings as runtime authority. Add explicitly opt-in version 2 Academy example marked unverified. Preserve existing v1 settings through confirmed migration.
- [ ] Run full tests and remapJar offline. Bump Cobblemon addon to 0.4.0, document controls, limits and untested live paths. Do not install.

## Verification

For each task: RED targeted test, GREEN targeted test, one full suite and independent task review. Final whole-change review checks contracts and scoped lifetime; run `./gradlew test remapJar --offline --console=plain`. Capture test counts and artifact SHA256. Do not claim live compatibility without a Minecraft session.

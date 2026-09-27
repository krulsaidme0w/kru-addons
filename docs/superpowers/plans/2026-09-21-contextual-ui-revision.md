# Contextual UI revision Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship a compact chart, a battle-only type matrix and contextual container/PC utilities.
**Architecture:** Keep OverlayHost and independent data adapters. Add contextual visibility and exclusive text input; compose the three panels on these shared contracts.
**Tech Stack:** Java 21, Minecraft 1.21.1, Meteor 0.5.8, Fabric Loom, JUnit 5.
**Spec:** `docs/superpowers/specs/2026-09-21-contextual-ui-revision-design.md` (approved).

## Global Constraints

- Official independent Meteor 0.5.8; Minecraft 1.21.1; Java 21.
- No modifications to ../cobbhelperv2, installed mods, or tools-addon.
- No Academy dependency, packet interception/queues, automated transfers or predictions.
- Theme/font from Meteor. Native screens stay alive under overlays; ownership of all consumed input is explicit.
- Unknown is not 1x; base-type reference is not current battle evidence.
- Do not stage/commit the inherited untracked tree. Filesystem baseline: `/tmp/kru-ui-revision.N6k4I6/src`.

## Task 1: Shared context, input and chart (controller)

Files: `gui/overlay/OverlayContent.java`, `OverlayHost.java`, `OverlayModule.java`, new `ContextVisibility.java`, `OverlayTooltip.java`; `mixin/OverlayKeyboardMixin.java`; `gui/TypeChartContent.java`; `TypeChart.java`; corresponding tests under `src/test/java/dev/krul/cobblemon/gui`.

Interfaces added to OverlayContent (defaults preserve existing content):
```java
default boolean contextual() { return false; }
default Object contextKey() { return null; }
default boolean textFocused() { return false; }
default void blur() {}
default boolean key(int key, int mods) { return key(key); }
default boolean charTyped(int codePoint, int mods) { return false; }
default java.util.List<String> tooltip(double x, double y) { return java.util.List.of(); }
default double defaultScale() { return 1.0; }
```

- [ ] RED: ContextVisibility tests catch reopening dismissed same context, staying visible after null, failure to reopen new context, manual toggle and session reset. Chart tooltip tests assert Fire against Grass=2, Electric against Ground=0, header roles, blank margins/outside. Tooltip placement tests keep bounds within viewport.
- [ ] GREEN: ContextVisibility owns context-key equality and manual visibility. Module refreshes after content tick and before hit/render; context reads must be cheap and current. Reset world/connection invalidates visibility before new tick. Host text owner precedes underlying-screen text checks/hotkeys, passes key repeats for editing but not repeated submission, routes `Keyboard.onChar`, releases consumed keys, blurs on outside click/close/collapse/context loss.
- [ ] GREEN: tooltip drawn once after all panels, in viewport GUI coordinates with no body scissor. New chart default .8; remove header/footer bands, preserve pinning and row/column highlights; reset settings provides explicit new default.
- [ ] Run focused tests then read changes against spec and native hook ABI.

## Task 2: Battle matrix (worker)

Files owned: `battle/matrix/` new package, `battle/matchup/CobblemonMatchupReader.java` only if needed, `BattleMatchups.java`; tests under `battle/matrix/`, reader regression tests if amended.
Consumes OverlayContent additions in Task 1. Produces BattleMatrixContent replacing BattleFactsContent in active module. No OverlayHost/Content edits in worker.

- [ ] RED: pure matrix test with independently expected Fire→Grass=2, Ice→Dragon/Ground=4, Normal→Ghost=0, Electric→Ground=0, dual neutral=1, unknown type=?, status move=—, dynamic/special semantics=?, base reference prefix ~, no fallback after known type change.
- [ ] GREEN: rows group known moves by own Pokémon; columns revealed enemies only. Reuse TypeEffectiveness, snapshot types/freshness and guarded native portraits, never MatchupCalculator best/risk. Tooltip describes reference-only math and freshness. No large facts footer. Reasonable fixed visible size, internal scroll for larger rosters. Context key is live battle ID, null outside battle. Refresh snapshot on tick not each render. Keep same module ID/binds.
- [ ] Run `./gradlew test --offline --tests '*BattleMatrix*' --console=plain`; ensure old reader tests stay green if touched. Report changed paths, red/green evidence, runtime caveats.

## Task 3: UI Utils adapters and command editor (worker)

Files owned: `pcui/` (retain old test compatibility or remove unused code deliberately), `CobblemonUiUtils.java`; tests in `pcui/`. No shared host edits; request interface adjustments from controller.
Consumes Task 1 OverlayContent input/context contracts. Module retains existing ID/settings. Adapter context must reflect current Screen plus handler/PC identity and connection (not screen title).

- [ ] RED: pure scoped-action tests reject replaced world/connection/screen/handler. Command editor tests exercise blank, /prefix normalization, single submission, cursor movement/selection/delete, paste filtering, blur, screen replacement and disconnect clearing. Use real production state classes, not source greps.
- [ ] GREEN: compact Meteor-styled command field with cursor/selection, Send, Copy title/context, Close, Close local (verified capability). Text editor uses Minecraft clipboard through a narrow injected boundary or native widget editing with custom Meteor painting. No Save/Load/Desync placeholders. Generic HandledScreen adapter and PC adapter share capability/action interface; actions re-read live current context before calling linked vanilla methods or verified PC closeNormally(false).
- [ ] GREEN: contextual current-screen key; change menu invalidates action target, preserves unsent draft only within same connection; no send on focus loss. Use `ChatUtils.sendPlayerMsg`/network API to send user commands once. No screen reopen, synthetic slot moves, packet queues or broad Cobblemon screen heuristics.
- [ ] Run focused tests, installed-PC ABI check, report evidence and limitations. Full Gradle suite serialized by controller.

## Task 4: Integration, review and artifact

Files: docs/cobblemon-overlays.md, docs/implementation-status.md, gradle.properties (0.3.0), tests for integrated hook routing if needed.

- [ ] Review task implementations, then independent final review focused on contextual lifecycle, stale actions, input leakage, factual matrix.
- [ ] Run full forced tests with COBBLEMON_RUNTIME_JAR pointing at installed pinned fork; `test remapJar --offline --rerun-tasks --console=plain`.
- [ ] Run distribution verification and fixture tests; document exact artifact/hash and manual live checklist. No installation.
- [ ] Report implemented scope, tests and untested live paths; Discovery stays explicitly pending.

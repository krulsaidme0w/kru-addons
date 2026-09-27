# Cobblemon overlays implementation plan

> Implement the approved design in the current independent addon workspace. Main owns integration; parallel workers own factual battle content and PC capabilities.

**Goal:** Three consistent, hotkey-driven floating panels over the existing game GUI, without predictive battle advice.

**Architecture:** One overlay host owns stacking, input capture, viewport geometry and persistence. Contents use a small Meteor-themed canvas. Thin client render/input hooks do not mutate native Cobblemon layouts. Reflection adapters expose only client-known facts.

**Tech stack:** Minecraft 1.21.1, Java 21, official Meteor 0.5.8, Fabric/Mixin.

**Spec:** `docs/superpowers/specs/2026-09-21-cobblemon-overlays-design.md`.

## Boundaries

- No Meteor fork, Academy dependency, installed-mod replacement or edits to cobbhelperv2.
- Packet tools and generic UI Utils are deferred, not deleted from the tools source tree.
- Discovery and Dungeon Discovery are unchanged in this stage.
- Existing repository is an unborn branch with inherited untracked files: do not create an implicit baseline commit or stage the user's entire tree.

## Tasks

- [x] Geometry/input: test viewport clamping, scale coordinate inversion and press/release ownership; implement `gui/overlay/OverlayGeometry`, `PointerCapture` and host consumers.
  - Example: a 400x300 panel in 200x150 viewport must shrink to fit, hit-test the rendered bounds, never pass an overlay-owned release to PC.
- [x] Shared host: `OverlayContent`, `OverlayCanvas`, `OverlayHost`, `OverlayModule`, thin render/mouse/keyboard mixins. Persist x/y/scale in Meteor module settings. Draw native/theme colors and Meteor text. Keep focused text widgets and hotkey editing out of bind handling. Uniform +/-/reset, drag, collapse, close.
- [x] Type Chart: `gui/TypeChartContent` retains TypeMatrixSelection and TypeEffectiveness; draw arrow with geometry and explicit attack/defence labels. `TypeChart` becomes persistent overlay module, not a replacement Screen.
- [x] Battle Facts: `battle/facts` consumes existing client snapshot reader, native portraits, factual rows and revealed details; test unknown versus current/last observed facts. Rewire `BattleMatchups` technical ID to factual panel, remove native battle mixin from active config.
- [x] PC utilities: `pcui` owns capability-probed PC adapter, immutable preview, scoped saved view; test connection/world invalidation. Register `CobblemonUiUtils`, no dependency on tools-addon.
- [x] Integration: run `./gradlew test remapJar --offline --console=plain`; validate JAR using `distribution/verify_artifacts.py`; independent review of input/lifecycle/facts risks and fix findings.
- [x] Handoff: document binds, actions and live checklist. Report only tests actually run; live PC/battle rendering requires in-game verification. No installation in this task.

## Outcome and remaining limits

Forced full run with `COBBLEMON_RUNTIME_JAR`: 300 tests passed, none skipped. Nine distribution tests passed. Native runtime behavior still requires the checklist in `docs/cobblemon-overlays.md`.

Review corrections: absolute transformed scissors (including portraits); scoped Meteor 2D text mode/model-view; sign/book text focus; hidden-HUD input suppression; real Cobblemon stat-stage ABI rather than multipliers; remappable ItemStack emptiness; independent native pose/layer/lighting cleanup.

PC Restore is intentionally an immutable facts view, not reattachment of a live storage screen. Desync remains disabled with a reason until stale native storage controls can be guarded. Summary/party adapters and Discovery redesign are deferred.

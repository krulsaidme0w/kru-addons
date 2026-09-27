# Historical implementation checkpoints

Archived evidence: versions, hashes, installation state and limitations below
apply to those checkpoints. See [current status](../implementation-status.md).

## Current revision: 0.3.3 overlay lifecycle fix (built; live acceptance pending)

- Overlay modules opt out of Meteor's automatic join/leave callback replay.
  A dismissed Type Chart is no longer reopened by `onActivate()` during rejoining;
  saved position/scale/collapse values are not changed. Manual show/toggle still works.
- OverlayHost remains responsible for clearing world-bound content/contexts and
  preventing rendering/input outside a world. No stale PC/battle state is retained.
- Native lifecycle regression observed RED before the flag, GREEN afterward.
  Full forced offline test/remapJar: **444 passed, zero failures/errors/skips**.
  Artifact independence check passed; no live dimension transition executed.
- Artifact: `cobblemon-addon/build/libs/kru-cobblemon-0.3.3.jar` (not installed).
  SHA-256: `d0d306aab8288a86b29153a5de444b90e708e8b23a7b5cb88cfdf6274dbecb3d`.

## Historical checkpoint: 0.3.2 strict Pokémon matrix (built; live acceptance pending)

- Battle Matchups is a compact Pokémon × Pokémon grid, up to six rows and six
  columns, four known-move chips per intersection, native header portraits and
  full hover details. No move-row paging or separate tall fighter cards.
- Only explicit CURRENT type facts authorize multipliers; base/profile/last-observed
  types and standalone stored Tera metadata cannot fill missing values.
- Pinned native opponent projections expose no current defensive type set.
  Opponent multipliers therefore remain `?`, not the old approximate catalog result.
  This limitation is intentionally visible, not solved by an unverified fallback.
- Identity conflicts retain unknown roster slots; exact live roster UUID reveal
  can replace a stale disguise projection without adding an unbound fighter.
  Active moves require the valid unanswered request, not potentially stale
  Transform/Mimic profile moves.
- Forced unrestricted offline test/remapJar: **442 passed, zero failures/errors/skips**.
  Nine distribution tests and artifact independence verification passed.
- Artifact: `cobblemon-addon/build/libs/kru-cobblemon-0.3.2.jar`.
  SHA-256: `075f24e0c474507ccd97ec4156239d499ceaab37eb07055e05c92a9ace57ef73`.
- Geometry fixture inspected at 6×6 and 2×2; no native Minecraft rendering or
  live Illusion/Transform run performed. Final read-only code review passed
  with no actionable P0–P2 findings.
  This revision is not installed; Type Chart and UI Utils sources are unchanged.

## Historical checkpoint: 0.3.1 manual UI sessions (built; live acceptance pending)

- Close hides a verified native menu while retaining a live UI Utils session;
  Exit ends that session without disabling the Meteor module.
- PC 1st In / Out transfers one Pokémon in the current box context, with exact
  UUID/destination acknowledgement and no automatic retry.
- Opt-in Academy Breeding In / Out operates only on the current pen's left parent.
  No pen scanning, pair planning or breeding automation is included.
- Native server closure, replacement, disconnect and world changes invalidate
  retained sessions. Hidden screens are never restored with cached slots.
- Artifact target: `cobblemon-addon/build/libs/kru-cobblemon-0.3.1.jar`.
  This revision is not installed. Live Academy/manual-transfer acceptance is pending.
- Final unrestricted forced offline `test remapJar`: **418 tests, zero failures,
  errors or skips**, including installed-runtime ABI checks. Build successful in 10s.
  Distribution fixtures: **9 passed**; remapped artifact independence check passed.
  SHA-256: `fc7996187595fca0de35675b10b0a725a708ec793c6efd7b29f351bd40657b9b`.
- See [controls and runtime checks](cobblemon-overlays.md).

## Historical checkpoint: 0.3.0 contextual UI (built; live acceptance pending)

- Compact Type Chart, 80% new default, cursor tooltip instead of fixed explanatory strips.
- Contextual battle matrix and container/PC UI Utils using the shared Meteor-themed host.
- Commands Enter/Send with text ownership; current-context actions, no stale-screen restore.
- Manual dismissal persists until the next menu/battle, or explicit hotkey reopening.
- No packet tools, installed mod changes or legacy Meteor fork changes.
- Discovery enrichment/general profile editor remains pending as a separate result.
- Final integrated forced offline test/remapJar run: **346 tests, 0 failures/errors/skips**,
  including ABI checks against the installed Kru-instance Cobblemon JAR.
  Distribution fixture tests: **9 passed**; remapped artifact boundary verification passed.
  Final scoped review passed with no open findings; native/live rendering has not been executed.
  Artifact: `cobblemon-addon/build/libs/kru-cobblemon-0.3.0.jar`.
  SHA-256: `afcf6867c486e3f480fa378fd842131f4b556fecaed0763af8de5ea3ac4340d5`.

## Historical checkpoint: 0.2.0 Cobblemon overlays

The 2026-09-21 implementation supersedes the old WindowScreen/native-pane design below:

- Shared Meteor-themed overlay host over existing PC/battle/world screens, with action hotkeys, input ownership, drag/collapse/close and persisted uniform scale/position.
- Type Chart retains effectiveness/selection and uses geometric arrows.
- Battle Matchups retains its module ID for settings compatibility, but its active path is Battle Facts only. The old native-pane mixin is no longer registered; predictive classes are reference-only.
- New Cobblemon UI Utils handles PC facts/copy/chart/local close and read-only saved facts. Desync and restored storage interaction are not advertised as supported.
- Generic UI Utils and packet tools are deferred; no tools dependency or installed modpack changes.
- Live Minecraft acceptance is still pending. See [controls and current runtime checks](cobblemon-overlays.md).
- Final forced offline `test remapJar` run: **300 tests, 0 failures/errors/skips**, including read-only ABI checks against the installed Cobblemon JAR. Distribution fixture tests: **9 passed**. This does not execute native rendering or live PC actions.

The remaining sections record the **previous 0.1.0 checkpoint**, not the current overlay architecture.

## Verified

- Independent project roots and external official Meteor 0.5.8 dependency.
- Official Meteor artifact checksum recorded in distribution/dependencies.json.
- Tools addon compiles/remaps against official Meteor; its functional modules are registered.
- Nine artifact-boundary fixture tests pass; tools output JAR passes validation.
- Cobblemon addon builds/remaps against official Meteor with all four module registrations.
- Full unrestricted Cobblemon suite: **243 tests, zero failures/errors/skips** on 2026-09-20.
  This includes four read-only ABI checks against the installed
  `Abes-Hutts-Cobblemon-1.21.1.1.7.3-1.0.5.jar`, selected through `COBBLEMON_RUNTIME_JAR`.
- Both remapped addon JARs pass artifact-boundary validation.
- Dungeon rules are explicitly opt-in JSON; Academy mappings live only in an example preset.
- Starscript labels run over independent bounded fact snapshots. Regression tests cover
  invalid rules, reload, missing facts, unsafe numbers, bounded traversal and expression semantics.
- Type Chart uses a Meteor WindowScreen opened by its module/keybind.
- Final review regressions pass: world-scoped Discovery observations, preserved
  authoritative current battle types regardless of stored Tera metadata, and
  contained linkage errors in optional native capability checks.
- Tools addon now contains the live Packet Inspector window (ALL/C2S/S2C,
  substring search, hide movement/keepalive, clear, unpaged rows), bounded GUI
  packet delay, and HandledScreen UI controls through a narrow accessor mixin.

## Retained with limitations

- Battle Matchups calculations and the native Cobblemon pane were retained. Its
  panel now uses the same local Meteor visual contract as Type Chart.
  A missing render hook or unsupported native pane reports a diagnostic.
- Radiant/custom aspect meanings are not assumed; unavailable semantics remain unknown.
- Old click-only chest-open inference and synthetic remembered Guardians are not enabled.

## Not implemented / not verified

- Live tools interaction, Minecraft rendering and focus. Installed-fork ABI byte checks pass;
  official Cobblemon versions have not been validated. These are distinct claims.
- Installation into any user instance.
- PC Autosort: deliberately deferred.

Packet file export is not required and will not be added as part of this migration.

The KRU repository has not been committed or installed. Existing cobbhelperv2 changes
and the installed modpack were left untouched. See runtime-checklist.md for remaining
acceptance work; this is an implementation checkpoint, not a finished two-addon release.

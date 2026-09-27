# UI session and single-Pokémon actions Implementation Plan

> **For agentic workers:** Use test-driven development and scoped independent workers; controller integrates and reviews.

**Goal:** Keep UI Utils after local Close; Exit ends its session without disabling the module; manual PC and left daycare transfers affect exactly one Pokémon.

**Architecture:** A connection/world-scoped session retains only a live adapter target after explicitly detaching its screen. Native menu replacement, server closure, disconnect and Exit invalidate it. PC and opt-in Academy daycare adapters own bounded single-action transactions; the controller owns the session and UI.

**Tech Stack:** Java 21, Minecraft 1.21.1, independent Meteor 0.5.8, pinned Cobblemon runtime ABI.

**Spec:** User-approved conversation: Close hides without intentional unlink; Exit ends current session; PC 1st In deposits first occupied party slot into current box, PC 1st Out withdraws first occupied slot of current box; daycare left slot only, first party Pokémon, no all-in, no planner, no pen iteration. No assertion that a remote server must retain a connection.

## Global constraints

- Only cobblemon-addon changes; legacy cobbhelperv2 read-only. User explicitly authorizes narrow manual daycare operations, not migration of Breeding automation.
- No Meteor changes, Academy hard dependency, packet inspector/delay or installation side effects.
- Client observations and exact identities only. One request per manual action, wait for authoritative acknowledgement; unknown/capacity/mismatch disables actions with reason.
- No restoring cached slots/screens. A retained hidden session is valid only while native live target and connection evidence remain valid.
- Existing unborn branch means source snapshot instead of worktree/commits; do not stage the inherited tree.

### Task 1: controller — session lifetime and UI

Files: pcui/UiMenuSession.java and tests; MenuAccess, MenuAdapter, UiToolsContent; minimal lifecycle hooks; CobblemonUiUtils settings.

- [x] Write lifecycle tests: detach survives null screen, normal close does not; replacement/identity/world/connection change invalidates; Exit suppresses same native menu, next menu permits a new session; late server closure blocks detached actions.
- [x] Run focused tests and observe RED. Implement pure identity state machine and native adapter evidence.
- [x] Rename Close to local operation, replace Close local with Exit; retain command field with a live connection while detached; copy title remains read-only.
- [x] Add PC and daycare action rows only in matching context, busy/unknown feedback; no field hotkey leakage.
- [x] Run tests GREEN, inspect interaction hooks and bounded rendering.

### Task 2: worker — PC one-shot transfer

Own pcui/ManualPcActions.java, PC-specific helper classes/tests only.

Public API: `boolean supports(Object screen)`, `String request(Object screen, boolean intoPc, java.util.function.BooleanSupplier valid)`, `void tick()`, `boolean busy()`, `String status()`, `void reset()`.

The session supplies the verified original PC screen as an access handle, not a restored GUI. Read current ClientPC/party data, derive UUID/position and current box; never read stale slot widgets for mutation. Use verified native transfer packet ABI. `request` sends at most one transaction; tick only observes exact UUID destination or timeout. Supplied valid predicate must be checked immediately before sending and each pending observation. Empty/full/unavailable return explanation. No retry of semantic move.

- [x] Test first occupied source, current-box destination, full/empty, pending double-click, exact UUID ACK, invalidation and timeout.
- [x] Run RED; implement verified native adapter and pure planning/pending logic; run GREEN.
- [x] Document copied-reference provenance and ABI proof; report changed paths and command evidence.

### Task 3: worker — opt-in daycare left-slot one-shot

Own pcui/daycare/** and tests plus resource profile only. Public class `ManualDaycareActions`: `boolean supports(Object screen)`, `String request(Object screen, boolean insert, java.util.function.BooleanSupplier valid)`, `void tick()`, `boolean busy()`, `String status()`, `void reset()`, `boolean ownsScreen(Object screen)`. Provide a no-arg constructor. Controller enables profile via module setting and calls only when enabled.

Server mappings belong in bundled opt-in profile, not generic Cobblemon code. Inspect old LiveMeteorDaycareClient, parent layout, party selector and dialogue contracts. Operate left slot of currently observed pen only. Insert selects exact first party UUID and verifies removal plus role occupancy; Out verifies role empty plus exact observed return when possible. Never claim UUID from lossy card projection. A known profile may permit explicitly chosen visible left-card withdrawal with storage delta proof; ambiguous result remains pending/unknown, never repeat-click.

- [x] Test recognition mismatch, occupied/empty left role, first source, one request, native selection/confirmation transitions, cancellation on unrelated menu/world, no duplicate mutation and bounded timeout.
- [x] Run RED; implement adapters using native current handler even after local Close; follow only expected owned picker/confirmation transitions. No automatic /daycare, no pen selection, no breeding planner.
- [x] Run GREEN; report verified limitations rather than substitute placeholders.

### Task 4: integration, review, delivery

- [x] Review adapter/session edges together; run full forced tests and remapJar with pinned installed-runtime ABI enabled.
- [x] Verify independent artifact boundaries and distribution fixtures. Bump addon 0.3.1 and document controls, profile opt-in, live limitations.
- [x] Fresh code review of source-snapshot diff; fix material findings with failing regressions first.
- [x] Report actual results and instructions for Interact Key + title drag. Do not install without a fresh install request.

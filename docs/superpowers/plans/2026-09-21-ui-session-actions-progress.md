# Progress — plan: docs/superpowers/plans/2026-09-21-ui-session-actions.md

User approved concrete behavior in conversation; no further design question remains.

Preflight: Task1 consumes independent Task2/3 request/tick/reset/busy/status APIs. Task3 owns only expected picker/confirmation screen transitions; controller retains root identity and invalidates unrelated screens. Tasks2/3 do not write host/UI files. All tasks use same module context and do not mutate server without an explicit button press.

Ruling: use existing unborn build/independent-addons branch and filesystem baseline, no commit/worktree — no HEAD exists and inherited tree is untracked; preserve user state.
Ruling: narrow manual daycare controls supersede AGENTS prohibition only for these explicit user-requested operations; no breeding planner or automation migration.

Baseline: /tmp/kru-session-actions.NYcher/src; baseline Gradle test successful (up-to-date previous results). No inherited Discovery code modified.
Task1: in progress. UiMenuSession initial missing-class RED then 7-test GREEN; additional native replacement+closure revision regression RED then GREEN. UiSessionSignals missing-class RED then host-focused GREEN. Host tests use temporary init file only while workers have incomplete TDD sources; final suite will have no filter.
Task2: complete. Worker 01a0c59f-1a89-7402-8c60-af3e9620ae97: focused29/29 including3pinnedABI; reviewer 01a0c5aa-833b-7c43-ac08-067e346913c7 spec PASS / quality PASS, no findings. Correct native ABI is MovePartyPokemonToPCPacket / MovePCPokemonToPartyPacket, not generic TransferPokemonPacket.
Task3: worker 01a0c59f-1ad9-76d0-bb74-bb8a1c726593 in progress (daycare only).
Task4: pending.

Native finding: pinned ClosePCHandler ignores ClosePCPacket when currentScreen is not PCGUI. UiSessionSignals must observe that payload even for detached sessions; no packet cancellation, inspection UI, storage capture or delay.
Interfaces: daycare validity callback is fast identity/world/connection only (prevents recursion through ownsScreen); daycare adapter itself validates expected native transitions. PC callback also refreshes native session evidence before dispatch. Active daycare expected transitions may consume root-close notifications; its own bounded state proof remains required.

Task1 review finding P2: owned GAP could mask terminal server closure. Fix removes
blanket epoch reuse; only one container-close increment plus a new non-null verified
transition may be accepted. GAP/same-screen/multiple/PC-close invalidates. Fast callback
now also checks accepted epochs; daycare ownsScreen must not invoke that callback.
New lifecycle RED→GREEN; final host slice23 tests passes. Scoped re-review PASS:
no residual P2 or new breakage in amendments. Task1 complete.

Task3 review P2: a recognized dialogue with gradual text can have renderInput=false
before it becomes ready. Do not consume confirmation or abort during that bounded
wait. Worker is adding a RED→GREEN readiness regression and explicit readiness gate.
Task4: initial integrated forced offline test/remapJar succeeded (12s); final rebuild
after the readiness fix remains required. Nine distribution tests and artifact
independence verification passed. Fresh cross-flow integration review PASS from
01a0c5b7-accd-7b52-a3cd-9a4aa84ca645: no additional actionable P0–P2 findings.
That verdict explicitly excludes the known readiness fix, which receives its own
scoped re-review before the final full test/remap build.

Final build after readiness correction: unrestricted forced offline test/remapJar
successful in10s, 418tests/0failures/0errors/0skips. Nine distribution fixtures pass;
remapped 0.3.1 artifact independence check passes. SHA256
fc7996187595fca0de35675b10b0a725a708ec793c6efd7b29f351bd40657b9b.
No installed mods, legacy source or inherited Discovery changes modified.

Task3 scoped readiness re-review PASS (Planck): P2 addressed, no new breakage.
All four tasks complete; task-specific and final integration reviews have no open
material findings. Keep the unborn branch and untracked inherited tree as-is;
no merge/commit/installation authorized. Live runtime acceptance remains pending.

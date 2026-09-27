# SDD ledger — plan: docs/superpowers/plans/2026-09-21-contextual-ui-revision.md

Approved spec: contextual-ui-revision-design.md. Baseline source snapshot: /tmp/kru-ui-revision.N6k4I6/src.

## Preflight

| Tasks | Interface / check | Result |
|---|---|---|
| 1 / 2 | OverlayContent contextKey/tooltip | Defaults keep compilation stable; worker consumes additions only. |
| 1 / 3 | OverlayContent textFocused/key/charTyped/blur | Host owns raw input; worker owns field editing. |
| 2 / 3 | Disjoint battle/matrix and pcui packages | No shared writes. |
| 1 | Context tests + compact chart requirements | Test pure lifecycle and tooltip content/placement, native wiring reviewed separately. |
| 2 | Known moves versus enemy types | No reuse of predictive calculator; base reference explicitly marked. |
| 3 | Container commands and live identity | No stale restore or broad packet interception. |
| 4 | Full build vs live proof | Separate reports, no installed mod changes. |

Ruling: work in existing unborn feature branch with a source snapshot, no initial commit/worktree — preserve inherited untracked project — cost: filesystem diff review instead of commit diff.

Task 1: complete (filesystem baseline diff; reviewer spec+quality PASS after corrections). Review found ineligible activation stealing focus; fixed onActivate front condition. Added immediate null-context assertion and composed scale/title/scroll tooltip test. Tooltip wrapping green, scoped re-review PASS. No open findings.
Task 2: worker 01a0c4e3-633b-7230-a153-6d495b07dbdc implemented; initial 44 focused tests pass. Fixed grouped-row scroll end, pager overlap, last-observed prefix and live snapshot guard. Task reviewer 01a0c4fe-dff6-7241-95cb-eaf897455931 returned conditional spec PASS and quality PASS with minor follow-up: dynamic-move fix is the only integration gate; nominal type badges should also avoid implying a confirmed dynamic type.
Task 3: complete (filesystem baseline diff; reviewer spec+quality APPROVE after one fix round). Generic local-close overclaim and hardcoded input surface corrected; exact vanilla capability whitelist and theme token; testable PC unlink boolean boundary. No open findings. Temporary Gradle init script only used during intermediate focused tests, not full build.
Task 4: complete for source/build verification. Version 0.3.0 and controls docs updated. Final integrated forced test/remapJar passed 11s: 346 tests, 0 failures/errors/skips, installed Kru-instance ABI enabled. Distribution fixture tests 9/9 passed; independent artifact verification passed. Final scoped review PASS; live Minecraft acceptance remains pending.

No further scope rulings. No installed jars changed. Source/report diffs remain because unborn repository has no commit evidence.

## Final integration review

Reviewer 01a0c503-741e-7941-8027-72dc462f1f17: one Important finding,
reader SPECIAL_MOVES missing aurawheel/ragingbull, causing static-type numeric cells
for form-dependent moves. Verified upstream onModifyType implementations in
https://github.com/smogon/pokemon-showdown/blob/master/data/moves.ts .
Single final fix wave assigned to original battle worker; reader→matrix regressions required.
Narrow audit of the same upstream source also identified terrainpulse (terrain-dependent
type) and struggle (typeless despite Normal catalog type). Both added to this same
bounded fix wave, together with conservative unknown badges for special moves.

Controller additionally found pre-focus key repeat adopting the underlying press's
release. New OverlayKeysTest regression failed (4 tests / 1 failure); capture now
occurs only on GLFW_PRESS, focused tests green3s. Final reviewer incorporated and
closed that finding.

Final fix verification: worker focused reader/matrix suite 39/39 green after observed
red regressions. All four special IDs and conservative badges are covered; ordinary
Flamethrower remains numeric. Refreshed task-2-diff.txt. Final reviewer returned
Ready / PASS: original Important and key-repeat findings closed, no open findings
in the reviewed scope. Task 2's integration gate and minor badge follow-up are closed.

Final artifact: cobblemon-addon/build/libs/kru-cobblemon-0.3.0.jar
SHA-256: afcf6867c486e3f480fa378fd842131f4b556fecaed0763af8de5ea3ac4340d5

No installation, commits, Meteor changes or legacy project writes. Discovery remains
a separate pending result. Manual live checklist: docs/cobblemon-overlays.md.

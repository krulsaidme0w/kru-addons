# Task1 — retained UI session and controls

Implemented UiMenuSession identity lifecycle, UiSessionSignals lifecycle-only counters, Close-local/Exit buttons, retained command field, scoped PC/daycare request bindings and opt-in Academy setting. No screen restoration. World/connection/native target change, server PC close/unlink, generic container close signals, unrelated screens and Exit revoke old authorization. Cursor-only OverlayInteractionScreen is neutral. Daycare expected transitions delegate to its bounded adapter; supplied callback uses fast session/world/connection identity to avoid recursive ownsScreen calls. PC callback refreshes all live evidence at each boundary.

Changed files: UiToolsContent, UiMenuSession, UiSessionSignals, MenuAdapter, ContainerMenuAdapter, PcMenuAdapter, CobblemonUiUtils, OverlayHost (one public cursor-screen predicate), UiMenuSessionTest, UiSessionSignalsTest.

TDD: UiMenuSession missing-class RED -> 7 tests GREEN; replacement coinciding with lifecycle revision initially failed (8 tests,1failure), then fixed to admit new native target while suppressing revoked old frame. UiSessionSignals missing-class RED then focused GREEN. Native ClosePCHandler classbytes show it otherwise ignores ClosePCPacket if PCGUI is hidden; lifecycle observer is needed.

Final host-focused command:
`COBBLEMON_RUNTIME_JAR=<exact Kru-instance jar from plan> ./gradlew test --offline -I /tmp/kru-session-actions.NYcher/host-tests.gradle --console=plain`

Result: BUILD SUCCESSFUL 4s, 20 tests / 0 failures / 0 skipped; isolated output `/tmp/kru-session-actions.NYcher/host-build/test-results/test`. Includes existing UiRuntimeContractTest installed-jar ABI. Existing legacy BattleMatchupPanel/Gradle deprecation warnings unchanged. Full source compiled; test-source includes are temporary only to coexist with workers' TDD. Controller final full suite will not use exclusions.

No live Minecraft interaction/transfer was performed. Handler/session identity is local proof with explicit server-close invalidation, not a guarantee that a remote server retains access. Copy/title remains diagnostic; blocked actions explain failure and no semantic retry occurs.

## Review fix: closure versus explicit handoff

Host reviewer found blanket old-revision reuse during an owned daycare step could
mask a terminal close while in GAP. Removed that reuse. The observed revision is
always supplied; a single container-only revision may accompany a newly observed,
non-null, adapter-proven transition screen. Same-screen/GAP/multiple-close/PC-close
events revoke authorization. Callback checks accepted epochs immediately before
pending mutations. `ownsScreen` is local observation only, not a call back into
the controller predicate, avoiding both recursion and handoff-grant deadlock.

Three new lifecycle regressions first failed compilation against missing explicit
handoff API, then passed. Host-focused suite after the fix: 23 tests, zero failures
or skips, including the pinned native ABI. Worker owns its complementary predicate
regression and method adjustment. Full integration follows without init filters.

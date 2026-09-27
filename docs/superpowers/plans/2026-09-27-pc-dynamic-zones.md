# PC dynamic zones implementation plan

**Goal:** One preview/apply builds named multi-box groups across the PC, automatically routing SHINIES and SPECIAL and allocating capacity.
**Architecture:** Pure collection planner produces an exact target snapshot after the existing fixed-box layout phase. Runtime moves to the frozen target, then renames with exact acknowledgements. Overlay shows group navigation and detailed destination boxes.
**Spec:** User-approved conversation design, amended with automatic SHINIES/SPECIAL routing and growth.

## Constraints and decisions

- KRU only; do not install or commit unrelated existing changes.
- MAIN, UTILITY, TRADE and BREEDING contents remain untouched after any explicitly previewed whole-box reorder. BREEDING is box 6.
- First six boxes are role anchors. SHINIES wins over SPECIAL; SPECIAL includes legendary, mythical, ultra_beast, paradox, restricted from authoritative species facts.
- Additional SHINIES/SPECIAL boxes are allocated after box 6. Preserve existing dynamic assignments when capacity allows; allocate more existing boxes when required. Never fabricate server capacity.
- User-pinned boxes cannot move, rename or serve as scratch. Insufficient capacity blocks before mutation.
- Default collection mode covers all candidates. Legacy filtered/current-box sorting remains separate; filters must never silently exclude collection members.
- Preview has no mutations. Execution is one request then authoritative ACK, no release/party changes. Renames only after positional completion.
- Current dirty working tree contains earlier user work; preserve it rather than moving or resetting it.

## Tasks

- [ ] Pure planner (separate implementation owner): create PcCollectionPlanner with Plan(result, protectedBoxes, groups), Group(key,label,boxes,count). Tests first for >30 shiny/special, precedence, protection, unknown types, capacity, stable reuse, idempotence. API: plan(PcOrganizerSnapshot projected, Set<Integer> explicitPinned).
- [ ] Main: integrate collection preparation and third rename phase into existing run. Regression tests for exact final snapshot, one request/tick, rename ACK, external changes, no preview requests. Retain existing prepare(...) for manual sort.
- [ ] Main: populate intrinsic class from existing species resolver; block collection on unknown appearance/class facts rather than treating missing as ordinary. Test adapter conversion.
- [ ] Main: simple default collection UI, selectable group navigation, readable preview counts/slots/names. Native PC navigation is local only, verify ABI. Keep manual sort available explicitly.
- [ ] Full offline tests/remapJar and read-only review; document limits and report no live validation/install.

## Verification

Run targeted red/green tests from cobblemon-addon, then `./gradlew test remapJar --offline`.
Main and planner owner use disjoint source/test files. Final review checks protected identities, unknown facts, stable naming, frozen target execution and metadata ACKs.

## Completion record

Implemented allocator, collection preparation, three-phase execution, live class reader,
group navigation and exact box preview. Existing manual sort remains available.
Pure allocation, reader, grouping and execution tests cover the planned edge cases.
Final review found missing role-move counts, obsolete preview after stop/block and
secondary-type promotion when primary is unreadable; all three corrected. The
primary-type regression was observed red then green. Stop/navigation UI paths
remain untested in a live client.

Final `./gradlew test remapJar --offline`: successful, 593 tests discovered,
576 passed, 17 skipped, zero failures/errors. `git diff --check` clean.
Installed JAR SHA-256 remains
`2a438dfc7126c55bcf4737fe3f91bfdfaafbc731efa2d0f289fbaf8ed41f5077`;
no installation, server actions or commits performed.

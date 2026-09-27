# Implementation status

## GitHub preparation — 2026-09-27

- **KRU Cobblemon 0.3.3:** Discovery, Dungeon Discovery, Type Chart, Battle
  Matchups, UI Utils and PC Organizer.
- **KRU Tools 0.2.0:** selective Packet Delay; no dependency on Cobblemon.

Academy profiles and the collection PC layout remain product defaults. Local
instance paths and saved personal configuration are excluded. Optional native
ABI checks require explicitly supplied JARs; skipped tests do not prove runtime
compatibility. The public build does not require a Minecraft installation.

CI builds both projects and uploads binaries, sources, checksums and reports.
Addon-specific tags create draft releases only after both builds pass. Publishing
the draft remains a maintainer action.

## Release acceptance still required

- Validate the intended Academy/Cobblemon runtime, including optional ABI tests
  and the [in-game checklist](runtime-checklist.md).
- PC Organizer: exercise protected zones, full collections, capacity changes,
  interrupted transfers and reconnects. Full-profile reads every active tick
  remain a performance follow-up; actual frame cost needs measurement.
- Preserve unavailable sorting facts instead of treating missing level/IV as
  zero. This static audit finding is not fixed by repository preparation.
- UI packet queue: verify respawn/player replacement invalidates old requests;
  current connection/world scope needs a player-lifecycle follow-up.
- Cobblemon compatibility has no tested version range yet. Compilation cannot
  prove reflective runtime mutation APIs remain compatible.

Build/release workflows do not modify a Minecraft instance.
[Historical reports](archive/implementation-status-pre-release.md) are retained
as development evidence, not current release guarantees.

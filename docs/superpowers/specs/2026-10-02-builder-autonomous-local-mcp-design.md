# Builder automatic local MCP design

## Intent

Make Builder a small local Minecraft data bridge for Codex: the player enters
selection bounds, Builder scans the loaded world automatically, and the local
MCP companion can inspect the exact snapshot, block catalog and useful item
metadata. Keep the in-game UI to bounds and a clear live status. Plans remain
validated previews; this change does not place or remove blocks.

## Current implementation

- `BuilderModule` starts an HTTP bridge on IPv4 loopback when activated.
- The player must Apply bounds and press Scan/Rescan.
- MCP must first request a pairing challenge; the player approves it in-game;
  the companion then receives a bearer token.
- MCP exposes paged block namespaces/search/details, with actual Minecraft
  inventory-rendered block thumbnails, but no general item catalog.
- Snapshot reads are bounded and block mutations are not exposed.

## Proposed behavior

### Minimal Builder workflow

1. Activating Builder starts its local bridge and MCP-readable services.
2. The player edits six bounds coordinates. A complete, valid bounds value is
   persisted and automatically schedules a scan; no Apply, Clear, Scan or
   Rescan controls are required. Invalid/incomplete bounds do not scan.
3. Scanning runs incrementally on client ticks with the existing per-tick work
   bound. If the player joins/rejoins or changes world/dimension, stored bounds
   for that context are restored and automatically rescanned. A changed bounds
   value invalidates old snapshot and preview evidence immediately.
4. The UI shows coordinates, dimensions/cell count, scan progress/result,
   bridge status and useful errors. No pairing prompt or scan buttons are shown.
   Existing generic window close/collapse behavior may remain.
5. The MCP process remains launched by the Codex MCP configuration. It connects
   to the already-running Builder bridge automatically; there is no extra
   in-game start/approval step.

### Local connection and trust boundary

- Remove challenge/token pairing state and the `pair_local_minecraft` tool.
- Keep the HTTP bridge bound to `127.0.0.1` only and reject non-loopback peers.
- MCP data routes accept local requests without a pairing bearer token. This is
  intentional: access is granted to processes on the same machine. Do not bind
  to LAN interfaces or add remote access. Show the loopback-only status clearly.
- Keep request size limits, bounded page sizes, strict request validation,
  client-thread scheduling, timeouts and read-only world access.

### World and registry data

- Keep existing paged block namespace/search/describe APIs and exact state data.
- Add a bounded, paginated item namespace/search/describe flow based on the
  active client item registry. Description includes exact registry ID, visible
  name, relevant item metadata and a Minecraft-rendered icon when available.
- Ensure block entries retain their placement-item mapping and existing actual
  Minecraft-rendered thumbnails. Do not serialize the full registry into a
  single MCP result.
- Any unavailable icon or unsupported metadata is reported explicitly; no
  appearance or behavior is inferred from an ID.
- Snapshot and registry tools expose only client-visible data. No commands,
  inventory changes, movement, or world edits are added.

### Plans and preview

- Preserve strict staged plan inspection against the exact current scan.
- Preserve plan archive/reinspection and explicit user approval before publishing
  an in-game hologram preview.
- Publishing remains preview-only. No place/break operation is added.

## Error and lifecycle handling

- No world, invalid bounds, oversized selection, unavailable chunk data,
  disconnect, failed HTTP bind, and stale snapshot are distinct visible status
  states; none silently reuses an incompatible snapshot.
- Repeated identical valid coordinates do not continually restart an active or
  completed scan. A new scan occurs only when bounds or world context changes,
  or when the previous scan failed and can be retried by correcting/reapplying
  bounds or rejoining. (There is no manual Scan control.)
- Bridge lifecycle remains tied to Builder module activation/deactivation.
- An interrupted/partial scan is never served as a complete snapshot.

## Compatibility and migration

- Preserve the selection bound store format and existing saved world-specific
  coordinates.
- Existing external clients that used bearer pairing must be updated to the
  loopback-only unauthenticated local protocol. MCP companion is updated in the
  same change.
- Respect the existing repo rule that Builder stays isolated from the
  Cobblemon addon and is read-only with respect to the world. This design
  explicitly supersedes the old manual-scan and explicit-pairing requirements.

## Validation

- Unit tests prove automatic scan scheduling on valid bounds and context change,
  no scan on invalid bounds, no duplicate scan on stable ticks, and stale data
  invalidation.
- Bridge tests prove a loopback request succeeds without a bearer token, a
  non-loopback peer is rejected, and world-mutating methods remain unavailable.
- MCP tests prove the pairing tool is absent and the item catalog tools have
  bounded schemas and paginated results.
- Item catalog tests prove exact-ID lookup, paging/search, missing-icon handling
  and icon output constraints.
- Full addon and MCP test suites/builds run. Live Minecraft behavior is reported
  separately and is not claimed unless actually exercised in-game.

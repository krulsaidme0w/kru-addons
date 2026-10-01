# Builder selection-only redesign

## Goal

Make the Minecraft Builder module a small, predictable world-selection and MCP
bridge companion. Build planning and dialogue happen in the external Codex/MCP
workflow, not in an embedded chat client. Activating Builder must not launch
Codex, authenticate, restore an old project, or transmit anything.

## User experience

- The Builder module opens its selection window when enabled; a keybind can
  reopen it later.
- The window contains only selection bounds, Apply/Clear, explicit Scan/Rescan,
  scan progress/result, the world-selection overlay toggle, and the local MCP
  pairing status/actions.
- Bounds persist per existing world context. Scanning remains bounded,
  client-visible, read-only, and subject to the current cell limit.
- The MCP bridge starts locally while Builder is active. A new pairing request
  is shown in the window and is not approved implicitly. The player may allow or
  reject it; an existing approval can be revoked.
- Errors and scan progress remain visible in this small window and in the game
  log. They do not bring back chat, sign-in, or request controls.
- The screen does not display an embedded conversation, Codex status, prompt,
  images, old plan history, or project reopen controls.

## Runtime boundaries

Remove the in-addon Codex CLI client/profile management, chat controller and
conversation archive, legacy project migration/reopen workflow, prompt/image
attachment workflow, and UI-only plan acceptance/history behavior. Activation
only initializes the read-only scanner and local MCP bridge. Remove obsolete
Builder settings and keybinds for Codex executable, chat submission, and
conversation opening; retain a selection-window reopen keybind if useful.

Keep the bounded world scanner, per-world bounds persistence, block catalog,
MCP pairing and data source, validated MCP plan intake, preview/overlay, and
MCP plan history/viewer. Keep shared plan schemas, state models, and validators
where the MCP server and preview still depend on them. Never add block placement,
movement, server commands, implicit pairing, or cloud transmission to activation.

The root and addon documentation must no longer describe the removed embedded
Codex/chat workflow as supported. Update Builder-specific instructions to
describe the selection-and-MCP contract. In particular, the Builder-specific
AGENTS guidance must be aligned with this design while leaving unrelated addon
boundaries intact.

## Old on-disk data

Delete only legacy Builder-owned conversation archives, project snapshots, and
image attachments under the confirmed Minecraft run directory's
`kru-builder/conversations`, `kru-builder/projects`, and
`kru-builder/attachments` paths. Before deletion, resolve and print the exact
paths and inventory their contents; do not follow symlinks or broaden the
target. If a target is absent, report that. Do not delete or alter
`kru-builder/codex-profile`, Minecraft saves/configuration, MCP plan history,
other mods' data, or any other files. Source-code removal and runtime-data
cleanup are separate steps; the latter happens only after the target has been
verified against the agreed paths.

## Data flow and failures

1. Module activation binds the current client world/session and restores bounds
   only for the matching world context.
2. The user enters or marks bounds, applies them, and explicitly starts a scan.
3. The scanner reports progress, rejects unknown/unloaded cells, and publishes a
   complete immutable snapshot to the local bridge only when finished.
4. The external MCP client reads that snapshot/catalog, authors and validates a
   plan, then may publish a preview through the paired local bridge.
5. The in-game module renders the preview, but never executes it.

World changes invalidate snapshots and previews. Pairing remains session-scoped
and bound to the current world identity as currently implemented. Bridge startup,
scan, invalid bounds, and pairing failures are explicit in the UI/log; none may
silently fall back to the old Codex client.

## Verification

- Unit/UI tests prove the selection window exposes the minimal controls and
  updates scan/pairing/error status without a conversation/controller.
- Module lifecycle tests or focused contracts prove activation does not create
  a Codex process, load an archive, migrate a project, or transmit data.
- Existing scanner, bounds-context, pairing, bridge, block-catalog, plan
  validation, preview, and MCP history tests continue to pass.
- Build both the Builder addon and MCP server. Inspect the built addon sources or
  dependency graph for removed Codex/chat references.
- For runtime cleanup, test the cleanup procedure against temporary fixture
  directories first. Verify it removes only the three agreed Builder-owned
  folders and preserves `codex-profile` and unrelated sibling files.
- Do not install or replace a mod during implementation; report live Minecraft
  behavior as unverified unless separately requested and tested.

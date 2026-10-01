# Builder local MCP world bridge and block catalog

## Status

Draft for user review. This specification describes architecture and behavior;
it does not authorize implementation until the user approves this document.

## Goal

Replace Builder's embedded Codex chat with a local Codex workflow that can
inspect the running Minecraft client, understand the selected build region and
the installed modded block registry, and return a validated, staged block plan.
The user reviews the plan in a local 3D viewer and/or in Minecraft's hologram
overlay. The system is a planning and inspection tool: it does not place or
break blocks, execute commands, or move the player.

The Minecraft client is authoritative for world state, block IDs, block-state
properties, modded model appearance, and screenshots. Codex conversations
remain in Codex's native history. A local archive stores plans and previews,
not a duplicate chat transcript.

## Architecture

Three local components have narrow responsibilities:

1. **Minecraft bridge (Fabric addon).** Reads only the player's explicitly
   selected bounds and client-loaded chunks. It exports bounded world snapshots,
   registry metadata, exact block-state descriptions, and on-demand images
   rendered by Minecraft. It validates incoming plans against the live registry
   and selected bounds, then publishes submitted plans to the
   existing Builder overlay. It never changes world blocks or player movement.
2. **Local MCP companion.** A Codex-launched stdio MCP process exposes the
   Minecraft bridge as a small set of typed tools, returns structured data and
   image content to Codex, and hosts the companion viewer/API on loopback. It
   performs search, paging, request validation, plan archival, and bridge
   lifecycle handling; it is not a second agent or chat UI.
3. **Local plan viewer and project skill package.** A browser UI shows the
   selected region, current snapshot, proposed changes, stages, and saved plan
   revisions. A repo-scoped Codex plugin package supplies MCP configuration
   and project-specific build-planning skills. It must not modify the user's
   global Codex configuration, login, or skills directory automatically.

The MCP companion starts when Codex launches the project plugin. It connects to
the Minecraft bridge over a loopback-only authenticated channel. The bridge
requires a one-time user pairing when a new local companion connects; after
pairing, routine read-only tool calls and plan previews do not require repeated
approval. The bridge and viewer bind only to loopback. Pairing tokens are
instance-local, are not placed in prompts or logs, and can be revoked in
Builder settings.

The Minecraft MCP Fabric project is a design reference, not a dependency: its
currently documented game-version matrix does not include this project's
Minecraft 1.21.1 target, and its broad mutation tools exceed this product's
read-only-plus-plan-preview scope.

## Block catalog

The catalog is built locally from the active Minecraft client registry and
keyed by a fingerprint of Minecraft version, loader, installed mod IDs and
versions, and active resource-pack identities. A changed fingerprint marks the
catalog stale and refreshes it incrementally without blocking render ticks.

Each catalog entry includes, when exposed by Minecraft:

- exact namespaced block ID, localized label, owning namespace/mod, and item
  counterpart if one exists;
- block-state property names and all legal values, plus default state;
- block and item tags, recipe references where available, map color, light,
  fluid status, collision/outline shape summaries, full-cube/occlusion flags,
  and whether the block has block-entity data;
- a cached default-state preview rendered from the live client model, with
  additional previews generated on demand for requested property combinations.

The compact catalog is stored as a versioned JSON snapshot with an in-memory
search index; rendered PNG previews are cached as separate files. This avoids
requiring a database runtime in either Minecraft or the MCP companion. It is
not sent to Codex wholesale. MCP tools page namespaces and return at most a
small bounded set of search matches; `describe_block` returns exact metadata
and one image. Preview cache keys include the registry/resource-pack
fingerprint and exact block-state properties. Missing models and rendering
failures are explicit.

Private block-entity contents, inventories, arbitrary NBT, and data outside
the selected bounds are not included in catalog or snapshot results. The
system may report a block-entity type and that special data exists so the agent
can avoid unsafe replacements.

## World inspection tools

The initial MCP surface is intentionally small and read-only except for
submitting a plan to the in-game preview:

- `minecraft_status`: connection state, world/dimension identity, selection,
  snapshot revision, and loaded/unknown counts.
- `read_selection`: paginated or slice-bounded exact block-state snapshot;
  includes relative and absolute coordinates, stable state properties, and
  explicit unknown/unloaded cells. Requests cannot exceed the configured cell
  limit or current selection.
- `capture_view`: on-demand PNG of the current Minecraft client view. It does
  not rotate the camera, move the player, or synthesize unobserved viewpoints.
- `list_block_mods`, `search_blocks`, `describe_block`: paginated local
  registry discovery and exact block-state/image inspection.
- `inspect_plan`: strict schema, bounds, registry, precondition, size, and
  conflict validation; returns a concise report and a plan image to Codex.
- `publish_plan`: sends a successfully inspected plan into Builder's preview
  overlay and local plan archive. It does not modify the world.
- `get_plan_status`: reports current preview lifecycle and whether the live
  snapshot has become stale relative to plan preconditions.

Tool metadata and server instructions explain ordering: read current selection,
search/inspect needed blocks, inspect a complete candidate plan, then publish
it. Data is on-demand and bounded so a large modpack or build region does not
flood context.

## Plan contract

Plans are versioned JSON with a palette of exact block IDs and properties,
ordered stages, relative coordinates anchored at the selection minimum, and
explicit operations:

- `PLACE`: the target cell must be known air in the referenced snapshot;
- `REPLACE`: the plan names the exact expected current block state and the
  exact replacement state;
- `REMOVE`: the plan names the exact expected current state and makes the
  intended empty result explicit.

Every operation is checked against the active bounds, snapshot revision,
registry properties, configured volume limits, and block-entity restrictions.
Unknown cells, invalid states, out-of-bounds positions, conflicting stage
assignments, and unsupported block-entity/NBT changes fail closed with exact
cell-level diagnostics. Plan stages compose deterministically into a final
assignment. Publishing makes the plan visible but does not imply approval to
execute it; execution is out of scope.

## Visual feedback and history

The local viewer is a separate loopback web page, not an assumed Codex-embedded
MCP App. It displays a rotatable 3D selection snapshot, plan changes overlaid
with distinct PLACE/REPLACE/REMOVE colors, stage/layer controls, exact block
names on hover, and plan validation warnings. It uses actual game-rendered
block/model previews wherever possible. If exact modded geometry cannot be
reconstructed outside Minecraft, it must label fallback/icon rendering rather
than imply photorealistic accuracy. Builder's in-game overlay remains the
authoritative view of the proposal in the actual world.

Codex remains the source of truth for conversations and their history. The
viewer keeps a local, versioned archive of plans, validation reports,
associated region snapshot metadata, screenshots/previews, timestamps, and an
optional user-supplied conversation label. It does not claim to enumerate or
mirror Codex's private conversation store when the client exposes no such API.
Selecting an archived plan opens it for inspection only; it never silently
publishes it into a different world or selection.

## Skills and Codex setup

The repo-scoped Builder plugin contains the local MCP server configuration and
skills for world inspection, modded-block research, tasteful composition, and
the strict plan schema. Setup is explicit and project-local. It does not copy
the user's global Codex profile, credentials, `config.toml`, or existing
skills. Codex tool output carries exact registry facts and rendered images;
the skill instructs the agent to research blocks incrementally, make
composition choices, inspect its proposed plan, and revise only in response to
validation or visual feedback.

## Privacy and safety

- No public listener, tunnel, cloud relay, arbitrary file read, shell tool, or
  arbitrary Minecraft command is exposed by the bridge.
- Snapshot and screenshot calls are explicit tool requests and are limited to
  the selected area/current view. They may be sent to the configured model
  provider, which is disclosed in the skill/setup instructions.
- All MCP arguments are untrusted and validated. Authentication, bounds,
  snapshot revisions, and plan operations are checked locally at the bridge.
- Read-only tools are annotated as read-only. `publish_plan` is labeled as a
  local preview action, with no block mutation capability in its code path.
- Disconnect, world change, dimension change, selection change, stale snapshot,
  renderer failure, or catalog fingerprint change is surfaced explicitly;
  stale plans cannot be published as current.

## Rollout and acceptance criteria

Implementation is staged so the existing Builder can remain available until
the replacement path works:

1. MCP/bridge contract and security; `minecraft_status`, bounded snapshot, and
   screenshot tools work end-to-end against a running client.
2. Fingerprinted block catalog, paging/search, exact state descriptions, and
   live-rendered block previews work for vanilla and representative modded
   blocks, including stairs, slabs, transparent blocks, and directional
   blocks.
3. Strict staged `PLACE`/`REPLACE`/`REMOVE` plan schema validates against live
   data and publishes only a preview; conflict, stale-world, unsupported
   block-entity, and out-of-bounds tests fail closed.
4. Local viewer renders selection plus plan, supports layers and history of
   plan versions, and makes fallback model fidelity visible.
5. Repo plugin/skills setup starts MCP for a trusted project without touching
   global Codex config; Codex can call tools, receive image results, and follow
   the plan workflow.
6. The old embedded chat can be removed only after the new flow passes the
   live-game acceptance checklist. No installation or activation is performed
   without the user's explicit request.

Automated acceptance includes unit/contract tests for schemas, paging,
fingerprints, exact-state round trips, diff composition, stale revisions,
bridge authentication, path/size limits, and viewer data. Live acceptance
uses the user's Minecraft 1.21.1 Fabric/Cobblemon modpack and representative
modded blocks. It confirms current-world image visibility to Codex, plan image
visibility, correct replacement preconditions, history persistence, and zero
world mutations.

## Explicit non-goals

- Automatic building, block breaking, movement, commands, inventory edits, or
  world/server administration.
- Sending the entire world, every block asset, or all mod data into model
  context at once.
- Duplicating Codex chat history or modifying global Codex identity/config.
- Guaranteeing exact out-of-game geometry for arbitrary custom/dynamic modded
  models; Minecraft's own renderer is authoritative.
- Installing the addon or local Codex plugin as part of implementation unless
  separately requested.

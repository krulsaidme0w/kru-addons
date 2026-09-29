# KRU Builder: searchable block catalog and visual previews

Date: 2026-09-30. Status: design proposed for review.
Branch: `feat/builder-agent`.

## Goal

Help the Builder agent make useful build proposals with blocks actually present
in the player's loaded Minecraft instance, including modded blocks, while
keeping each model request small. The player should be able to trust that a
suggested registry ID exists and inspect a visual preview of a particular block.

This feature supplements the bounded world scan; it does not replace it. The
agent may propose a plan, but only local validation can turn it into a preview,
and the player remains responsible for placing blocks manually.

## User flow

1. The player asks for a build or a revision.
2. When the agent needs a block it does not know, it asks the local catalog for
   mod namespaces/counts, then searches by mod and/or text in bounded pages.
3. Search results contain short names, exact registry IDs, and a hint to request
   details for a chosen result; search never attaches a page of images.
4. The agent requests details for one exact ID. Builder returns the ID, valid
   block-state properties/values and one compact rendered item preview, using
   the active resource packs. This preview shows the block's inventory/model
   appearance; it is not a promise of every in-world orientation or behavior.
5. The agent may then use that ID/state in its structured proposal. Existing
   local registry, selection-bound, operation-count and world-state validation
   remains authoritative. Missing/invalid blocks fail validation as before.

No catalog query runs automatically on opening Builder or sending a prompt.
Only requested result pages and individual detail previews enter the model
context. Current world images remain explicitly captured by the player.

## Architecture

Use the Codex App Server's experimental `dynamicTools` interface on each Builder
thread, with a small fixed allowlist and client-executed calls. This avoids
starting a separate MCP service and avoids enabling Codex's general MCP, shell,
filesystem, network, plugin, or external integrations. The App Server documents
the `thread/start.dynamicTools` plus `item/tool/call` request/response flow as
experimental; the currently pinned CLI must be proven compatible before this
is enabled. See [Codex App Server dynamic tools](https://developers.openai.com/codex/app-server).

The client will handle only the exact registered tool names and validate every
argument against strict bounds before touching Minecraft data. Unknown method,
tool name, malformed arguments, wrong thread/turn, cancelled request, or
oversized response returns a bounded error; all other server-initiated requests
continue to be rejected. Tool execution is dispatched to the Minecraft client
thread for registry/localized-name/render access, then converted to immutable,
size-limited results off-thread as needed. It must never inspect the world,
containers, block-entity NBT, files, or arbitrary game state.

## Tool contract

- `list_block_mods(page)`: paginated namespace and block count, with optional
  display name from Fabric metadata when available. Stable namespace is the
  identity; missing metadata is not an error.
- `search_blocks(query, mod, page)`: search registry IDs and available localized
  block names; optional namespace filter; fixed page size of at most 20 results.
  Empty query is allowed only with an explicit mod filter. Results include
  stable IDs and concise labels, not state combinations or images.
- `describe_block(id)`: exact registry lookup only; returns valid state property
  names and allowed values plus exactly one preview image for the block's item
  model rendered with the active resource packs. It never accepts a path or URL.

Page bounds, query length, page number, result byte size, image dimensions, and
calls per turn are capped in code. Bad or unknown inputs produce a clear
tool-level error rather than a guessed match. The index is derived from the
current client registry and does not persist or transmit until queried.

## Image and privacy behavior

Render the block's item/model preview locally from Minecraft's loaded client
assets; do not fabricate a generic cube or send a world screenshot as a block
preview. Return the image as an App Server-supported image content item tied to
the `describe_block` tool result, plus a short text label/ID. No disk path,
resource-pack contents, or complete texture atlas is exposed to Codex. Keep the
preview small (target 128x128 PNG) and within existing attachment/input byte
limits. If the pinned App Server/model combination cannot accept image content
from a dynamic-tool result, report that incompatibility and do not silently
substitute an unrelated image or broaden filesystem access; settle the fallback
before enabling the feature.

Catalog search and requested previews are transmitted to the configured model
provider as part of the conversation. They contain only public registry IDs,
localized labels/properties and the rendered item preview, not inventory,
player data, world context, or resource files. Existing Builder send/privacy
behavior applies.

## Failure and compatibility

- If the pinned CLI does not advertise/accept the experimental tool contract,
  Builder fails closed with a clear incompatible status; no tool requests are
  executed by a permissive fallback.
- If the model requests unknown IDs, unavailable translations, invalid pages,
  or unsupported properties, return a narrow explanation; local plan validation
  remains the final authority.
- Resource reload or disconnect invalidates transient icon/render work. Never
  use stale world context to answer a catalog-only tool.
- Tool calls are read-only and do not need an in-game approval prompt; their
  names and descriptions must say that they inspect only the local block
  registry and return requested details.

## Acceptance and tests

- Unit tests for mod/page bounds, stable namespace filtering, text/ID search,
  unknown IDs, valid state-property output, malformed/oversized inputs and
  allowlist rejection of every non-catalog server request.
- App Server protocol tests for tool registration, exact dispatch, concurrency,
  cancellation/stale turn handling, bounded errors, and unsupported pinned CLI
  behavior using synthetic process frames (no account/model request).
- Image tests verify a single decodable PNG preview, bounded dimensions/bytes,
  selected resource-pack texture path where test fixtures permit, and no path or
  world screenshot leakage. Validate actual image content-item compatibility
  against the pinned CLI/model before runtime enablement.
- Regression tests keep shell, filesystem, network, MCP, plugins and all
  unrecognized server requests unavailable; proposal validation still rejects
  nonexistent IDs and out-of-bounds states.
- Live check in a disposable game instance: search a vanilla block, search a
  block from an installed mod, request one visual preview, and confirm the
  resulting plan validates. No automatic installation or world modification.

## Scope exclusions

No full block dump in prompts; no image per search result; no remote catalog,
downloads, automatic placement, arbitrary code execution, shell/file tools,
or world-wide scan. A visual gallery/UI for the user is a separate feature.

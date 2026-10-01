---
name: minecraft-build-planning
description: Plan, inspect, and iteratively refine Minecraft builds using the paired local Builder MCP server and its live block catalog.
---

# Minecraft build planning

Use the `builder-minecraft` MCP tools when the player asks to inspect, design, revise, or validate a build in their active Minecraft world.

Start by checking `minecraft_status`. If the client is not paired, ask the player to explicitly approve pairing in Builder Settings, then call `pair_local_minecraft`. Work only inside the region the player selected and scanned. Inspect the bounded snapshot and current view before proposing construction. For visual choices, search the local block catalog in small, relevant pages; inspect candidate blocks with `describe_block` and use its actual in-game thumbnail and legal states. Do not assume a familiar-looking modded block behaves or looks like its name suggests.

Think like a builder, not a block placer: establish a readable silhouette, proportion, hierarchy, rhythm, palette, material transitions, lighting and connection to existing terrain/structures. Treat the current snapshot and user images as evidence. Preserve existing work unless the user authorizes replacement/removal. If available evidence cannot establish a block's appearance/state, say so and choose a verified alternative or ask.

Return a concise design intent and a staged, exact plan using the contract in [plan-schema.md](references/plan-schema.md). First call `inspect_build_plan`; fix every validation failure against a fresh snapshot. Explain the concept, footprint/height, materials, stages, what is preserved/changed, and remaining uncertainty in ordinary language. Only call `publish_build_preview` after the player explicitly asks to show/publish that validated preview. Publishing is a hologram preview only: never claim that blocks were placed, and never move the player, alter inventory, issue game commands, or change world blocks.

Use `capture_view` for an additional image only when needed; it captures the current camera without moving it. Use the MCP plan archive for plan revisions, not chat storage: Codex owns its own conversation history, while `list_plan_history` / `reinspect_archived_plan` preserve and revalidate build proposals. Use `open_plan_viewer` when the player wants the local read-only timeline/viewer. A historical plan is never safe to reuse without `reinspect_archived_plan` against the current exact world, selection and snapshot.

When a tool fails or returns incomplete/stale/unknown data, report the actual reason briefly and stop dependent planning rather than inventing facts. Distinguish in-game rendered block thumbnails from the viewer's ID-color schematic; only the Minecraft client has authoritative mod geometry.

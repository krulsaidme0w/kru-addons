---
name: minecraft-build-planning
description: Plan, inspect, and iteratively refine Minecraft builds using the paired local Builder MCP server and its live block catalog.
---

# Minecraft visual studio

Use the `builder-minecraft` MCP tools when the player asks to inspect, imagine, compare, or refine a build in their active Minecraft world.

Treat this as architectural design, not block filling. Before drafting coordinates, define
the massing (primary volume, secondary volume, terrace/connection), a readable silhouette,
an entrance axis, circulation, negative space, and a small material palette with explicit
roles. Never accept a rectangular shell plus random furniture as a finished design.

The normal workflow is visual, not contractual:

`brief → visual references → 2–4 concepts → live block/item lookup → local render → camera critique → iterate`

Do not force a palette exercise onto a small minimalist build. Choose materials by visual role
only when useful: silhouette, frame, surface, glass, light, furniture, and one accent. For a
small house, composition, roof shape, depth, window rhythm, terrace scale and light matter
more than the number of blocks.

Use image generation for reference concepts when it helps the player choose a direction. Label
generated images as inspiration, then replace guessed blocks with live catalog entries from the
current Minecraft instance. Keep the visual idea separate from any later exact export.

Treat `compile_build_ops` and exact block operations as optional export helpers only. The
visual studio must work without exposing coordinates or forcing the player through a plan.

Start by checking `minecraft_status`. The bridge is local-only and connects automatically while Builder is active; there is no pairing or Allow step. If no selection or complete snapshot is reported, ask the player to enter six valid bounds in Builder and wait for the automatic scan to finish. Work only inside that region. Inspect the bounded snapshot and current view before proposing construction. For visual choices, search local block and item catalogs in small, relevant pages; inspect candidate blocks with `describe_block` and use its actual in-game thumbnail and legal states. Use `search_items` / `describe_item` for exact item IDs and rendered icons. Do not assume a familiar-looking modded block behaves or looks like its name suggests.

Think like a builder, not a block placer: establish a readable silhouette, proportion, hierarchy, rhythm, palette, material transitions, lighting and connection to existing terrain/structures. Treat the current snapshot and user images as evidence. Preserve existing work unless the user authorizes replacement/removal. If available evidence cannot establish a block's appearance/state, say so and choose a verified alternative or ask.

Return concise visual concepts in ordinary language: silhouette, camera impression, key details,
reference direction, and what should be tested next. Use the local viewer as a visual studio:
show the Minecraft-backed scene, orbit it from front/side/top/isometric views, and preserve
iterations. Exact block plans and `inspect_build_plan` are optional export paths only, never a
mandatory step for ideation. Nothing is published to the world.

Use `capture_view` for the current camera without moving it. Use the local archive for visual
iterations and reference renders, not as a source of truth for construction. Codex owns the
conversation history; previous visual directions are inspiration and must be rechecked against
the current camera, selection and live catalog before reuse.

When a tool fails or returns incomplete/stale/unknown data, report the actual reason briefly and stop dependent planning rather than inventing facts. Distinguish in-game rendered block thumbnails from the viewer's ID-color schematic; only the Minecraft client has authoritative mod geometry.

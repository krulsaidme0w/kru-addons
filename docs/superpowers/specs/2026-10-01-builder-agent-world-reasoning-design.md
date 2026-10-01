# Builder Agent World Reasoning and Visual Review Design

## Goal

Let the agent reason from the exact selected-world snapshot and local block registry, produce coherent Minecraft architecture plans, and inspect a compact visual rendering of its own draft before returning it to the user.

## Scope

- Preserve strict selected-bounds-only planning and exact registry/state validation.
- Make known, unknown, and uncaptured cells unambiguous to the agent.
- Expand the current read-only block catalog tools only where needed for plan inspection and composition.
- Give the agent a bounded visual feedback loop: draft → validate/render → inspect → at most one automatic critique/revision pass → final plan.
- Improve design instructions for silhouette, proportions, structural support, circulation, negative space, palette roles, and restrained accents.
- Keep full plans, tool calls, and previews bounded; fail safely when a state, model, or preview cannot be verified.

## Non-goals

- Placing, removing, or editing world blocks; changing player movement/camera; executing commands.
- A generic agent filesystem, unrestricted MCP access, or broad world access outside configured bounds.
- Importing a separate structure-generation mod or adding a network dependency.
- Claiming an objective “beauty score.” Heuristics may validate consistency, but visual quality remains model-judged and user-reviewable.

## Current foundation

The Builder request already includes numeric bounds, a compact voxel map with exact state palette, explicit unknown/uncaptured markers, an optional previously accepted plan, and constraints forbidding unsupported block-entity data. Local dynamic tools already list mod namespaces, search registry IDs/localized names, and describe an exact block with a small inventory-model preview. `PlanValidator` and Minecraft-specific rules validate bounds, duplicate assignments, dependencies, registry states, and paired structures. The world renderer displays a read-only plan ghost. This project extends those seams; it does not replace them.

## Architecture

Add a read-only plan-inspection dynamic tool that receives a bounded candidate plan in the same structured representation used by the final response. It validates exact IDs, states, stage dependencies, occupied cells, bounds, and unsupported data before rendering. On success it returns a compact contact sheet to the model containing an isometric view plus top and side projections, with a small palette legend and distinction between existing blocks, placements, replacements, and removals. The render uses local baked block models/textures and must run on the client render thread; it never changes the world or player view. Image dimensions, compressed bytes, plan cells, stages, and tool-call count have explicit caps.

The model receives the preview as a normal tool result and is instructed to perform one critique pass: check overall massing, readable entrance/circulation, support continuity, proportions, palette balance, and obvious clipping/holes; revise if needed, then return its final schema-constrained response. A hard client-side turn/iteration budget prevents loops. Final decoding and proposal acceptance remain separate and revalidate against the original request snapshot. Preview failures are returned as explicit tool results; the agent must not claim visual review occurred if it did not.

Design guidance is a compact rubric, not a fixed style generator. The user’s prompt controls style; otherwise the agent chooses a coherent style and explains the palette and composition briefly. For modded blocks it must search and inspect exact candidates instead of inventing IDs. Tool output is paginated and bounded to avoid filling context with the entire registry. The plan schema remains full-state-only; inventories and NBT-dependent blocks are rejected with exact diagnostics and safe alternatives can be proposed.

## Data flow

1. Send immutable current snapshot and explicit selection bounds to the model.
2. The model uses catalog search/description only as needed, then submits a draft to the plan-inspection tool.
3. The client validates that draft against the same snapshot and bounds. Invalid drafts return structured coordinate/state errors and no misleading preview.
4. The client renders the validated draft into one bounded contact sheet and returns it as an image tool result.
5. The model makes at most one critique/revision pass and returns the final structured plan plus concise design rationale.
6. The existing decoder validates again; `ProjectSession` exposes the draft, and user action is still required to save/activate its overlay.
7. The conversation archive records final response, tool-review result, every proposal revision, and its source bounds without persisting user images or auth data. Historical contact sheets can be regenerated locally from the archived plan and source bounds; unavailable local block models produce an explicit unavailable-preview state.

## Safety and failure handling

- The tool is read-only and bound to the active request generation, session, and exact selection.
- A stale/cancelled tool call cannot return a preview for another request.
- No coordinate outside bounds can be rendered as a valid proposal.
- Missing block models, invalid properties, excessive image bytes, unavailable render thread, and unsupported state are explicit failures; they never silently become air or vanilla substitutes.
- If preview generation is unavailable, the tool returns an explicit failure and the visual critique pass is skipped. A validated plan may still be returned, but its review metadata and UI must say `visualReview=false` and expose the reason; neither the agent nor UI may imply that it saw the plan.
- The preview is a plan artifact, not a screenshot of the live world, and must label unknown/uncaptured surroundings.

## Verification

- Tool contract tests cover accepted/rejected schemas, exact bounds, stale calls, unsupported states, size caps, and image response validation.
- Renderer tests cover stable projections, stage filtering, replacement/removal distinction, palette legend mapping, empty/oversized plans, and render-state cleanup.
- Agent protocol tests prove the image is returned to the active tool call, no second critique cycle can start, cancellation stops rendering, and final output is decoded only after the review budget is exhausted.
- Representative fixtures cover a compact pavilion, a tall narrow tower, an asymmetrical/modded palette build, an occupied-cell revision, an out-of-bounds draft, and a block-entity-dependent proposal.
- Manual in-game verification checks that the visual preview matches the in-world ghost and that the preview process never changes blocks or camera position.

## Dependencies

Consumes archive/diagnostic interfaces from `2026-10-01-builder-chat-archive-and-errors-design.md`. The UI project displays the resulting plan preview and review status but does not own rendering or validation.

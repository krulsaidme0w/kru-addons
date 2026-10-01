# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary user (inferred from the approved Builder specification): a Minecraft player using Codex to inspect a selected region and refine a construction proposal.

## Product Purpose

Builder's local MCP companion exposes the active Minecraft client's selected-world evidence and modded block registry to Codex, then helps the player review a validated block plan before any in-game change.

## Positioning

The Minecraft client remains authoritative for loaded-world state, active block registry, and screenshots; plan validation happens against an exact bounded snapshot and publication is preview-only.

## Operating Context

The user selects and scans a bounded region in Minecraft, pairs a local MCP companion once, researches blocks incrementally, inspects a staged plan, and reviews its preview in Minecraft and the local viewer.

## Capabilities and Constraints

- The bridge and viewer bind to loopback and require explicit in-game pairing.
- World inspection is limited to the current explicit selection and completed client-loaded snapshot.
- Plans use exact relative coordinates, block states, and PLACE/REPLACE/REMOVE preconditions.
- Unknown cells, stale snapshots, unsupported block entities, and invalid registry states fail closed.
- Preview publication does not place or remove blocks, move the player, execute commands, or edit inventory.
- Codex owns conversation history; the companion archive stores plan revisions and preview metadata, not chat transcripts.
- Exact out-of-game geometry for every modded model is not guaranteed; fallback fidelity is labeled.

## Product Principles

- Minecraft is the source of truth.
- Show evidence and uncertainty instead of guessing.
- Keep discovery incremental and bounded.
- Make every proposed transition inspectable before publication.
- Keep planning separate from world mutation.

## Evidence on Hand

Approved architecture and behavior: `docs/superpowers/specs/2026-10-01-builder-local-mcp-world-design.md`.

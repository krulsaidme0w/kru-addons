# Builder Local MCP Roadmap

This roadmap sequences four separately testable implementation plans from the
approved design spec. It is not a second detailed plan; each phase plan defines
its own files, interfaces, tests, and commits.

1. [Local bridge and MCP server](2026-10-01-builder-mcp-bridge.md) — a Codex
   stdio server can discover a paired running Minecraft client, report status,
   read a bounded selection, and return a real client screenshot.
2. [Modded block catalog](2026-10-01-builder-mcp-catalog.md) — the active
   registry is indexed locally and searchable, with exact block-state metadata
   and Minecraft-rendered previews.
3. [Plan protocol and viewer](2026-10-01-builder-mcp-plans-viewer.md) — the
   agent can validate and publish a staged PLACE/REPLACE/REMOVE proposal; a
   local viewer shows the current selection, changes, stages, and plan history.
4. [Codex plugin and build skills](2026-10-01-builder-codex-plugin.md) — a
   repo-scoped plugin starts the local server and supplies versioned build
   workflow skills without editing global Codex configuration.

Dependencies:

```text
Bridge/MCP ──→ Block catalog ──→ Plan protocol/viewer ──→ Codex plugin/E2E
```

No phase may add a Minecraft world-write or player-movement tool. Live-game
checks use the existing 1.21.1 Fabric/Cobblemon instance and representative
vanilla and modded blocks. Installation remains a separately requested action.

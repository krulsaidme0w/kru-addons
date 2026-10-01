# Builder Agent Implementation Roadmap

The work is split into three independently testable plans so archive durability and safe world reasoning are established before the UI depends on them.

1. **Archive and diagnostics** — [`2026-10-01-builder-chat-archive-and-errors.md`](2026-10-01-builder-chat-archive-and-errors.md). Adds local selectable conversation records, complete plan revision history, safe legacy migration, and structured errors.
2. **Agent world reasoning and visual review** — [`2026-10-01-builder-agent-world-reasoning.md`](2026-10-01-builder-agent-world-reasoning.md). Adds a compact design rubric, bounded plan-inspection tool, local multi-view plan preview, and at most one critique/revision cycle.
3. **Codex-like chat UI** — [`2026-10-01-builder-codex-chat-ui.md`](2026-10-01-builder-codex-chat-ui.md). Replaces the crowded form with conversation navigation, transcript, inline plans/errors, and a pinned composer.

## Dependency order

```text
Archive + structured diagnostics
             ↓
Agent world reasoning + visual review
             ↓
Minimal conversation UI
```

The agent and UI must not write to the Minecraft world. A saved plan remains read-only and must be revalidated against a fresh scan before it can become the active overlay. Screenshot attachments remain explicit and are not saved into chat history; generated plan previews can be recreated locally from the plan and its source bounds.

## Overall completion checks

- Two or more independent conversations and their message/plan histories survive screen close, module restart, and world/selection changes.
- A failed request is retained as an inline diagnostic with its sanitized cause and a useful next action.
- A plan outside bounds or using an unknown/unsupported state is rejected with coordinates and a specific reason.
- For a valid draft, the agent receives a local rendered preview; it can critique/revise at most once. If rendering fails, the saved response says visual review did not happen.
- The minimal UI keeps chat history and composer usable at small game-window sizes and exposes a selected conversation list.
- Existing Builder addon tests pass and the addon package builds; in-game verification confirms no world or camera mutation.

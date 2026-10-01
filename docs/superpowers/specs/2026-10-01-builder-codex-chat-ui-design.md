# Builder Codex-Like Conversation UI Design

## Goal

Replace the crowded Builder form with a minimal, usable conversation workspace: selectable saved chats, readable history, a persistent composer, and inline plan/error artifacts.

## Scope

- Main layout: conversation selector/sidebar, selected conversation history, pinned composer.
- New conversation and selecting an existing conversation from the local archive.
- Inline user/agent messages, progress, structured failures, saved-plan history, and visual plan previews.
- A compact connection state and access to the existing bounds/settings editor without permanently consuming chat space.
- Responsive behavior for small game windows and resizable/scaled UI.

## Non-goals

- Replicating Codex branding or web application behavior beyond the familiar information hierarchy.
- Cloud synchronization, accounts, conversation sharing, or image history.
- Hiding essential diagnostics behind an “Open Details” control or replacing transcript errors with a transient status strip.
- Any world mutation controls.

## Layout

Use a two-pane conversation workspace modeled on Codex’s interaction pattern, not its exact visuals. The left pane contains “New chat” and a compact list sorted by recent activity, with one clearly selected conversation. The right pane contains a scrollable chronological transcript. A single composer remains anchored at the bottom with the send action; explicit image/screenshot controls remain optional and retain their current consent behavior. When the viewport is too narrow, the list collapses behind one navigation control and the history/composer retain priority.

Connection readiness is a small status indicator near the composer/header, not a full-width block of repeated technical text. Bounds, overlay, executable, and privacy settings live in a settings view/drawer and do not occupy the transcript by default. A draft plan appears as an inline artifact with its preview, stage/cell summary, validation status, and one clear action to save/use it as the read-only overlay. Previous plan versions remain reachable from the same conversation history. There are no controls to place blocks in the world.

## Message and error presentation

Every user/agent message remains in the transcript. Progress updates may update one in-flight item instead of spamming permanent entries. A failure is an inline, persistent diagnostic item showing operation/phase, concise cause, relevant sanitized technical detail, and the next useful action. Do not require expanding a hidden details panel to learn why it failed. Large diagnostics wrap, remain scrollable, and cannot push the composer off-screen. The status indicator may summarize current state but is never the sole record of an error.

Plan previews show the contact-sheet image produced by the agent-review project. They are explicitly labeled as proposed plan previews, not live screenshots. On conversation reopen, the archive reloads transcript and plan history; any plan requiring world context is visibly stale until a fresh scan validates it. A world/selection change never erases or silently switches the selected conversation.

## Accessibility and sizing

Respect Meteor scale and viewport dimensions, cap the sidebar width, reserve a minimum composer height, support text wrapping and vertical scrolling, and ensure errors and status are distinguishable by text/icon as well as color. At very small dimensions, prioritize transcript and composer and expose conversation selection/settings through compact controls.

## Verification

- Pure UI-model tests cover selected conversation, empty state, error presentation, stale plan marker, and viewport layout decisions.
- Widget contract tests prove the composer stays present, history scrolls independently, and sidebar collapses at narrow sizes.
- Manual checks at 854×480, 1191×628, and 1920×1080 verify scaling, long messages, large diagnostics, multiple conversations, and plan previews.
- In-game check confirms the minimal main view contains only navigation/status, transcript, and composer; settings remain out of the way.

## Dependencies

Consumes the archive and structured diagnostic interfaces from `2026-10-01-builder-chat-archive-and-errors-design.md`, and plan preview/review metadata from `2026-10-01-builder-agent-world-reasoning-design.md`.

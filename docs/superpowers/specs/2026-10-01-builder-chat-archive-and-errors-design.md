# Builder Conversation Archive and Error Reporting Design

## Goal

Make Builder conversations and plan revisions durable, selectable local records, and make every meaningful failure visible in the conversation with enough context to diagnose and retry safely.

## Scope

- Persist separate conversations with stable IDs, titles, timestamps, ordered messages, and a plan history.
- Preserve proposed, revised, rejected, and user-saved plans as immutable revisions with their validation outcome and source context identity.
- Select, create, and reopen conversations without clearing them when the world, selection, screen, or module runtime changes.
- Record operation failures in the selected conversation, not only in a transient status label.
- Keep the existing fail-closed rule: reopening a plan never makes it usable in a changed world until fresh bounds and block states validate it.
- Migrate the currently persisted single accepted plan into an initial conversation when it is safe to do so.

## Non-goals

- Persisting authentication state, access tokens, screenshots, image attachments, or raw process output.
- Sending any world mutation request. Builder remains a plan-only tool.
- Synchronizing the archive with a cloud service.
- Keeping every transient progress update as a permanent message.

## Architecture

Add a local archive domain independent of the GUI and Codex process. A conversation record owns its stable conversation/project UUID, display title, created/updated times, ordered text entries, and immutable plan-history entries. Each plan-history entry stores the full `BuildPlan`, its lifecycle label (proposal, revised, rejected, or saved), source context revision/session/bounds, and validation diagnostics. The current selected conversation ID is persisted separately. A plan’s project UUID is the conversation ID so existing plan validation and rendering continue to work without a second identity translation.

An archive store under the existing `kru-builder` instance directory uses versioned JSON and the repository’s established safe file practices: bounded decode, reject symbolic links, write temporary files, force data, and atomically replace where supported. Store APIs accept immutable records and leave thread scheduling to the caller. Message and plan writes are serialized in order by the controller; a failed write remains visible as a durable-storage error and must not be reported as saved. Deleting a conversation is explicit and removes only that conversation’s archive file; no automatic pruning silently loses history.

Replace string-only failure paths with a structured diagnostic carrying category, operation/phase, user-safe cause, optional exact sanitized detail, suggested next action, and timestamp. Categories distinguish scan/context, authentication/transport, model/tool, malformed or invalid proposal, local persistence, and cancellation. Controller transitions append one readable failure entry to the active conversation; repeated identical failures may be deduplicated while preserving a retry count. Credentials and screenshot bytes/paths are excluded. Codex stderr must be retained only in a bounded, sanitized local diagnostic path if needed; it is never dumped into the chat verbatim.

## Data flow

1. Creating a conversation assigns a fresh UUID and saves an empty archive record before selecting it.
2. User text is appended before dispatch so a crash cannot erase what was sent. A completed response and any returned proposal are then appended as separate durable events.
3. Every schema-decodable proposal is stored even when semantic validation fails. If the response cannot be decoded into a plan, its assistant response and structured diagnostics are stored instead. Acceptance adds a saved-plan lifecycle event only after fresh-snapshot validation succeeds and archive persistence confirms success.
4. A rejected or superseded plan remains in history; it cannot become active merely by reopening the conversation.
5. On startup, the store loads the conversation index and records. Legacy single-plan storage is imported once, preserving the old plan as an untrusted saved revision. It becomes an active overlay only after the existing fresh validation path succeeds.
6. World/selection changes invalidate active scan/session state but do not clear transcript or plan history.

## Failure behavior

- Corrupt or unsupported archive data is reported with the affected conversation/file and recovery action; valid conversations remain available.
- Storage failure is never described as a successful save. The current in-memory conversation remains open with a visible unsaved warning and retry action.
- Invalid plans retain all validator diagnostics, including stage and coordinates, in a readable transcript entry.
- Transport/tool failure preserves the user request and reports the failing phase, sanitized underlying reason, and useful next action.
- A stale saved plan remains viewable but is visibly marked as needing a fresh world check.

## Compatibility and privacy

The archive is local to the Minecraft instance directory. It stores user/model text and block plans, so its location and deletion behavior must be documented. Existing explicit screenshot attachment consent remains unchanged; user screenshots and image attachments are not copied into chat history. Small generated plan contact sheets may be regenerated from the archived plan and source bounds; they are derived previews, not screenshots or user attachments. Authorization URLs, device codes, tokens, environment values, and raw screenshots are never archived.

## Verification

- Unit tests cover archive round-trip, multiple conversations, ordered messages, plan lifecycle history, malformed/oversized files, symlink rejection, atomic-replace fallback, and visible save failure.
- Controller tests prove world changes preserve history, requests are saved before dispatch, failed turns create one diagnostic entry, accepted plans are recorded only after fresh validation, and rejected/stale plans remain inactive.
- Migration tests prove the legacy accepted plan imports once and cannot bypass fresh-context validation.
- Run the Builder addon test suite and package verification without staging or modifying unrelated user changes.

## Dependencies

The agent visual-review project consumes conversation IDs, plan-history APIs, and structured diagnostics defined here. The UI project consumes the conversation-list and diagnostic presentation APIs defined here.

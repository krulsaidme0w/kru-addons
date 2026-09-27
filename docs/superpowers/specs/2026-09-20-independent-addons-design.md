# Independent KRU addons

User-approved architecture, including the server-neutral/configurable-rules refinement.

## Product boundary

Unmodified official Meteor 0.5.8 is an external dependency on Minecraft 1.21.1 / Java 21.
Two independently buildable addons use the official Meteor addon entrypoint contract.
Removing either addon must not disable the other or modify Meteor's built-in navigation.
The original cobbhelperv2 remains unchanged.

## Cobblemon addon

Retain Discovery, Dungeon Discovery, Type Chart and Battle Matchups. Port existing tested computations instead of rewriting them. Discard breeding automation, screenshots used for its live verification, catalog UI, Shop, GTS and old UiUtils. Autosort is not part of this stage.
Require Cobblemon, not Academy. Data readers may adapt to supported Cobblemon ABIs; unsupported/missing fields are unavailable, never affirmative defaults.

Minecraft/Cobblemon client facts feed configurable recognition rules and presentation. Profile-specific guardian names, dimension IDs and item-model numbers are data presets, disabled by default. Generic facts and rules must be testable without a running client.
Starscript supplies labels over a read-only per-observation context. It is not a UI renderer, packet transport or permission to invoke server commands. Predicates use structured configuration, not a new executable language. Invalid rules yield diagnostics; missing inputs do not match. Compile labels on load, evaluate only bounded snapshots. No global mutable Starscript entity variable.
Client resource spawn data is labeled as local reference data, not authoritative server spawn probability. Optional protocol providers are isolated and cannot become required mod dependencies.

## Presentation

Use Meteor's existing widgets and theme wherever possible. The tools addon now
includes the functional UI-Utils surface as a Meteor module: contextual controls
for handled screens, bounded GUI packet delay, and an interactive packet viewer.
Its packet viewer is live-only: direction (`ALL`/`C2S`/`S2C`), simple substring
search, movement/keepalive suppression, clear, and an unpaged one-row-per-packet
list. No packet file export is part of the design.

Type Chart and Battle Matchups use the same visual contract (title bar, muted
panels, compact controls, bounded input), but each addon carries its own tiny
implementation so there is no runtime dependency between JARs. Packet and chart
windows explicitly own keyboard/mouse focus. Contextual toolbox and matchup
overlays leave the existing container/battle screen intact. Intercept input only
inside active panel bounds; outside clicks reach the underlying screen. Closing a
viewer does not implicitly stop a separate recording service.
The addons share conventions, not a mandatory shared runtime or cross-addon imports.

## Tools addon

Packet file export is explicitly out of scope (user decision). Lack of JSONL support is not a reason to implement a new recorder. Prioritize interactive inspection, filtering and queue controls from the selected upstream.

Preserve a selected upstream GUI/packet implementation behind Meteor module lifecycle controls. Audit source/metadata and compare original behavior before selection. Do not treat a changed version range as a compatibility port. Do not silently remove ModernFix or bypass its declared conflict. A large backport or substantive upstream choice requires reporting evidence to the user before proceeding.

## Acceptance

- Each addon builds on its own against the official Maven Meteor artifact.
- Output jars contain no copied Meteor classes and no cross-addon references.
- Standard type calculations and preserved feature tests pass.
- No selected server profile means no Academy-specific classification.
- A preset can change a model mapping/name/dimension without Java edits.
- Missing facts, invalid expressions and profile reload have deterministic tests.
- Runtime verification distinguishes plain Cobblemon from the installed fork; ABI-sensitive overlays are not claimed working from compilation alone.
- No installation or server-side actions during development.

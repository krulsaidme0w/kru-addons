# Discovery runtime acceptance

Status: not executed. Build/JUnit evidence is recorded separately.

## Safe setup

- Minecraft 1.21.1, official Meteor 0.5.8, current independent Cobblemon addon.
- Keep original pack mods unchanged. Installation requires an explicit request.
- Both Discovery modules start disabled on a clean configuration. Profile classification starts disabled.
- Save a copy of the current Meteor configuration before a live migration check.

## Pokémon facts

- Observe a normal Pokémon, shiny Pokémon, alternate form and a player-owned Pokémon.
- Species and entity registry ID must be separate fields. No field failure may hide other values.
- Form/aspects, level and gender use actual client evidence. IV/EV/ability/nature defaults must not be presented as known private values.
- Unknown ownership must not match `wild=true`.
- Native portrait failure must not close the inspector or fabricate a species portrait.
- Server-map rarity appears only after an actual supported CobbleNav message; local reference data remains labeled separately. No automatic request/command is sent.

## Common lifetime and rendering

- Enable both consumers: inspect tick cost with many loaded entities; collection must not be duplicated per consumer or per label.
- Unload a target's chunk: live highlight disappears without a claim that the Pokémon despawned server-side.
- Change dimension, reconnect and reconfigure: data is cleared; panel position, scale, hidden/collapsed state, filters and binds remain.
- Enable Discovery with global Meteor HUD off: HUD remains off. Existing Discovery HUD element does not move.
- Resize the game window and change GUI scale. Narrow inspector shows a usable Back path, not clipped controls.
- Use Interact Key, drag the title bar, release outside the window, type in search. No held movement keys or game hotkeys leak from text input.

## Generic observations and rules

- With no profile selected, inspect an item display, armor stand, ordinary entity and loaded block entity. No automatic colored classification.
- Inspect the crosshair target and create a draft rule from facts actually present.
- Add a sixth category with a different name/color/range. It must classify and render without Java changes.
- Check numeric minimum/maximum, equals, suffix, contains and token. Missing fields explain UNKNOWN; unrelated data does not match.
- Two matching rules with equal priority use file order. Higher priority wins when explicitly changed.
- Disabled category and disabled rule must not render.

## Profile transactions

- Invalid draft: Validate/Preview/Apply/Save show a useful error, active profile and saved bytes remain unchanged.
- Apply a valid draft: active rules change, disk does not. Save valid draft: disk changes atomically.
- Select a different missing/broken profile: old server rules cease classification immediately.
- Reject absolute external paths, traversal and symlink escape.
- Import a v1 file: preview preserves rule order/results and old target styles/toggles. Save only after explicit confirmation; original backup remains readable.
- Academy preset remains opt-in and labeled historical/unverified until its dimension, IDs and model numbers are checked against current observations.

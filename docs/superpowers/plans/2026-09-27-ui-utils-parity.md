# UI Utils behavior in the KRU overlay

## Approved scope

The user approved replacing automatic collection with UIUtils-style controls:
save the current screen/handler, restore it with V, local close, server-side
desync, enable/disable inventory packet sending, manually buffered packets,
flush/discard, flush then disconnect, editable packet fabrication, title JSON,
chat/commands. Preserve the Cobblemon PC overlay and use its own packet contract.
Reference: MrBreakNFix/ui-utils commit b1342f381a2ce6054b0e42840fd937af5fdbc7e7.
Implement independently; do not copy upstream implementation or its defects.

## Boundaries

- Only <repository>; no installation, no live packet experiments.
- Preserve existing uncommitted PC Organizer/Discovery work.
- No dependency between tools-addon and cobblemon-addon; official Meteor remains untouched.
- Saved screens are client objects, not evidence of an active server container.
- Packet controls only cover inventory packet families, never the entire connection.
- Clear network queue and saved references on disconnect/world change or module disable.
- Bound queue size and fabrication repetitions; never silently lose queued packets on each click.
- No automatic moves, releases, chat commands, or packet sends on saving/loading a GUI.

## Tasks

1. Packet tools: pure FIFO/send/delay state and tests; Meteor packet hook; explicit flush/discard;
   native-container fabrication editor (sync/revision/slot/button/action/repetitions);
   explicit Cobblemon storage packet allowlist. One implementation owner, separate files.
2. Saved GUI: identity-scoped holder tests, Screen/ScreenHandler restoration adapter,
   configurable V binding, forget on module/world/connection reset; support plain PCGUI.
3. Overlay: remove collection setting/classes/tests; wire compact controls and advanced section,
   editor launch, saved/closed/desynced state, correct input ownership and title JSON.
4. Verification: targeted red/green tests, full offline test/remapJar, separate review of packet
   ordering, stale screen lifecycle and close/desync behavior. Document parity and limitations.

## Checks

Run from cobblemon-addon: `./gradlew test remapJar --offline`.
Behavior tests must cover FIFO ordering, blocked sends, manual flush, overflow, reset,
saved handler identity and reloading after local close, context changes and editable fields.
Live Minecraft verification remains pending; a passing build is not live compatibility proof.

# UI Utils in KRU

Behavioral reference: [MrBreakNFix/ui-utils](https://github.com/MrBreakNFix/ui-utils),
commit `b1342f381a2ce6054b0e42840fd937af5fdbc7e7`. Independently implemented;
upstream code is not bundled or copied.

## Everyday controls

- **Chat or /command**: ordinary text goes to chat; a leading slash runs a command.
- **Save GUI / Load GUI**: retains the actual screen and, for native containers,
  its handler. Default **V** restores the saved window while playing with no other
  screen open. The key is configurable in the module settings.
- **Close without packet**: locally hides supported vanilla chest/inventory and
  Cobblemon PC screens. Unknown modded teardown implementations are not assumed safe.
- **Send packets**: block or allow inventory clicks, not chat, movement or keepalive.
- **Delay packets**: keep inventory packets in order until switched off or manually
  flushed. This is not a millisecond delay. Queue count is always visible.

## Advanced controls

- **De-sync** requests server container close / Cobblemon PC unlink but leaves the
  client window open. **Close normally** ends the container session normally.
- **Send queued / Discard queued** explicitly dispatch or drop buffered packets.
  Explicit flush bypasses this module's Send/Delay switches.
- **Send + disconnect** dispatches queued writes before disconnecting; this is not
  a promise of server acknowledgement.
- **Fabricate Packet** opens an editor for native container Sync ID, Revision,
  Slot, Button, Action and Repeat (1–100), with explicit Slot and Button sends.
  Slot sends include local prediction. Native fields are not invented for
  Cobblemon PC, whose storage protocol is separate.
- **Copy Title JSON** copies the serialized title, not the inventory contents.

## Lifetime and limits

Saved GUI is a client object, not a server reopening request or evidence that the
server still accepts clicks. Stale sync/revision packets may be rejected. Saving
and loading do not themselves send inventory moves.

Saved references and queue clear on disconnect, world change and module disable.
Within one connection/world the queue can span menus intentionally. It is capped
at 512 packets; overflow drops the newest packet with an on-screen warning.
Do not combine this queue with the separate Tools addon's UI Utils packet delay.

Cobblemon storage move/swap/rename packets are explicitly classified. PC helper
transfers are disabled while Send is off, Delay is on, or the UI was restored or
de-synced. There is no automatic item collection anymore.

Offline unit tests and remapped-JAR compilation do not replace live Minecraft
checks. No updated JAR is installed as part of this change.

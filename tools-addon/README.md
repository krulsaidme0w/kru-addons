# KRU Tools 0.2.0 — selective Packet Delay

Independent Meteor 0.5.8 addon for Minecraft 1.21.1. Cobblemon is not required.
Only Packet Delay is registered; old UI Utils / Packet Inspector are excluded
from the runtime artifact. No additional GUI mixins are installed.

## Use

Meteor → KRU Tools → Packet Delay (right-click for settings).

- `Outgoing packets`: select exact C2S packet classes. Empty = delay nothing.
- Module ON holds selected packets; OFF sends the FIFO on the same connection
  and world. Use Meteor's ordinary `Bind` setting for a hotkey (unbound initially).
- `Send queued now`: flush without turning the module off.
- `Discard queued`: forget held packets without sending; the module stays on.
- Module info shows queue count.
- World/connection change, respawn or disconnect discards stale queued packets. Selected
  classes and the module toggle are not manually reset on those boundaries.
  During respawn, holding resumes only after the player/world is replaced.
- At 4096 packets the existing queue is released before the next packet passes.

This is manual pause/release, not an N-millisecond ping simulator. Incoming
packets are not delayed. CustomPayload selection applies to the whole class,
not individual Cobblemon payload IDs. Other packet-cancelling Meteor modules
may still cancel released packets. Delaying keepalives/teleport confirmations
or inventory transactions can cause timeouts or desynchronization.

Build: `./gradlew test remapJar --offline`. Output:
`build/libs/kru-tools-0.2.0.jar`. Native Minecraft runtime has not been tested.
Nothing is installed into PrismLauncher automatically.

## Alternatives checked

- [PlayClient](https://github.com/Olafcio1/PlayClient): PacketDelay, current
  `gradle.properties` targets 1.21.11.
- [Softcore](https://github.com/8oft/softcore-addon): selected outgoing queue;
  documented 1.21.10/1.21.11, no verified 1.21.1 artifact found.
- [DU public addon](https://github.com/YAYLOLDEV/du-addon-public): master is
  1.21.4; listed branches 1.21.4, 1.21.8, 1.21.10, 1.21.11, master.
  This is not asserted to be the original official DupersUnited distribution.
- [TrouserStreak](https://github.com/etianl/Trouser-Streak): modern main contains
  timed C2S/S2C PacketDelay. Its 1.21.1 branch has no PacketDelay.java, and the
  official 1.5.1/1.21.1 JAR downloaded and inspected has no PacketDelay class.

No verified ready-to-use equivalent for this pinned game/client pair was found.
Only the relevant hold/release implementation was adapted; see THIRD_PARTY.md.

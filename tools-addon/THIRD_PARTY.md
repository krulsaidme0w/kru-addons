# Packet Delay provenance

The selective class match, FIFO holding and re-entrant release mechanism are
adapted from DupersUnited's PacketDelayModule and PacketPauseManager:
https://github.com/DupersUnited/dupersunited-mod/tree/4b61cea0ca90d4a6e27792a2e7a02598f737b150

Upstream authors: vinzy, litten, blendy, Crosby, Khao and contributors.
License: GPL-3.0-or-later (see LICENSE).

KRU adaptations: Minecraft 1.21.1 / Meteor 0.5.8; Meteor PacketListSetting and
PacketEvent instead of the upstream GUI/module/mixin framework; instance-local
synchronized queue, explicit world/connection/player scope; original packet
connection validation; a respawn barrier until player/world replacement;
finally-protected release;
4096-packet cap releases the old FIFO before the current packet continues;
empty selection by default; manual send/discard controls. The module's normal
Meteor Bind toggles hold/release. There is no automatic millisecond delay.

The dormant KRU UI Utils / Packet Inspector sources, their exclusive helpers
and tests, and the unregistered GUI mixin configuration have been removed.
Packet Delay remains the only registered module; its PacketPauseSession queue
and lifecycle behavior are unchanged. LICENSE and this provenance document
remain included in the JAR. Cobblemon is not a dependency.

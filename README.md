# KRU Addons

Independent addons for **Meteor Client 0.5.8**, **Minecraft 1.21.1**, **Fabric**
and **Java 21**. Developed for **Cobblemon Academy**: Academy dungeon recognition
and the collection-oriented PC layout are enabled by default. Other servers and
Cobblemon builds are not guaranteed to be compatible.

## Addons

| Addon | Features | Cobblemon required |
| --- | --- | --- |
| KRU Cobblemon | Discovery, Dungeon Discovery, Type Chart, Battle Matchups, UI Utils, PC Organizer | Yes |
| KRU Tools | Selective Packet Delay with manual send/discard | No |
| KRU Builder | Experimental client-side build planning and manual hologram | No |

The addons do not depend on one another. Meteor is external and unmodified;
no Meteor fork or Cobblemon binary is distributed here.

KRU Builder is experimental. It reads a bounded selection and prepares a
proposal for the player to inspect and accept; it never places/breaks blocks,
moves the player, or sends server commands. Before confirmation, the UI shows
the context and images prepared for a request. Confirming sends the text,
selected-area block data, and visible images to Codex through the configured
native-OS CLI. Do not include private builds or screenshots you do not want to
send to the model provider. The CLI uses a separate Builder-owned Codex profile;
it does not copy the regular Codex login or configuration. See
[Builder setup and privacy](builder-addon/README.md).

## Installation

1. Back up your instance and close Minecraft before replacing JARs.
2. Use Fabric for Minecraft 1.21.1, Java 21 and official Meteor Client 0.5.8.
   For KRU Cobblemon, retain your Academy instance's Cobblemon and dependencies.
3. Download the desired binary from [Releases](https://github.com/krulsaidme0w/kru-addons/releases).
   Do not install `*-sources.jar` or development JARs.
4. Put it in your instance's `minecraft/mods` directory. Keep only one version
   of each addon. Do not combine KRU with the old modified Meteor fork.
5. Start Minecraft and enable the features you want in Meteor.

Until a release is published, build locally or download an addon artifact from
a successful Actions run. CI success does not prove live server compatibility.

## Defaults and controls

- **Dungeon Discovery:** Academy observation profile is enabled by default.
  Server/resource-pack updates may require adjustments.
  [Profile settings](cobblemon-addon/src/main/resources/profiles/README.md).
- **Discovery:** species, rarity, class and appearance filters combine together.
  Unknown client facts do not prove rarity or ownership.
- **PC Organizer:** Collection mode offers MAIN, SPECIAL, SHINIES, UTILITY,
  TRADE and BREEDING zones, automatic shiny/special routing and type groups.
  Protected-zone contents are preserved. Review the preview before applying.
  [PC zones](docs/pc-dynamic-zones.md).
- **UI Utils:** container/PC overlay, commands, saved GUI restoration
  (`V` by default), and advanced inventory packet controls. Restoring a client
  GUI does not reopen a server-side session.
  [Controls and limits](docs/ui-utils-parity.md).
- **Battle Matchups:** known move effectiveness and potential type comparisons;
  unavailable information remains visibly unknown.
- **Packet Delay:** no packet classes selected by default; choose what to hold.
  Send/discard are manual, not a timed latency setting.

These are product defaults, not an exported player configuration. No personal
instance paths, saved accounts, server lists or user configuration are shipped.
Packet tools and automated transfers can desynchronize a client or violate server
rules. Use them only where permitted.

## Development

```sh
(cd cobblemon-addon && bash ./gradlew build)
(cd tools-addon && bash ./gradlew build)
(cd builder-addon && bash ./gradlew build)
python3 -m unittest discover -s distribution -v
python3 distribution/release.py cobblemon-addon
python3 distribution/release.py tools-addon
```

All three Gradle roots build independently. Packaging validates addon boundaries,
binary metadata, source archives and checksums.

- [KRU Builder setup, limits and experimental runtime checklist](builder-addon/README.md)
- [Builder live validation checklist](docs/builder-runtime-checklist.md)

- [Build, optional ABI checks and draft releases](distribution/README.md)
- [Current status and known limitations](docs/implementation-status.md)
- [Runtime checklist](docs/runtime-checklist.md)
- [GPL license](cobblemon-addon/LICENSE), [Cobblemon notices](cobblemon-addon/NOTICE),
  [Tools provenance](tools-addon/THIRD_PARTY.md)

Historical reports describe past revisions, not current release guarantees.
KRU is not an official Meteor, Cobblemon or Academy project.

# Migration decisions

Source: read-only cobbhelperv2 worktree at migration time (HEAD eb211f2 plus its existing uncommitted packet/UI work). The worktree is a source reference, not a Gradle dependency.

| Feature | Decision | Boundary/risk |
| --- | --- | --- |
| Discovery | Retain | Keep loaded Pokémon observations/filtering. Remove screenshot capture dependencies. No automatic hardcoded server commands. |
| Dungeon Discovery | Retain with configurable rules | Preserve bounded scanning and geometry; classify using opt-in profile data, not Academy literals in Java. |
| Type Chart | Retain | Reuse standard type arithmetic and matrix selection. Open an independent Meteor-themed window, not the old Strategy/catalog navigation. |
| Battle Matchups | Retain, ABI-sensitive | Preserve calculations over revealed facts. Cobblemon screen hooks/native icons require explicit compatibility tests. |
| PC Autosort | Deferred | Separate behavior/safety audit before porting. |
| Breeding | Exclude | No engine/runtime/GUI automation migration. |
| Catalog UI / Shop / GTS | Exclude | Read-only species/move metadata needed by retained functions is not the discarded catalog UI. |
| Old custom UiUtils | Exclude | Tools addon uses separately selected upstream components. |
| Packet file recorder | Exclude | User explicitly does not require file export. |

## Server-specific facts found in old Java code

- Dungeon dimension `dungeons:dungeons` and paper model IDs 1150, 1151, 1152, 1478.
- Guardian name classification, `_stake` suffix, weapon/loot-name substring matching.
- Hardcoded level threshold and interpretation of appearance aspects.
- Literal `/rtp` and optional CobbleNav spawn-map parsing.
- English/Russian chest-title matching and click-only opened-chest inference.

IDs/names/model mappings belong in user-selected data presets. Custom protocol parsing belongs in optional adapters. No match on missing facts, no command merely because a server resembles Academy.

## Existing assumptions not promoted to guarantees

- Client resource spawn pools do not establish current server rarity/probability.
- Missing ownership getters do not establish a wild Pokémon.
- An observed click or matching GUI title does not establish successful loot collection.
- Remembered markers are not fresh live entities.
- Class/method compatibility with the installed Cobblemon fork does not establish compatibility with all Cobblemon versions.
- Type matchup ranking is not a complete damage simulator.

## UI boundary

Settings/lifecycle registration belongs to the Meteor addon; rule evaluation and type computations do not depend on its UI. UI conventions are shared, but neither addon imports the other. Native screens retain input ownership outside overlay bounds. In-game verification remains required after compile/test success.

# Default Discovery audit — 2026-09-22

Scope: current KRU source, installed CobbleNav 2.2.5 ABI, targeted unit tests.
Read-only audit of Cobblemon code; no gameplay or server writes. No live-render
verification. Installed KRU is still 0.3.1; built 0.3.3 is not installed.

## Confirmed findings

1. **Server rarity reader is incompatible with installed CobbleNav.**
   `discovery/CobblemonSpawnMapPacketReader.java:26-36` expects
   `getWeightedBucket()` and entries with `getData()/getResult()/getBucket()`.
   `javap` on installed `cobblenav-fabric-2.2.5.jar` proves SpawnMapPacket instead
   exposes `getBucketName()` and `getSpawnDataList()` containing SpawnData with
   `getRenderable()`, `getSpawnAspects()`, `getSpawnChance()` etc. The expected
   accessors are absent. `DiscoveryDomainTest` uses fakes with the obsolete
   shape, so its green result does not establish installed ABI compatibility.
   Additionally `read()` reflectively calls the Minecraft accessor string
   `payload`; this boundary needs an intermediary-runtime test / typed call.
   Do not compensate with bundled spawn rarity and call it server evidence.

2. **Radiant branch never receives data.**
   `pokemon/CobblemonPokemonReader.java:24-25` always constructs radiant=null.
   `CobblemonObservationAdapter` copies that into observations. Aspects are
   collected by the reader but discarded at the observation boundary.
   A server-specific aspect meaning belongs in an explicit opt-in profile,
   not in universal Cobblemon species logic.

3. **HUD settings are overridden on activation.**
   `Discovery.onActivate()` sets global `Hud.active=true`, then
   `ensureHudElement()` resets an existing element to Left/Top at (8,80).
   This affects the user's global HUD choice and custom position. Automatic
   Meteor activation callbacks at world boundaries can repeat this behavior.

4. **HUD limit setting is misleading.**
   `CobblemonDiscoveryHud.limit` accepts 1–16 but `visibleObservations()` first
   limits the source to `MAX_REMEMBERED=8`. Values 9–16 cannot display more rows.

5. **Requested deep collection/profile editor is not implemented.**
   PokemonObservation stores entity ID, species, shiny/radiant, rarity,
   classes/labels, position and timestamp. No individual level, gender, form,
   owner/wild state, HP, ability, moves or IV evidence is carried into this feed.
   Even registry-derived type information is not included. The module still
   registers as `discovery`, not Pokémon Discovery. Missing server/private
   individual facts must remain unavailable, not be guessed from species.

6. **Fixed configuration remains.**
   Scan radius 256; index cap 128; feed cap 8; display age 30 seconds; fixed
   text scale 1.5 and hardcoded rarity/class colors. There is no editable
   observation-rule profile in this module. These are not all bugs, but they
   fall short of the earlier configurability request.

7. **Filter composition is not AND across all visible settings.**
   Exact species intentionally overrides rarity/class/appearance. Separately,
   selecting Shiny-or-Radiant returns from the appearance branch before checking
   minimum rarity, although the rarity setting remains visible. Make the
   intended composition explicit in UI/tests before changing this behavior.

8. **Per-frame entity lookup work is repeated.**
   Both render passes scan loaded IDs, and `findEntity` scans the whole world
   for every selected entity. This is a code-level performance risk, not a
   measured live lag diagnosis. Index by entity ID / use world's lookup API.

## Already present

- Client-visible Pokémon scanning, exact species matching, shiny filter,
  registry species classification, own nametags/tracers and bounded HUD feed.
- World-scoped observation index: no remembered entity IDs across world objects.
- Rarity separates server evidence from local resource data; UNKNOWN is not
  silently promoted to common/rare.
- No commands or automatic gameplay actions from Discovery.

## Verification

`./gradlew test --offline --tests '*Discovery*' --tests '*Rarity*' --console=plain`
passed: 32 tests, zero failures/errors/skips. This includes four Dungeon
lifecycle tests but is not a full DungeonDiscovery audit. Installed CobbleNav
ABI was independently inspected with javap; those mismatches are not covered
by the existing fake-based tests.

Recommended next change: correct/test the exact optional CobbleNav adapter,
preserve HUD placement/global toggle, reconcile limits and filter UI, then
extend Pokémon observations with only actual client-provided facts. Keep
server semantics in opt-in profiles. No Discovery implementation changed here.

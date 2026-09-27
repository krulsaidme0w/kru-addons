# Academy observation profiles

Dungeon Discovery ships with the Academy preset enabled by default. On first
use it seeds `config/kru-cobblemon/profiles/academy-example.json` if absent;
existing profile files are not overwritten. Edit it for your resource pack/server,
or disable `profile-enabled` in Dungeon Discovery. The old `config/kru-cobblemon/dungeon.json` location is
accepted only as an explicit legacy import. Set `profile-file` to a different filename
to select another file. Use **Reload profile** after editing. Disabling the
profile or loading invalid/missing JSON clears previous rules and target caches.
Server updates can invalidate the preset's rules. It does not send server
commands or connect to a server automatically.

The generated file is version 2:

```json
{
  "version": 2,
  "settings": {
    "scanIntervalSeconds": 5,
    "visualRange": 1024,
    "crosshairInspection": false,
    "verboseLogs": false
  },
  "categories": [{"id":"rare_loot","name":"Rare loot","enabled":true,
    "color":[255,170,110,220],"boxes":true,"tracers":false,
    "nametags":true,"arrows":true,"maxDistance":256}],
  "rules": [{"id":"example","enabled":true,"categoryId":"rare_loot",
    "priority":10,"label":"Rare {observation.name}",
    "all":[{"field":"registry_id","equals":"minecraft:chest"}]}]
}
```

`settings` is optional and controls the runtime without adding noisy Meteor
controls: `scanIntervalSeconds` (1–3600), `visualRange` (1–1024),
`crosshairInspection`, and `verboseLogs`. Category `name`, rule `label`,
colors, boxes, tracers, nametags, arrows, and per-category `maxDistance` stay
in the profile. Nothing is inferred from a technical entity ID when a display
name or label is available.

Each rule has `id`, `enabled`, `categoryId`, `priority`, `label`, and `all`, a
nonempty list of predicates. A predicate has `field` and exactly one of
`equals` (string, boolean or number), `min` (number), `suffix`, `contains`, or
`token` (strings). Strings are case-sensitive. `token` matches an entire
comma-delimited aspect, not a substring. Every predicate must match.
The first matching rule wins. Missing values never match, including negative or
false checks. No regex, method lookup, or executable predicates are supported.

Category IDs are user-defined; there is no fixed dungeon enum. The bundled v1
example is migrated once to the five legacy categories, after which you can
rename/add categories and change their colors and visual switches.
Generic facts: `kind`, `dimension`, `registry_id`, `name`, `x`, `y`, `z`.
Entity facts include `item_id`, `model_data`, `head_item_id`, `head_model_data`
when those items/components exist. Pokémon facts include `species`, `form`,
`level`, `shiny`, `aspects`, `wild`, `ownerUuid` when the client can prove them.
Missing ownership APIs do not prove wildness. Display names are not registry IDs.

Labels use Starscript, for example `{observation.name}` or
`{observation.species} Lv.{observation.level}`. Labels compile on load and run
only after predicates match, on a fresh bounded scalar snapshot. No Meteor
globals, functions, commands, or live entity objects are exposed. Missing label
values use Starscript's natural `null` semantics; use a conditional such as
`{observation.name == null ? 'Unknown' : observation.name}` for a custom fallback.
Runtime label errors fall back to the rule's ID. Invalid expressions
reject the profile with a diagnostic.

Limits: 128 KiB per file, 128 rules, 16 predicates per rule, 512 characters per
label expression, at most 64 examined facts of at most 256 characters, 128 output characters.
Numeric predicates/facts outside ±9,007,199,254,740,991 are rejected/unavailable
to prevent different integers collapsing to the same Starscript number.
Bundled Cobblemon spawn pools are local reference data, not server probabilities.

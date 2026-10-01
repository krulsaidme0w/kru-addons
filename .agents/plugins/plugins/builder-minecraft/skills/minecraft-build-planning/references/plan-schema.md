# Builder staged plan contract

The MCP tool `inspect_build_plan` accepts a strict JSON object with only these root fields:

```json
{
  "schemaVersion": 1,
  "id": "2d7dfc2e-130a-4adb-9164-e929f37504b6",
  "title": "Lake pavilion",
  "revision": 1,
  "palette": {
    "frame": {"id": "minecraft:dark_oak_log", "properties": {"axis": "y"}},
    "floor": {"id": "minecraft:spruce_planks", "properties": {}}
  },
  "stages": [
    {
      "id": "structure",
      "title": "Support frame",
      "after": [],
      "operations": [
        {"op": "PLACE", "position": {"x": 0, "y": 0, "z": 0}, "target": "frame"},
        {"op": "REPLACE", "position": {"x": 1, "y": 0, "z": 0}, "expected": {"id": "minecraft:oak_planks", "properties": {}}, "target": "floor"},
        {"op": "REMOVE", "position": {"x": 2, "y": 0, "z": 0}, "expected": {"id": "minecraft:dirt", "properties": {}}}
      ]
    }
  ]
}
```

Coordinates are integer offsets from the selected bounds' minimum corner. The validator constrains them to the currently selected and scanned region. `PLACE` requires the cell to be known air and has no `expected` field. `REPLACE` requires an exact expected current block state and a target palette key. `REMOVE` requires an exact expected current state and has no target. Block states use exact namespaced IDs and all relevant legal properties. The palette is a map from short plan-local keys to states. A plan contains at most 32,768 final assignments and 16 MiB encoded JSON.

Stages are applied in order; `after` may only reference an earlier stage. A cell may be assigned at most once within a stage; a later stage can deliberately transition that cell again if its expected state follows the earlier transition. Unknown/unloaded cells, stale revisions, unsupported block entities, conflicts, invalid states and out-of-bounds coordinates fail closed. The tool's inspection result returns the validation token, counts, diagnostics, and schematic. Pass that exact token and identity to `publish_build_preview` only after explicit user approval. Publishing changes Builder's preview, not Minecraft blocks.

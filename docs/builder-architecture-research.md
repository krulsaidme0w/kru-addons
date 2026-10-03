# Builder architecture research

## What existing systems do differently

- `minecraft-agentic` gives the model parameterized building operations such as walls,
  windows, roofs, rings and boxes. The operation expander owns gap-free geometry,
  clamping, block validation and caps; the model does not enumerate thousands of cubes.
- APT separates architectural decomposition from execution and uses multimodal input,
  memory and reflection. The useful part for Builder is the reflection loop: compare a
  blueprint and a rendered view, then produce a coordinate-addressable revision.
- Axiom exposes shape/editor tools and masks instead of forcing manual block placement.
  This confirms that massing and structural edits need a higher-level vocabulary.
- Litematica-GPT separates a planner from a compact compiler/DSL and only loads the
  compiled schematic after the plan is inspectable.

## Builder implementation

The local Builder server now runs an architecture review during `inspect_build_plan`.
It scores staged composition, palette roles, spatial depth, repeated rhythm, furniture
vocabulary and small-scale accents. A failed review cannot be published to the Minecraft
hologram. The archive viewer displays the score and warnings, and every inspection result
contains the loopback viewer URL.

The intended pipeline is:

```text
brief -> composition -> palette roles -> staged plan
      -> exact Minecraft validation -> local server viewer/review
      -> revision -> Builder hologram preview -> world changes (future, explicit)
```

The next extension should be a small, versioned operation vocabulary (`wall`, `window`,
`roof`, `terrace`, `furniture_zone`, `trim`) compiled into exact PLACE operations before
the existing validator. This keeps geometry deterministic while leaving style decisions
to the planner.

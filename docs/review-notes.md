# Migration review

Read-only source review is separate from compile/test and live Minecraft checks.

## Artifact gate (resolved and tested)

Nine fixture tests cover external Meteor dependency, no embedded implementation
classes/nested JARs, no sibling dependency or classes, no Cobblemon dependency in
tools, and a real Meteor entrypoint with authors metadata.

## Observation rules (resolved and tested)

- Reject numbers outside the supported safe range rather than silently rounding
  adjacent large integer IDs to the same predicate value.
- Bound examined facts, not just stored facts; an empty profile does no traversal.
- Use Starscript's actual expression semantics instead of regex dependency
  extraction. Null conditionals, whitespace and literal text are regression tested.

## Final integration review (resolved and tested)

- Discovery must invalidate observations across world identity changes, including
  before HUD/render reads; join/leave handling alone does not cover dimensions.
- A stored Tera type is not evidence that authoritative current battle types
  should be replaced with baseline species types. Missing current types remain
  unknown unless active battle evidence proves otherwise.
- Optional native battle capability checks must also contain linkage failures.

Final unrestricted test/remap run passed with 234 tests, no failures/errors/skips,
including the installed-fork bytecode ABI checks. World references are released
on module deactivation/disconnect. Both artifacts and all nine boundary fixtures
passed subsequent validation.

## Explicit remaining product gaps

- Native battle pane is retained as interim presentation, not unified Meteor UI.
- No in-game verification or official-Cobblemon compatibility guarantee yet.
- Tools addon behavior is implemented; live interaction and focus checks remain.

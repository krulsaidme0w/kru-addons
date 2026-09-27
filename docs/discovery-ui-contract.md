# Discovery UI contract

Scope: Pokémon Discovery and profile-driven Discovery only. Mode: Operate.

## Direction contract

THESIS: inspect what the client actually knows, then turn that observation into an explicit rule.

OWN-WORLD: existing Meteor theme, font, title bar and input controls; neutral compact rows, category color only for classification.

STORY: find an object, inspect its value and source, preview a rule, explicitly apply or save it.

FIRST VIEWPORT: search above bounded observations list; selected facts beside it. Narrow view switches to a detail page with Back. Generic editor exposes Observations, Rules, Categories, Profile; errors remain beside the operation that failed.

FORM: user-pinned list/detail tool inside existing Meteor world; no visual-world seed needed. Reuse existing overlay host for cursor/drag/scale and Meteor widgets for editing.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

Existing Meteor remains visual authority; no replacement global DESIGN.md or new raster assets are required for this extension. Live Minecraft screenshots are required before claiming visual validation. Source/layout tests alone must be reported as such.

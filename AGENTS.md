# Boundaries

- Never edit ../cobbhelperv2 or replace installed mods as a side effect of development.
- Meteor is the official external 0.5.8 dependency, not local source or a copied jar from cobbhelperv2.
- cobblemon-addon and tools-addon have independent Gradle roots, namespaces and artifacts. No cross-addon imports, project dependencies or runtime dependencies.
- Only the Cobblemon addon requires Cobblemon. No Academy hard dependency.
- Reuse Meteor UI/components; do not alter its core tabs/module visibility. Windows and contextual overlays must have explicit input ownership.
- Rules observe client-visible facts only. Missing facts never count as proof. Server-specific IDs, item model numbers, names and dimensions belong in editable presets. Academy presets ship enabled by default, as documented in README.
- Preserve upstream implementations where applicable. Document copied sources, license notices and every behavior-changing adaptation.
- A successful unit test/build is not proof of live Minecraft compatibility. Report untested runtime paths explicitly.
- PC Organizer and current UI Utils are maintained features. Do not migrate Breeding, catalog UI, Shop, GTS or unrelated legacy automation.
- Never commit local Minecraft configuration, logs, accounts or personal absolute paths. Preserve upstream license notices and attribution.

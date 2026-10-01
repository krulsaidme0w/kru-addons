# Builder Block Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Builder's Codex agent search the installed block registry in small pages and request a compact, locally rendered preview for one exact block.

**Architecture:** Register three fixed client-executed Codex App Server dynamic tools per thread. A pure catalog owns search, paging and limits; a Minecraft adapter supplies registry/localized metadata/state properties and a renderer supplies one preview image. The server-request handler dispatches only those names and rejects everything else; existing proposal validation remains unchanged.

**Tech Stack:** Java 21, Minecraft 1.21.1, Fabric Loader 0.16.14, Meteor Client 0.5.8, Gson, Codex CLI 0.153.4 app-server, Gradle/JUnit.

**Spec:** `docs/superpowers/specs/2026-09-30-builder-block-catalog-design.md`

## Global Constraints

- The App Server `dynamicTools` API is experimental; prove compatibility with pinned CLI `codex-cli 0.153.4` before enabling it.
- `search_blocks` returns at most 20 concise results per page; `describe_block` accepts one exact registry ID and returns one preview.
- The feature is read-only and may inspect only the local block registry and render the requested item preview.
- Keep shell, filesystem, network, MCP, plugins, external integrations, world reads, container reads and block-entity NBT unavailable.
- Preview uses loaded Minecraft client assets/resource packs, target size 128x128 PNG, maximum 128 KiB raw PNG bytes, with no world screenshot or file path in the tool result.
- Existing local plan validation remains authoritative; Builder never places or breaks blocks.
- Do not install the addon or modify the game instance as part of implementation.

## Review Focus

- Pinned CLI rejects dynamic tool registration or image content: protocol compatibility tests and a no-inference local app-server probe must fail closed before enabling the feature (Task 1).
- Malformed, oversized, negative or unbounded query/page inputs: strict decoder tests reject them before registry access (Tasks 1-2).
- Duplicate localized names across mods: namespace and exact registry ID remain the only identities (Task 2).
- Missing item model, resource reload or disconnect during preview: return a bounded tool error and never leak stale image/world data (Task 3).
- Stale/cancelled tool calls or any non-allowlisted server request: no catalog execution, and non-catalog requests remain rejected (Tasks 1 and 4).

---

### Task 1: Define and gate the dynamic-tool protocol

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/agent/BuilderDynamicTools.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/agent/BuilderDynamicToolsTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/agent/CodexProcessTest.java`

**Interfaces:**
- Produces `BuilderDynamicTools.definitions(): JsonArray`, `BuilderDynamicTools.dispatch(String name, JsonObject arguments): JsonObject`, and a constructor-injected handler `BuilderDynamicTools.Handler.call(String, JsonObject): JsonObject` for Minecraft-backed execution. `dispatch` validates exact tool name and arguments before calling the handler; result is the App Server `DynamicToolCallResponse` shape `{success:boolean,contentItems:[{type:"inputText",text:"..."},{type:"inputImage",imageUrl:"data:image/png;base64,..."}]}`.
- `CodexProcess` receives the `BuilderDynamicTools` instance at construction. It registers exactly its definitions in `thread/start.dynamicTools`; on `item/tool/call`, it validates current `threadId`/`turnId`, dispatches the exact allowlisted name, and replies to that request ID. The existing rejection path remains for every other server request.

- [x] **Step 1: Write failing tests for definitions and safe dispatch**
  - `BuilderDynamicToolsTest.registersOnlyThreeStrictReadOnlyTools`: names are `list_block_mods`, `search_blocks`, `describe_block`; all argument schemas disallow additional fields and bound strings/integers.
  - `rejectsUnknownToolAndUnexpectedArguments`: handler is not called for an unknown name, extra field, missing field, bad JSON type, or out-of-range page.
  - `returnsBoundedTextAndImageContentItems`: payload rejects more than one image, non-data image URLs, images over 128x128 or 128 KiB raw bytes, and serialized result over the 256 KiB response cap.
  - `CodexProcessTest.registersToolsAndAnswersOnlyCurrentToolCall`: thread start carries exactly the definitions; a matching request receives a response; unrelated server requests still receive `-32601`.
  - `rejectsStaleTurnAndUnknownMethodWithoutDispatch`: stale IDs and other server-request methods never invoke the handler.
- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.agent.BuilderDynamicToolsTest --tests dev.krul.builder.agent.CodexProcessTest)`

Expected: compilation/test failures because the dispatcher and App Server tool flow do not exist.
- [x] **Step 3: Implement strict tool descriptors and per-call routing**
  - Add the fixed names and JSON schemas; cap text input at 128 characters, page at 0..10,000, serialized tool output at 256 KiB, and image output at one PNG no larger than 128x128 and 128 KiB raw bytes. Emit image results as `inputImage` with a PNG data URI in `imageUrl`.
  - Include a `dynamicTools` array in each new `thread/start`; do not add MCP configuration or change global Codex settings.
  - In `CodexProcess.awaitTurn`, route only `item/tool/call` whose thread and active turn match. Preserve JSON-RPC request IDs exactly. On cancellation, turn change, malformed params or handler failure, return bounded failure content without executing a second time.
  - Treat a CLI error mentioning unsupported `dynamicTools` at `thread/start` as `Connection.INCOMPATIBLE`; do not retry with a less isolated configuration.
- [x] **Step 4: Run the focused tests and verify fail-closed behavior**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.agent.BuilderDynamicToolsTest --tests dev.krul.builder.agent.CodexProcessTest)`

Expected: PASS; all non-tool server requests remain rejected.
- [x] **Step 5: Probe the pinned CLI without starting a model turn**
  - Add/run a local integration check using the isolated Builder profile: launch exact `codex-cli 0.153.4`, initialize app-server, call `thread/start` with the three definitions and an empty `turn` history, then close it. Do not call `turn/start`, change account/config, or contact the model provider.
  - Expected: the CLI accepts the definitions. If not, STOP implementation and report the incompatibility for design review; do not loosen the allowlist or update the pinned version implicitly.
- [ ] **Step 6: Commit the protocol boundary**

  Deferred: `CodexProcess.java` and its test already contain unrelated pending user edits. Per the SDD ledger ruling, do not stage those whole files or commit a mixed change.

Run: `git add builder-addon/src/main/java/dev/krul/builder/agent/BuilderDynamicTools.java builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java builder-addon/src/test/java/dev/krul/builder/agent/BuilderDynamicToolsTest.java builder-addon/src/test/java/dev/krul/builder/agent/CodexProcessTest.java && git commit -m "feat(builder): add isolated dynamic tool protocol"`

### Task 2: Implement bounded catalog search and state details

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalog.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/catalog/BlockCatalogTest.java`

**Interfaces:**
- `BlockCatalog(List<BlockEntry> entries)` builds immutable namespace/name/ID indexes.
- `BlockCatalog.listMods(int page): ModPage` returns at most 20 namespace entries, optional display label, count and next-page marker.
- `BlockCatalog.search(String query, Optional<String> mod, int page): BlockPage` returns at most 20 stable-ID matches and next-page marker.
- `BlockCatalog.describe(String exactId): Optional<BlockDetails>` returns exact ID, localized label, and immutable valid property-name/value lists. `BlockEntry` contains only registry ID, namespace, localized label and valid state properties.
- Task 1's dispatcher converts these domain results to bounded JSON; this task does not know about Codex protocol or Minecraft classes.

- [x] **Step 1: Write failing catalog tests**
  - `listsNamespacesWithCountsAndPagesAtTwentyEntries`: deterministic namespace sort, exact counts, page size cap 20, and no repeated/omitted IDs.
  - `searchesLocalizedNameAndRegistryIdWithinOptionalNamespace`: exact namespace filter; duplicate display names stay distinct by registry ID.
  - `emptyQueryRequiresNamespaceAndUnknownExactIdReturnsEmpty`: unfiltered empty query is rejected, mod-scoped empty query is accepted, unknown ID never fuzzy-matches.
  - `rejectsOversizedQueryNegativePageAndPageOverLimit`: query >128 chars, page <0 or >10,000 rejected.
- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.catalog.BlockCatalogTest)`

Expected: compilation failure because catalog types are not implemented.
- [x] **Step 3: Implement immutable indexes and deterministic paging**
  - Sort by namespace then registry ID; case-fold query with `Locale.ROOT` and match ID or localized label.
  - Define page size as exactly 20; calculate offsets with overflow-safe arithmetic. Return total count and nullable next page, never all entries in a response.
  - Preserve registry IDs as identity; translated/display names are labels only.
- [x] **Step 4: Run catalog tests and verify**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.catalog.BlockCatalogTest)`

Expected: PASS, including boundary and namespace-collision cases.
- [ ] **Step 5: Commit the pure catalog**

  Deferred to avoid mixing this feature with the existing dirty checkout; bundle safe feature files in a later explicit commit.

Run: `git add builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalog.java builder-addon/src/test/java/dev/krul/builder/catalog/BlockCatalogTest.java && git commit -m "feat(builder): add paginated block catalog"`

### Task 3: Connect Fabric registry and render a single block preview

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/minecraft/ClientBlockCatalog.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/minecraft/BlockPreviewRenderer.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/minecraft/ClientBlockCatalogTest.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/minecraft/BlockPreviewRendererTest.java`

**Interfaces:**
- `ClientBlockCatalog.snapshot(): BlockCatalog` reads the current `Registries.BLOCK` and Fabric mod metadata on the Minecraft client thread. It converts each block's default state properties using `Property.name(value)` and enumerates each property's allowed values.
- `ClientBlockCatalog.describe(String exactId): Optional<BlockDetails>` resolves only exact IDs in the current registry.
- `BlockPreviewRenderer.render(String exactId): CompletableFuture<PreviewPng>` runs rendering on the Minecraft render thread and returns PNG bytes plus width/height. `PreviewPng` contains no path.
- Rendering implementation must use Minecraft's baked item model/active resource packs, target 128x128, and never sample the world framebuffer. If these constraints cannot be met with the pinned Minecraft API, stop and report before wiring the preview into Codex.

- [x] **Step 1: Write failing tests for registry metadata and PNG constraints**
  - Bootstrap the Minecraft registry as in `MinecraftPlanRulesTest`; assert registry IDs are identities and every valid state-property value is enumerated without assuming translated labels are unique.
  - `ClientBlockCatalogTest.unknownIdDoesNotResolve`: invalid IDs return empty without fallback.
  - `BlockPreviewRendererTest.previewIsSingleBoundedDecodablePng`: valid fixture returns decodable 128x128-or-smaller PNG; bytes <=256 KiB.
  - `BlockPreviewRendererTest.unavailableItemModelReturnsErrorAndNeverWorldCapture`: missing item/model reports failure and renderer has no world-capture dependency.
- [x] **Step 2: Run the focused tests and confirm they fail**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.minecraft.ClientBlockCatalogTest --tests dev.krul.builder.minecraft.BlockPreviewRendererTest)`

Expected: compilation failures because the Minecraft adapters do not exist.
- [x] **Step 3: Implement registry metadata adapter and GUI-model preview renderer**
  - Read registry and Fabric metadata only on the client thread; use namespace metadata when available, otherwise display the namespace itself.
  - Resolve one exact block item and render its baked GUI item model into an isolated offscreen target. Do not read chunk/world state, disk resource-pack files, block entities, or the live framebuffer.
  - Re-check current client/world/resource generation before completing a scheduled preview; fail if stale. Encode only bounded PNG bytes and return an error if no item model is available.
- [ ] **Step 4: Run focused tests and a client render probe**

  Focused registry/PNG tests pass. The live framebuffer/model appearance check remains pending because no disposable Minecraft client was launched; the user's game was not modified or installed into.

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.minecraft.ClientBlockCatalogTest --tests dev.krul.builder.minecraft.BlockPreviewRendererTest)`

Expected: PASS. In a disposable dev instance only, render one vanilla and one mod block into the preview buffer and inspect dimensions/appearance. Do not install into the user's game.
- [ ] **Step 5: Commit registry lookup and preview rendering**

  Deferred under the same dirty-checkout ruling.

Run: `git add builder-addon/src/main/java/dev/krul/builder/minecraft/ClientBlockCatalog.java builder-addon/src/main/java/dev/krul/builder/minecraft/BlockPreviewRenderer.java builder-addon/src/test/java/dev/krul/builder/minecraft/ClientBlockCatalogTest.java builder-addon/src/test/java/dev/krul/builder/minecraft/BlockPreviewRendererTest.java && git commit -m "feat(builder): render block catalog previews"`

### Task 4: Wire the catalog into Builder and explain model behavior

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/agent/BuilderDynamicTools.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java`
- Modify: `builder-addon/src/test/java/dev/krul/builder/agent/CodexProcessTest.java`
- Modify: `builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java`
- Modify: `builder-addon/README.md`

**Interfaces:**
- `BuilderModule.startRuntime(long generation)` and the executable-setting replacement path create a registry catalog service bound to the active client lifecycle and pass its `BuilderDynamicTools.Handler` to `CodexProcess`.
- `list_block_mods` serializes `ModPage`; `search_blocks` serializes `BlockPage`; `describe_block` serializes exact details plus one `{type:"inputImage",imageUrl:"data:image/png;base64,..."}` item in the exact wire format accepted by the pinned App Server. Confirm PNG modality before wiring; do not guess an undocumented payload shape.
- Prompt context states that the tools are local/read-only, tells Codex to search before inventing modded IDs, describes the 20-result page, and says `describe_block` yields one inventory-model image; it does not grant new authority.

- [x] **Step 1: Write failing end-to-end synthetic protocol tests**
  - `CodexProcessTest.searchToolReturnsOnlyTwentyCatalogEntries`: a fake app-server invokes page 0; response has <=20 entries and no images.
  - `CodexProcessTest.describeToolReturnsOneImageContentItem`: exact ID response contains one valid PNG image item and concise ID/property text.
  - `CodexProcessTest.catalogErrorsDoNotFallbackToFilesystemOrWorld`: missing namespace/ID/model returns bounded tool error; no non-allowlisted request invokes catalog.
  - `BuilderAddonContractTest.catalogToolsRemainReadOnlyAndPlanValidationStillRejectsUnknownBlocks`: catalog tool names/docs contain no mutating action; existing `MinecraftPlanRules` rejection remains covered.
- [x] **Step 2: Run focused tests and confirm they fail**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.agent.CodexProcessTest --tests dev.krul.builder.BuilderAddonContractTest)`

Expected: failures because no registry service is wired to the dispatcher.
- [x] **Step 3: Wire service lifecycle, tool serialization and concise prompt guidance**
  - Inject the active catalog and preview renderer; invalidate in-flight previews on resource reload, world lifecycle changes and Builder deactivation.
  - Require the selected default Codex model to advertise image input whenever catalog previews are enabled; if unsupported, fail closed with an actionable status rather than sending an image it cannot interpret.
  - Do not send catalog entries or previews proactively; only exact dynamic tool calls produce them.
  - Update README data disclosure to say requested registry results/previews are sent to the configured model provider, and tool policy to list the three narrow local reads while continuing to disable MCP and other integrations.
- [x] **Step 4: Run focused tests and verify**

Run: `(cd builder-addon && ./gradlew --no-daemon test --tests dev.krul.builder.agent.CodexProcessTest --tests dev.krul.builder.BuilderAddonContractTest)`

Expected: PASS; canceled/stale calls, malformed tool args and every non-catalog server request fail closed.
- [ ] **Step 5: Commit the integrated feature and disclosure**

  Deferred: README, BuilderModule, CodexProcess and tests contain unrelated user changes; do not commit those mixed files.

Run: `git add builder-addon/src/main/java/dev/krul/builder/agent/BuilderDynamicTools.java builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java builder-addon/src/main/java/dev/krul/builder/agent/CodexProcess.java builder-addon/src/test/java/dev/krul/builder/agent/CodexProcessTest.java builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java builder-addon/README.md && git commit -m "feat(builder): wire searchable block catalog"`

### Task 5: Full regression, packaging, and review

**Files:**
- No new feature files expected. Inspect all catalog/protocol changes from Tasks 1-4.

**Interfaces:**
- Consumes the complete catalog tools, app-server routing, registry adapter and preview renderer.
- Produces a tested distribution artifact only; installation into the user's instance remains out of scope.

- [x] **Step 1: Run full Builder tests, build and distribution validation**

Run: `(cd builder-addon && ./gradlew --no-daemon test remapJar sourcesJar)`

Run: `python3 -m unittest discover -s distribution -v`

Run: `python3 distribution/release.py builder-addon`

Expected: all tests pass; release validation reports matching JAR metadata/checksum. Do not overwrite or install any game files.
- [x] **Step 2: Review security and scope diff**
  - Verify only `item/tool/call` for the three exact names is accepted.
  - Verify environment allowlist, empty Codex working directory, `read-only` sandbox, approval policy and disabled MCP/shell/filesystem/network/plugin settings remain unchanged.
  - Verify search responses contain <=20 text entries; detail responses contain at most one PNG data URI (128x128, <=128 KiB raw) and no path, screenshot, block-entity data or world contents.
  - Verify README accurately describes transmission and that unknown proposal IDs still fail local validation.
- [ ] **Step 3: Commit only feature files**

  Deferred; no files were staged or committed because existing modifications overlap feature files.

Stage the exact files modified by Tasks 1-4 and commit as `feat(builder): add searchable block catalog tools`.

## Handoff

After the plan is approved, use the chosen execution method. Do not install the built addon without a separate explicit user request.

# Builder MCP Block Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Codex a compact, searchable local catalog of the active modpack's blocks, exact state properties, useful construction metadata, and real Minecraft-rendered previews.

**Architecture:** The Fabric client scans registry metadata once per runtime fingerprint and writes a bounded versioned JSON snapshot plus cached PNGs under the instance-owned Builder directory. MCP exposes paginated search and exact descriptions, never a wholesale catalog dump. All Minecraft model rendering stays on the client render thread and occurs on demand.

**Tech Stack:** Java 21, Fabric/Yarn 1.21.1, Gson, existing `BlockCatalog`, `ClientBlockCatalog`, `BlockPreviewRenderer`, MCP server from `2026-10-01-builder-mcp-bridge.md`.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-local-mcp-world-design.md`

## Global Constraints

- Catalog only active registries and resource packs; fingerprint includes game, loader, mod IDs/versions, and pack identities.
- Never send the whole index to Codex; search returns at most 20 items per page.
- Exact IDs and legal property values come from Minecraft, not model-generated text.
- Rendering/index work must not block client render ticks.
- Do not export container contents, arbitrary NBT, or private block-entity data.

## Review Focus

- Duplicate localized labels must remain unambiguous — test same name across namespaces and exact-ID lookup.
- A mod/resource-pack change must not reuse stale previews — test fingerprint invalidation.
- State-property combinations can be huge — test legal-value discovery without Cartesian enumeration.
- Missing or dynamic models must not be presented as exact images — test explicit unsupported/fallback metadata.
- Large catalogs must remain bounded — test paging, snapshot size caps, cancellation, and cache eviction.

---

### Task 1: Extend pure block catalog metadata and search index

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalog.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalogFingerprint.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalogIndex.java`
- Modify: `builder-addon/src/test/java/dev/krul/builder/catalog/BlockCatalogTest.java`

**Interfaces:**
- Produces: immutable `BlockEntry` metadata with exact ID, namespace, display label, item counterpart, sorted properties/legal values, tags and compact construction traits.
- Produces: optional block/item tags, recipe references, default state, RGB map color, light level, fluid flag, collision/outline shape summaries, full-cube/occlusion flags, and block-entity capability; unavailable traits are explicitly absent rather than guessed.
- Produces: `BlockCatalogIndex.search(query, namespace, page, pageSize)` with hard maximum page size 20 and deterministic ordering.
- Produces: stable fingerprint from version strings and sorted resource identities.

- [ ] Add tests for exact ID, localized search, namespace filter, deterministic paging, fingerprint order independence, and changed-mod/resource-pack invalidation.
- [ ] Run the catalog test class and confirm failures.
- [ ] Implement metadata and in-memory index without Minecraft API dependencies; test tags, traits and recipe references as optional fields so missing mod APIs remain explicit.
- [ ] Rerun catalog tests and confirm pass.
- [ ] Commit as `feat(builder): index active block catalog metadata`.

### Task 2: Export and incrementally refresh the local catalog snapshot

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/ClientBlockCatalog.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalogSnapshotCodec.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockCatalogStore.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/catalog/BlockCatalogSnapshotCodecTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/catalog/BlockCatalogStoreTest.java`

**Interfaces:**
- Consumes: Task 1 immutable entries/fingerprint.
- Produces: bounded atomic versioned JSON cache under `<instance>/kru-builder/catalog`; metadata read on client thread, serialization/write on IO executor.
- Produces: explicit states `SCANNING`, `READY`, `STALE`, `FAILED` and a generation token preventing stale writes.

- [ ] Test codec round-trip, corruption, limits, atomic replacement fallback, cache fingerprint mismatch, and cancellation on game reload.
- [ ] Run focused tests and confirm failures.
- [ ] Implement time-sliced registry scan and async persistence; no complete rescan on render tick.
- [ ] Rerun focused tests and Builder test suite; confirm pass.
- [ ] Commit as `feat(builder): persist fingerprinted block catalog`.

### Task 3: Add client-rendered block-state previews

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BlockPreviewRenderer.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/BlockPreviewCache.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/ClientBlockCatalogTools.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/minecraft/BlockPreviewRendererTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/catalog/BlockPreviewCacheTest.java`

**Interfaces:**
- Produces: `render(StateKey key, BooleanSupplier current) -> PreviewResult` on Minecraft render thread; result declares exact/default state and any fallback reason.
- Produces: persistent PNG cache keyed by catalog fingerprint + exact state properties, bounded by entry count and bytes.

- [ ] Test cache key normalization, invalid state requests, stale render cancellation, image bounds, and cache eviction.
- [ ] Run focused tests and confirm failures.
- [ ] Render exact requested legal state via client model renderer; do not enumerate property Cartesian products.
- [ ] Rerun renderer/catalog tests and Builder suite; confirm pass.
- [ ] Commit as `feat(builder): cache rendered modded block previews`.

### Task 4: Expose paginated catalog MCP tools

**Files:**
- Modify: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BuilderMcpServer.java`
- Modify: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BridgeClient.java`
- Test: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BlockCatalogToolsTest.java`
- Modify: `builder-addon/README.md`

**Interfaces:**
- Produces: `list_block_mods(page)`, `search_blocks(query, namespace?, page)`, and `describe_block(id, properties?)`.
- Produces: `describe_block` returns exact legal state properties, available construction facts, and one PNG `image` result; maximum 20 text entries and one image per call.

- [ ] Test schemas, unknown IDs/properties, pagination, image bounds/MIME, and no whole-registry response.
- [ ] Run MCP server focused tests and confirm failures.
- [ ] Wire MCP tools to the paired bridge/catalog APIs and document modpack catalog behavior.
- [ ] Run addon tests, MCP tests, `remapJar`, and `git diff --check`; confirm pass.
- [ ] Commit as `feat(builder): expose searchable modded block catalog`.

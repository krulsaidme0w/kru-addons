# Builder automatic local MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Builder to bounds plus status while it automatically scans and gives Codex loopback-only access to bounded world, block and item data.

**Architecture:** Keep the existing client-thread world scanner, HTTP bridge and stdio MCP companion. Replace the manual scan and challenge/token handshake with idempotent auto-scan and loopback-only local requests, and extend the existing paged block-catalog pattern to items with exact-ID descriptions and rendered icons.

**Tech Stack:** Java, Fabric/Minecraft client APIs, Meteor UI, JUnit 5, MCP Java SDK.

**Spec:** `docs/superpowers/specs/2026-10-02-builder-autonomous-local-mcp-design.md`

## Global Constraints

- Builder remains isolated from the Cobblemon addon; no cross-addon dependencies.
- World access is client-visible, bounded to the selected region, and read-only.
- The bridge binds only to `127.0.0.1`; non-loopback peers are rejected.
- No inventory mutation, commands, player movement, block placement or block breaking.
- Scans remain incremental on client ticks and incomplete snapshots are never served as complete.
- MCP catalog and selection responses remain paginated and size-bounded.
- Plan inspection stays exact-snapshot-bound; hologram publication still requires explicit user approval.

## Review Focus

- Typing bounds must not launch scans for partial or rapidly changing values; test debounce and invalid-input handling in Task 1.
- World/dimension changes must invalidate old evidence but restore matching saved bounds; test reset/rebind behavior in Task 1.
- A local unauthenticated request from a non-loopback peer must not reach data handlers; test rejection in Task 2.
- Registry item entries may have missing icons or no useful translation; test explicit fallback metadata in Task 4.
- Large item registries must not create unbounded MCP output; test pagination and schema limits in Tasks 4–5.

---

### Task 1: Automatic scan scheduling and bounds editing

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/context/BoundsAutoApply.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/context/ScanSession.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/test/java/dev/krul/builder/context/ScanSessionTest.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/context/BoundsAutoApplyTest.java`

**Interfaces:**
- Produces: `BoundsAutoApply.update(String[] coordinates, long nowMillis, Bounds current) -> Optional<Bounds>`; it returns a new valid value only after all six coordinates are stable for 350 ms and parse under the existing 32,768-cell cap.
- Produces: `ScanSession.ensureScan(WorldReader reader) -> boolean`; starts only when bound to a ready world, valid bounds exist, and neither a scan nor complete snapshot exists.

- [x] **Step 1: Add tests** for incomplete/invalid coordinates, stable valid coordinates after 350 ms, changed values restarting the debounce, unchanged values returning no new bounds, auto-scan start, and no duplicate scan on subsequent ticks.
- [x] **Step 2: Run** `./gradlew --no-daemon test --tests 'dev.krul.builder.context.BoundsAutoApplyTest' --tests 'dev.krul.builder.context.ScanSessionTest'` in `builder-addon`; confirm the new API tests fail before implementation.
- [x] **Step 3: Implement** the pure bounds debounce helper and idempotent `ScanSession.ensureScan`; have `BuilderModule.onTick` start the scan automatically when bounds are valid and no snapshot exists, including after context restore.
- [x] **Step 4: Verify** the focused tests pass, including the existing guarantee that world identity changes abandon previous scan evidence.

### Task 2: Loopback-only bridge without pairing

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BuilderBridgeServer.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeProtocol.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeDataSource.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Delete: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgePairing.java`
- Modify/Delete: `builder-addon/src/test/java/dev/krul/builder/bridge/BuilderBridgeServerTest.java`, `BridgePairingTest.java`

**Interfaces:**
- Consumes: Task 1 automatic scan lifecycle.
- Produces: bridge endpoints for status, selection, capture, registries, and plans that require no bearer token but continue rejecting non-loopback clients.
- Removes: `BridgeProtocol.Method.PAIR`, token/challenge request fields, and pairing state API.

- [x] **Step 1: Add bridge tests** proving local data reads succeed without Authorization, the bridge binds only to loopback, the peer policy rejects non-loopback addresses before reaching the source, oversized/invalid bodies remain rejected, and no block mutation endpoint exists.
- [x] **Step 2: Run** `./gradlew --no-daemon test --tests 'dev.krul.builder.bridge.BuilderBridgeServerTest'` in `builder-addon`; confirm the no-token path currently fails.
- [x] **Step 3: Implement** direct authenticated-handler replacement with loopback-checked request handling; remove `/v1/pair`, `/v1/pair/status`, bearer verification and pairing object ownership from `BuilderModule`.
- [x] **Step 4: Verify** focused bridge tests and protocol strict-decoding tests pass; update protocol constructors without relaxing unknown-field rejection.

### Task 3: Minimal Builder UI and live status

**Files:**
- Modify: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/test/java/dev/krul/builder/BuilderAddonContractTest.java`
- Modify: `AGENTS.md`
- Modify: `builder-addon/README.md`
- Modify: `.agents/plugins/plugins/builder-minecraft/skills/minecraft-build-planning/SKILL.md`

**Interfaces:**
- Consumes: Task 1 automatic bounds/scans and Task 2 loopback bridge state.
- Produces: one simple window with six persisted bounds coordinates, dimensions, scan state/progress, and bridge endpoint/status; no Apply, Clear, Scan/Rescan, Allow, Reject, Revoke, or overlay-toggle controls.

- [x] **Step 1: Add/update contract tests** asserting the scan/pairing controls and callbacks are absent while bounds and status remain visible.
- [x] **Step 2: Run** `./gradlew --no-daemon test --tests 'dev.krul.builder.BuilderAddonContractTest'` in `builder-addon`; verify the new UI contract fails against the current screen.
- [x] **Step 3: Implement** debounced auto-save of valid bounds from the six fields, automatic scan status display, and concise bridge state/error labels; preserve generic window close/collapse only if the Meteor window framework requires it.
- [x] **Step 4: Update** Builder documentation and repository instructions so they describe automatic scans and loopback-only access and no longer direct contributors to add manual Scan or explicit pairing.
- [x] **Step 5: Verify** UI contract tests and bounds persistence tests pass.

### Task 4: Paged item catalog with real icons

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/catalog/ItemCatalog.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/minecraft/ClientItemCatalog.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/minecraft/ItemPreviewRenderer.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeProtocol.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeDataSource.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/bridge/BuilderBridgeServer.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderBridgeDataSource.java`
- Create: `builder-addon/src/test/java/dev/krul/builder/catalog/ItemCatalogTest.java`
- Create/Modify: tests for client item extraction and preview fallback under `builder-addon/src/test/java/dev/krul/builder/minecraft/`

**Interfaces:**
- Produces: `ItemCatalog.listMods(page)`, `search(query, namespace, page)`, and `describe(exactId)` using the existing 20-result page size and bounded query/page limits.
- Produces: bridge methods `LIST_ITEM_MODS`, `SEARCH_ITEMS`, `DESCRIBE_ITEM` and routes `/v1/catalog/items/mods`, `/v1/catalog/items/search`, `/v1/catalog/items/describe`.
- Item descriptions expose registry ID, namespace, localized name, item class/category where available, and a real item-renderer PNG when a model exists; missing models are explicit metadata and do not fail the whole description.

- [x] **Step 1: Add catalog tests** for deterministic namespace paging, exact namespace search, case-insensitive ID/name search, exact-ID description, invalid page/query rejection and missing-item lookup.
- [x] **Step 2: Run** the focused catalog tests and confirm expected failures before implementation.
- [x] **Step 3: Implement** immutable paged `ItemCatalog` and client-thread registry adapter over `Registries.ITEM`; use actual item-stack model rendering through a private framebuffer, never the world framebuffer.
- [x] **Step 4: Add bridge tests** for all three item endpoints, bounded payloads, strict protocol method validation and image omission when an item has no renderable model.
- [x] **Step 5: Verify** catalog, bridge and item-preview tests pass.

### Task 5: MCP tool surface and automatic connection

**Files:**
- Modify: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BridgeClient.java`
- Modify: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BuilderMcpServer.java`
- Modify: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BridgeClientTest.java`
- Modify: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BuilderMcpToolsTest.java`
- Modify: plugin MCP configuration and Builder MCP README only if endpoint defaults or setup text require changes.

**Interfaces:**
- Consumes: Task 2 unauthenticated loopback protocol and Task 4 item-catalog endpoints.
- Produces MCP tools `list_item_mods`, `search_items`, and `describe_item`; removes `pair_local_minecraft` and all companion pairing state. Existing block, selection, plan-inspection, archive and explicit preview-publish tools remain.

- [x] **Step 1: Add tests** for item tool names/schemas, paginated item calls, PNG forwarding, absent pairing tool, and a BridgeClient that makes status/catalog requests without Authorization.
- [x] **Step 2: Run** focused MCP tests and verify they fail against the current pairing tool and request client.
- [x] **Step 3: Implement** no-auth `BridgeClient` requests and bounded MCP item tools; update tool instructions to say data is loopback-local, plans are preview-only, and publication requires explicit approval.
- [x] **Step 4: Verify** the MCP tests pass and output remains below the MCP tool-response cap.

### Task 6: Full verification and install

**Files:**
- Verify: all changed Builder addon and MCP source/tests; installed artifact only after successful builds and confirmation Minecraft is closed.

- [x] **Step 1: Run** `./gradlew --no-daemon clean test remapJar` in `builder-addon`.
- [x] **Step 2: Run** `./gradlew --no-daemon -p ../builder-mcp-server test shadowJar` from `builder-addon`.
- [x] **Step 3: Review** `git diff --check`, `git status --short`, and the full relevant diff; ensure no unrelated user changes were staged or modified.
- [ ] **Step 4: Build and install** the updated addon and MCP plugin artifact only when target installation paths are established, verify hashes, and report any live Minecraft checks not performed.

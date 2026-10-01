# Builder MCP Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Codex use a local MCP stdio server to inspect a running Builder client through a bounded, authenticated loopback bridge.

**Architecture:** Add a separate Java 21 MCP companion using the official MCP Java SDK 2.0.1. The Fabric addon owns the authoritative snapshot and an HTTP loopback bridge; the companion maps bounded MCP calls to typed bridge requests and returns text or PNG results. A one-time in-game pairing authorizes the companion; all world access remains read-only.

**Tech Stack:** Java 21, Gradle, Fabric 1.21.1, Meteor Client, MCP Java SDK 2.0.1, JDK HTTP server/client, Gson.

**Spec:** `docs/superpowers/specs/2026-10-01-builder-local-mcp-world-design.md`

## Global Constraints

- Bind bridge and local companion endpoints to loopback only.
- The game client is authoritative; access only explicitly selected bounds and loaded client chunks, with a hard selection cap of 32,768 cells (`ScanCursor.MAX_CELLS`).
- No world mutation, command execution, or movement tools.
- All bridge work touching Minecraft objects runs on the client thread; expensive IO and MCP transport run off-thread.
- MCP requests and responses have strict size, coordinate, and timeout limits.
- Do not edit global Codex config, auth, or skills during install/setup.

## Review Focus

- A disconnected or changed world must never leak a previous session's snapshot — test disconnect, world switch, and stale generation rejection.
- Unknown/unloaded cells must stay unknown rather than become air — test chunk-boundary and unavailable chunk reads.
- A bridge caller outside loopback or without a valid one-time pairing token must be rejected — test unauthorized requests and token rotation.
- MCP stdio must contain only protocol frames, never logs — test stdout framing while diagnostics go to stderr.
- A slow/stale client-thread request must time out without later publishing data — test timeout cancellation and generation checks.
- Codex and Minecraft may run in different Windows/WSL network namespaces — test the user's actual topology while keeping the bridge local-only and authenticated.

---

### Task 1: Define bridge protocol and pure validation

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeProtocol.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgeRequestValidator.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/bridge/BridgeProtocolTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/bridge/BridgeRequestValidatorTest.java`

**Interfaces:**
- Produces: versioned request/response records for `status`, `read_selection`, `capture_view`, and `pair`; every response carries `worldGeneration` and `snapshotRevision`.
- Produces: `BridgeRequestValidator.validateSelection(Bounds requested, Bounds selected, int maxCells)` and bounded integer-page/PNG validation.

- [ ] Write tests for supported protocol version, invalid/oversized JSON, requested bounds outside selection, volume overflow, and negative/out-of-range page numbers.
- [ ] Run `cd builder-addon && ./gradlew test --tests dev.krul.builder.bridge.BridgeProtocolTest --tests dev.krul.builder.bridge.BridgeRequestValidatorTest` and confirm failures.
- [ ] Implement immutable protocol records and fail-closed validation without Minecraft dependencies.
- [ ] Rerun the focused tests and confirm pass.
- [ ] Commit as `feat(builder): define local bridge protocol`.

### Task 2: Add paired loopback bridge lifecycle to the addon

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/bridge/BuilderBridgeServer.java`
- Create: `builder-addon/src/main/java/dev/krul/builder/bridge/BridgePairing.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/BuilderModule.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/ui/BuilderScreen.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/bridge/BridgePairingTest.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/bridge/BuilderBridgeServerTest.java`

**Interfaces:**
- Consumes: Task 1 protocol records and validators.
- Produces: `BuilderBridgeServer.start(BridgeDataSource)`, `close()`, and `BridgeDataSource.status/readSelection/captureView` callbacks. Server lifecycle is tied to Builder activation/world session and invalidates outstanding requests on change.
- Produces: one-time pairing challenge shown in Builder with explicit Allow/Reject; successful approval issues an instance-local bearer token, accepts only loopback requests, and supports revocation.

- [ ] Write tests for loopback-only bind, pairing success/replay/expiry/revocation, token-required routes, lifecycle shutdown, and no response after generation changes.
- [ ] Run focused bridge tests and confirm failures.
- [ ] Implement JDK HTTP routes with explicit request-size/time limits; show pending pair requests in Builder, keep diagnostics off response payloads, and never log secrets.
- [ ] Wire server activation/deactivation and world-generation changes from `BuilderModule` without blocking render ticks.
- [ ] Rerun bridge tests and `./gradlew test`; confirm pass.
- [ ] Commit as `feat(builder): expose paired loopback world bridge`.

### Task 3: Implement client-thread snapshot and capture adapters

**Files:**
- Create: `builder-addon/src/main/java/dev/krul/builder/bridge/BuilderBridgeDataSource.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/ClientWorldReader.java`
- Modify: `builder-addon/src/main/java/dev/krul/builder/minecraft/WorldCapture.java`
- Test: `builder-addon/src/test/java/dev/krul/builder/bridge/BuilderBridgeDataSourceTest.java`

**Interfaces:**
- Consumes: live `ScanSession`, `WorldSnapshot`, bounds and world generation; bridge callback API from Task 2.
- Produces: bounded relative+absolute cell records, loaded/unknown counts, stable snapshot revision, and current-view PNG capture. No camera or world mutation.

- [ ] Write adapter tests for stale snapshots, exact block IDs/properties, unknown cells, byte caps, and client-thread dispatch/cancellation.
- [ ] Run focused tests and confirm failures.
- [ ] Implement data source using existing scanner/capture paths; do not rescan or recapture on every render tick.
- [ ] Rerun focused tests and `./gradlew test`; confirm pass.
- [ ] Commit as `feat(builder): serve bounded world snapshots to MCP`.

### Task 4: Create MCP Java companion and stdio tools

**Files:**
- Create: `builder-mcp-server/settings.gradle`
- Create: `builder-mcp-server/build.gradle`
- Create: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BuilderMcpServer.java`
- Create: `builder-mcp-server/src/main/java/dev/krul/builder/mcp/BridgeClient.java`
- Create: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BridgeClientTest.java`
- Create: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BuilderMcpToolsTest.java`

**Interfaces:**
- Produces: MCP tools `minecraft_status`, `read_selection`, `capture_view`, and `pair_local_minecraft` with strict JSON schemas and response caps.
- Produces: `BridgeClient` typed methods mapping to the Task 1 bridge protocol; status/read methods are annotated read-only.

- [ ] Write tests for tool schemas, maximum snapshot page size, image MIME/size, bridge timeout, connection failure, and protocol version mismatch.
- [ ] Run `cd builder-mcp-server && ../builder-addon/gradlew -p . test` and confirm failures.
- [ ] Add the official MCP Java SDK pinned at 2.0.1 and implement stdio server; ensure logs use stderr only.
- [ ] Build a self-contained `builder-mcp-server` runtime artifact without Minecraft/Meteor classes.
- [ ] Run server tests and inspect stdio initialize/tools/list/call with MCP Inspector; confirm pass.
- [ ] Commit as `feat(builder): add local MCP world inspection server`.

### Task 5: Verify end-to-end bridge lifecycle

**Files:**
- Test: `builder-mcp-server/src/test/java/dev/krul/builder/mcp/BridgeEndToEndTest.java`
- Modify: `builder-addon/README.md`

- [ ] Test pair → status → bounded selection → screenshot → world change → stale call rejected using an in-process fake bridge.
- [ ] Smoke-test from the actual Codex runtime to the Windows Prism instance; if loopback namespaces differ, document the supported local pairing/configuration path before broadening bind addresses.
- [ ] Run both addon and MCP server test suites; confirm pass.
- [ ] Document first pairing, disconnect behavior, selection limits, privacy, and local-only endpoint.
- [ ] Commit as `test(builder): verify local MCP bridge workflow`.

# KRU Builder

KRU Builder is a client-side world-inspection and preview addon for Minecraft
1.21.1, Fabric, Java 21 and Meteor Client 0.5.8. It has no embedded chat or
model client: the addon starts a loopback bridge, scans the selected region on
request, and renders validated plans supplied by the separate MCP server.

## Use

1. Enable Builder. It starts the loopback bridge and opens the bounds window.
2. Enter six numeric coordinates. After the values stop changing briefly,
   valid bounds are saved per world/server and dimension and scanned
   automatically. Editing bounds immediately invalidates the prior snapshot.
3. Start the separate `builder-mcp-server`/Codex MCP integration on the same
   machine. It connects to the already-running local bridge automatically; no
   in-game pairing or scan action is needed.
4. Ask the external agent to inspect the area, block/item catalogs and prepare
   a staged plan. The addon validates and displays it as a hologram preview
   only.

The minimal panel contains only bounds, dimensions, scan status and local MCP
bridge status. Use the `open-builder` keybind to reopen it. Scan errors and
invalid bounds are reported in the panel and local game log.

## Local bridge and data

The bridge binds only to `127.0.0.1:47653`, rejects non-loopback peers, and
does not use an Allow/pairing token. This intentionally trusts processes on the
same computer; it is not a LAN service. It exposes the complete bounded scan,
paged block and item catalogs (with client-rendered icons where available),
and plan validation/preview endpoints. MCP plan history is owned by the
separate server under the OS application-data directory; the addon does not
save conversations or projects.

An explicit MCP capture request can capture the current world framebuffer;
the temporary PNG is deleted after the bridge returns it. The screenshot can
include visible names/signs. No data is sent to a remote provider by the addon;
the external MCP client controls its own model communication.

## Limits and safety

- At most **32,768 selected cells** and **2,048 cell reads per game tick**.
- Only loaded client-world cells are observed. Unknown/unloaded cells are not
  treated as air.
- A world/session or selection change invalidates the snapshot and preview;
  matching saved bounds are automatically rescanned after reconnect.
- The addon never places or breaks blocks, moves the player/camera, edits
  inventory, changes movement keys, or issues server commands.

The separate MCP server can be built and packaged with:

```sh
(cd builder-addon && ./gradlew -p ../builder-mcp-server test packageForCodexPlugin)
```

See the [live runtime checklist](../docs/builder-runtime-checklist.md).

# KRU Builder live runtime checklist

Status: **not live validated**. Automated tests and independent Gradle builds
do not establish Minecraft runtime compatibility. Check items only after a
real run in a disposable instance.

- [ ] Launch with Minecraft 1.21.1, Fabric, Java 21 and Meteor 0.5.8; verify
  enabling Builder starts `127.0.0.1:47653` and opens the selection panel, but
  does not scan or transmit automatically.
- [ ] Apply bounds and verify applying/marking does not scan; explicit
  **Scan / Rescan** reports progress and respects the 32,768-cell and
  2,048-reads-per-tick limits.
- [ ] Scan loaded and unloaded areas; confirm incomplete/unknown cells are not
  presented as known and world/server/dimension changes invalidate stale data.
- [ ] Test local MCP pairing request, explicit Allow, Reject and Revoke. Confirm
  unpaired MCP requests cannot read selection/catalog or publish plans.
- [ ] Inspect catalog search and rendered block thumbnails with the active
  modpack; verify pagination and descriptions remain usable.
- [ ] Validate and publish a small plan through MCP; confirm it renders as a
  preview and no world blocks, inventory, movement, camera or server state are
  changed. Test invalid/stale plan rejection.
- [ ] Test explicit MCP screenshot capture with HUD, resource packs and shaders;
  verify temporary capture cleanup and healthy GL state.
- [ ] Check overlay toggle and panel scaling at multiple GUI scales/resolutions;
  confirm the UI remains usable while scan errors/status are visible.

Record client/mod versions, operating system, GUI scale, resource/shader packs,
and observed results here when each check is actually performed. Do not mark an
item complete based on unit tests, compilation, artifact inspection, or an
assumption.

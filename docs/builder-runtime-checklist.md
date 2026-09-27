# KRU Builder live runtime checklist

Status: **not live validated**. Automated tests and independent Gradle builds
do not establish Minecraft runtime compatibility. Items below remain unchecked
until someone records a real result from a disposable test instance.

- [ ] Launch with Minecraft 1.21.1, Fabric, Java 21, Meteor 0.5.8 and the
  intended native Codex CLI version; verify Builder appears and reports
  missing/incompatible dependencies clearly.
- [ ] Confirm selection controls, overlay input ownership, Builder window
  focus, keyboard shortcuts, chat text entry, collapse/restore, cancellation
  and GUI scaling at multiple resolutions/scales.
- [ ] Scan a small area spanning loaded and unloaded chunks; confirm unknown
  cells remain unknown, progress is bounded, and changing world/server clears
  stale context.
- [ ] Inspect framebuffer capture and preview with HUD, resource packs and
  shaders; verify GL state/resources remain healthy and screenshots contain
  only the intended frame.
- [ ] Exercise image add/preview/remove/confirm, image capability unavailable
  state, and request cancellation. Confirm the disclosure precedes cloud
  transmission and only reviewed data is sent.
- [ ] Perform the dedicated Builder-profile Codex login manually; inspect
  connection/account errors, the effective isolation policy and model/image
  capability. No login or model request has been performed as part of CI or
  this packaging task.
- [ ] Review, reject and accept a small proposal; manually build it and compare
  full block states, stage/progress display, save, explicit reopen, and stale
  world/selection rejection.
- [ ] Confirm no block placement/removal, movement, camera movement, server
  command, or permission-dependent action is issued by Builder.

Record client/mod versions, operating system, GUI scale, resource/shader packs,
and observed results here when each check is actually performed. Do not mark an
item complete based on unit tests, compilation, artifact inspection, or an
assumption.

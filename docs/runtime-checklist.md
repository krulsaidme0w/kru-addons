# Runtime acceptance (not yet performed)

For 0.2.0, use [the current overlay checklist](cobblemon-overlays.md).
The tools checklist below is historical/deferred, not part of the current rollout.

Build success does not prove these checks. Use a separate Minecraft 1.21.1 /
Java 21 instance with official Meteor 0.5.8; leave the existing instance untouched.

## Independence

- Launch official Meteor alone; record its normal navigation.
- Add kru-cobblemon and a supported Cobblemon runtime. The five modules appear
  in the Cobblemon category without replacing Meteor tabs or built-in modules.
- Remove kru-cobblemon. Meteor still launches with no KRU classes required.
- Test tools separately; it must not require Cobblemon or the sibling addon.

## Observations

- With the dungeon profile disabled, no server-specific targets are classified.
- Explicitly enable an edited local profile. A changed model mapping affects
  only the matching client-visible item; unrelated items remain unclassified.
- Reload invalid/missing JSON. Show a diagnostic and clear prior rules/markers.
- Change world/server and disconnect. No stale markers or owner assumptions.
- Discovery labels local spawn information as reference data, not a confirmed
  live server probability. Missing ownership information does not mean wild.

## Windows and battle overlay

- Set Type Chart's overlay action key in Meteor, open/close it, and inspect all 18 types at small
  and large GUI scales. No clipped matrix or off-screen close control.
- Enable Battle Matchups (Battle Facts content) before and during battle. Check revealed types,
  unknown inputs, hover/click bounds, disabling, closing, and next battle.
- Verify both the installed Cobblemon fork and the intended official release
  separately. A skipped optional ABI test is not a supported-version claim.
- Test unsupported battle ABI diagnostics and fallback text/icons.
- Confirm clicks outside the panel reach the battle screen and no extra
  network actions are produced by this read-only overlay.

## Tools addon

- Enable `packet-inspector`; verify the live window opens with one row per packet,
  direction buttons, substring search, hide movement/keepalive and clear.
- Select packet types in module settings and confirm capture filtering is separate
  from the viewer's search/display filters.
- Enable `ui-utils` on a handled screen; verify Close local, Desync/Resync,
  sync/revision and GUI-delay controls, including queue clearing on disconnect/off.
- Close the viewer and disable the module; no packet file is created and no queue
  is flushed into a later connection.

The retained native battle pane is an interim presentation. A consistently
Meteor-themed contextual overlay remains a separate UI completion item; do not
mark it accepted merely because the chart uses Meteor WindowScreen.

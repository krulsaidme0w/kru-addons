# Cobblemon overlays — 0.3.3

Minecraft 1.21.1 / official independent Meteor 0.5.8 / Java 21.
Development build: this revision does not replace installed mods.

## Enable and interact

Meteor → Cobblemon → Type Chart, Battle Matchups, Cobblemon UI Utils.
Enable desired modules once; existing module IDs/settings/binds remain.

- Type Chart opens manually. Battle Matchups automatically appears only in a battle.
- Type Chart's shown/hidden state survives dimension changes and reconnect lifecycle
  callbacks within the running client. Position, scale and collapse settings are
  retained. Manual module activation / Show overlay still explicitly opens it.
  World-bound battle and inventory state is cleared separately; this does not keep
  stale menu sessions alive across dimensions.
- UI Utils appears over inventory, server container menus and Cobblemon PC.
  /wondertrade, /daycare and /gts use the generic container adapter, not title matching.
- A closed contextual panel stays dismissed for that menu/battle. The next menu/battle
  shows it again; Show Hide Key can restore it within the same eligible context.
- Assign Show Hide Key and Interact Key in Meteor; defaults remain unbound.
  Interact Key releases the world cursor without replacing an existing native menu.
- Drag the title; -, +, 1:1, collapse and close share scaled hit-testing.
  1:1 requests scale 1 and resets position. Reset position / scale in module settings
  restores the panel default, including the chart's new 80% default.
- Existing saved scales are not overwritten. Reset the chart setting when upgrading
  from a profile that explicitly stores scale 1.
- Click outside to return input to the native menu. Captured mouse/key releases stay
  with the overlay even when it moved or closed after the press.
- A focused command field owns typing/hotkeys; Escape first blurs it. Native chat,
  search/rename and Meteor settings otherwise retain their input priority.
- Small viewports fit to 50%, then clip/scroll. Shift-wheel pans a clipped host
  horizontally; the requested scale is retained when the window grows.

## Type Chart

Standard 18×18 matrix: attack rows, defence columns, blank = 1×.
80% new default; no fixed upper/lower explanatory strips. Hover shows full type names
and multiplier near the cursor, bounded to the viewport. Row/column highlighting
and click-to-pin remain. Escape unpins before closing.

## Battle Matchups

Pokémon form both axes: up to six own roster rows and six opponent roster columns.
Each intersection contains up to four compact known-move entries; hover supplies
the full move name and evidence. Native portraits stay in compact headers, with
no separate tall fighter cards. Unknown roster identities remain unknown slots;
they do not disappear from the grid merely because their moves/species are hidden.

Only explicit current battle-type facts authorize a type-chart multiplier. No
species/base-type, profile, last-observed or standalone stored-Tera fallback exists.
`?` means insufficient information, not 1×; unavailable portraits are not replaced
with guessed species. Status moves show a dash and unsupported dynamic/special
move semantics remain unknown. This is type effectiveness, not damage or a best-move
recommendation; ability/item/weather effects are not simulated.

The window automatically appears only in battle. Battle end clears the view; a new
battle cannot reuse prior facts. Existing saved panel scale is retained; use Reset
position / scale to adopt the new compact default after upgrading.

Pinned-runtime limitation: the installed Cobblemon fork's live and saved opponent
projections do not expose current defensive types. Repository Terastallization is
last-observed, not a current type-set proof. Consequently this strict matrix shows
`?` for opponent multipliers unless a verified current source becomes available;
the old `~` species-reference values are intentionally gone. The UI must not imply
that a compact matrix also created server data that the client never received.

## UI Utils

One compact Meteor-styled panel with generic container and separate PC adapters:

- Command + Enter/Send: accepts input with/without /. Only explicit submission sends;
  Enter repeat cannot send twice. Feedback means sent, not server-side success.
  Cursor/selection, clipboard, deletion and bounded single-line paste are supported.
- Copy title: copies current native menu title. Hover over the status line shows
  container ID/slot count or Cobblemon PC, never fake PC sync IDs.
- Close: hide the local menu while keeping the UI Utils panel. Verified vanilla
  inventory/chest screens only, plus PC via its adapter.
  PC uses closeNormally(false), verified containers setScreen(null). Unknown subclasses
  retain commands/Exit, but local close is unavailable with an explanation.
  It does not guarantee a retained server link. Other mods can
  attach their own removal hooks; live modpack compatibility needs a separate check.
- Actions recheck world, connection, screen and handler/PC identity immediately.
  Menu replacement invalidates old actions. Drafts do not auto-send; connection/world
  reset clears them.
- Exit: end the current UI Utils session and request normal native close/unlink
  where still valid. The Meteor module remains enabled; a new menu starts a new
  session. Pending actions are cancelled locally, not reversed server-side.
- PC 1st In: first occupied party slot to the first free slot of the selected box.
- PC 1st Out: first occupied slot of the selected box to the first free party slot.
  One request per click; wait for exact UUID/destination acknowledgement. Empty/full,
  missing live data, native last-party restriction or an uncertain result prevents
  another send. Timeout requires Exit/reopening, never an automatic retry.
- Enable `Server adapters → academy-daycare-controls` explicitly for Academy.
  Breeding In selects the first party Pokémon for the current pen's left slot;
  Breeding Out withdraws only its left parent. The right parent is untouched.
  Expected native picker/confirmation screens are followed for that one operation.
  No `/daycare` auto-opening, pen switching, pair selection or breeding loop exists.
  Unrecognized layouts, ambiguous choices or confirmations stop with a reason.
  Profile mappings: bundled `profiles/academy-daycare-controls.properties`.

After Close, actions use the live handler/storage, never cached item slots or a
restored screen. Server close/unlink, replacement, disconnect and world change revoke
the retained session. Local storage identity is not a promise that the server keeps
the link alive. Native PC handlers can redirect a destination occupied in a race;
the addon does not claim success without its exact requested destination ACK.

To move a panel over the world: assign `Overlay → Interact Key` in the module settings
before opening the target menu. Press it to release the cursor; hold left mouse on
the pink title bar and drag. Escape returns to the game (first blurring a focused
command field). An already open inventory/menu provides the cursor normally.

No Save/Load/Desync placeholders, packet tools/queues, restored stale GUI or bulk
Pokémon transfers. Unsupported screens that are neither HandledScreen nor verified
PCGUI need a specific adapter; generic Cobblemon class-name prefixes are not support.

## Required in-game acceptance (not replaced by unit/ABI tests)

1. Inventory, /wondertrade, /daycare, /gts and PC: auto-show without replacing menu;
   close panel, verify dismissal lasts until the next context.
2. Type/paste/select/delete, hold Enter: exactly one command, no input/slot actions
   leaking beneath. Escape first exits field focus.
3. Command changes menu, handler replacement, disconnect/reconnect: old actions
   cannot execute; drafts never auto-send; no stale GUI restoration.
4. Close then cursor interaction/commands; Exit then new-menu auto-show. Check modpack
   removal hooks, server-forced closure and no operation after invalidation.
5. Chart: hover while pinned, all viewport edges, custom/vanilla Meteor fonts,
   scale/resize/scroll, inside versus outside clicks.
6. Battle start/end/new battle, switch/reveal, dual types/immunities, unknown/dynamic
   move types, bounded scrolling and portrait fallback.
7. Overlap, drag beyond edges/release, Escape and native text-input priority.
8. PC In/Out once, rapid repeat, last party member, full/empty box/party; exact UUID ACK.
9. Opt-in Academy left In/Out, occupied/empty role, duplicate-looking Pokémon,
   confirmation, unrelated menu, Close-before-action, disconnect and timeout.

Discovery enrichment/profile editing is still pending, not delivered by this UI revision.
Meteor sources, packet tools, installed modpack and legacy cobbhelperv2 stay untouched.

# KRU Builder (experimental)

KRU Builder is an experimental, client-side planning and hologram addon for
Minecraft 1.21.1, Fabric, Java 21 and official Meteor Client 0.5.8. It does not
require Cobblemon or Academy. The interface reuses Meteor's UI and does not
modify Meteor core.

## Start safely

1. Install the official Meteor client and this addon's binary JAR in a separate
   test instance. Do not install the sources JAR.
2. Enable Builder in Meteor, open its chat, and set both area corners numerically
   under **Settings**. Apply saves the bounds for this world/server and dimension.
   Restoring bounds does not scan or send anything.
   Only already-loaded client chunks are read. An unloaded or unavailable cell
   remains unknown; an incomplete scan cannot be accepted as a complete plan.
3. Choose **Connect / sign in** using a native Codex CLI executable on the
   same operating system as Minecraft, then use **Sign in with Codex** for the
   Builder-owned profile. Builder requires exactly `codex-cli 0.153.4` and
   fails closed if the installed protocol or effective configuration is not
   compatible. It does not copy the regular Codex login/configuration. No WSL
   fallback is attempted; Windows installations may need the direct
   `codex.exe` executable path in Builder's module settings.
   With the default `codex` setting, Builder first uses an explicitly installed
   instance-local `kru-builder/bin/codex.exe` on Windows (`bin/codex` on other
   systems). Explicit executable settings are never overridden. Builder does
   not download executables or modify your global Codex installation.
4. Write a request and choose **Send**. This action refreshes the bounded
   local scan, captures a world screenshot, checks the context again and sends
   the request without another confirmation dialog.
   Use **+ Image** to reveal the optional reference PNG path; Builder copies the file
   into its private attachment store and leaves the original untouched. Remove
   unwanted references before sending. Each request replaces the previous automatic
   world screenshot while retaining selected reference images.
5. A valid returned proposal appears automatically as a hologram. **Save plan**
   persists it as the active project. The addon renders guidance; the player places or removes blocks
   manually. Reopen a saved project only when deliberately choosing it for the
   current selection/world.

The main window keeps the conversation and current action together.
**Settings** contains saved numeric bounds, connection details and saved
project reopening; **Back to chat** preserves the entered request. Failed
requests show a short status in the window and report the full error once in
local game chat. Stage and progress controls appear when a proposal is available.

## Data and privacy

Codex runs as a local process, but requests use the configured model
provider and are not offline. **Send** authorizes capture and transmission;
there is no separate disclosure or approval dialog in the game UI.
A request can include your text, the
selected area's block IDs and state properties, unknown-cell locations, the
current accepted plan, and every image still visible in the attachment list.
When the model asks, Builder can also send bounded pages of local block registry
IDs/names and one requested block's allowed state values plus a 128×128-or-smaller
inventory-model preview. These catalog tools inspect no world, inventory,
containers or files; results and previews are sent to the configured model
provider only in response to its request.
The world screenshot is captured before HUD rendering; names or signs visible
in the world itself can still appear. Reference images are opt-in. Inventory,
chat, containers and arbitrary block-entity data are not included.

Opening the window, applying/restoring bounds and connecting do not send a build
request. **Cancel request** stops pending preparation or the owned request
process; a request already accepted by the provider may already have been
transmitted.

Builder keeps project files under the Minecraft instance's `kru-builder/projects`
directory and temporary images under `kru-builder/attachments`. Its Codex
profile uses a separate `kru-builder/codex-profile` tree, with an addon-owned
Codex home and empty working directory beneath it. The child process receives
an allowlisted environment and does not inherit the regular Codex home, auth,
configuration or provider tokens. Builder does not read/copy those stores.
Review and protect the instance data according to your needs. Do not commit it
or share it unintentionally.

Builder's isolated policy enables the Codex Code Mode host runtime needed to
route its fixed, read-only local block-catalog tools and pins
`features.code_mode=false`. The selected model can still require a particular
tool mode, so the host runtime is available for that protocol. Shell/file
tools, MCP integrations, plugins, apps, hooks, web search, skills/instructions
and network access remain disabled for the model thread. It requires the
expected read-only sandbox and `never` approval policy before a request.
Unsupported or uninspectable policy state, missing
login, incompatible model/image capability, stale world state, and unavailable
world facts are shown as unavailable/errors and block the dependent action.
No credentials or login are needed in CI; tests use synthetic process frames.

## Limits and behavior

- At most **32,768 selected cells**; at most **2,048 cell reads per game tick**.
  Only loaded client-world cells are observed.
- A plan can contain at most **32,768 distinct targets** and **4,096
  operations**. Operations are validated locally against the selection and
  current block registry before they are shown as proposals.
- At most **4 PNG images**, each at most **1,280 pixels on either edge**, with
  **8 MiB combined encoded input**. The image limits can be lower than the
  Codex protocol's per-turn transport limits.
- A project file is versioned and limited to **8 MiB**. Reopening it is an
  explicit action and requires validation for the current selection and block
  registry. A world/server change invalidates live context; saved plans are not
  silently carried into a new world.
- Unknown/unloaded cells are never treated as air. A stale scan, changed
  selection, changed world, unsupported block state, or invalid plan prevents
  acceptance until refreshed or corrected.
- Builder never places or breaks blocks, changes movement keys, moves the
  player/camera, issues server commands, or automates world edits. It grants no
  server permissions and does not require OP.

These limits and safeguards are code-level behavior, not a claim of tested live
Minecraft compatibility. See the [runtime checklist](../docs/builder-runtime-checklist.md).

## Build and package

```sh
(cd builder-addon && bash ./gradlew --no-daemon build)
python3 -m unittest discover -s distribution -v
python3 distribution/release.py builder-addon
```

The independent artifact is `kru-builder-<version>.jar`; the sources JAR,
`LICENSE`, `NOTICE`, `THIRD_PARTY.md` and `SHA256SUMS` accompany the packaged
binary. Builder has its own Gradle root, namespace and CI matrix entry. A
`builder-vX.Y.Z` tag routes to this addon; tag builds create drafts only.

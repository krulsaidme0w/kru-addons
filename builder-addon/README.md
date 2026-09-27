# KRU Builder (experimental)

KRU Builder is an experimental, client-side planning and hologram addon for
Minecraft 1.21.1, Fabric, Java 21 and official Meteor Client 0.5.8. It does not
require Cobblemon or Academy. The interface reuses Meteor's UI and does not
modify Meteor core.

## Start safely

1. Install the official Meteor client and this addon's binary JAR in a separate
   test instance. Do not install the sources JAR.
2. Enable Builder in Meteor and mark two opposite corners of a small test area.
   Only already-loaded client chunks are read. An unloaded or unavailable cell
   remains unknown; an incomplete scan cannot be accepted as a complete plan.
3. Open the Builder window and read its cloud data disclosure. Grant the
   explicit **session consent** control before connecting; consent defaults off
   and resets when the Builder session closes. Revoke it at any time to cancel
   pending transmission. Then connect using a native Codex CLI executable on the
   same operating system as Minecraft, then use **Sign in with Codex** for the
   Builder-owned profile. Builder requires exactly `codex-cli 0.153.4` and
   fails closed if the installed protocol or effective configuration is not
   compatible. It does not copy the regular Codex login/configuration. No WSL
   fallback is attempted; Windows installations may need the direct
   `codex.exe` executable path in Builder's module settings.
4. Write a request and choose **Prepare**. Preparation refreshes the bounded
   local scan and captures a preview; it does not transmit the request.
   Optionally add a reference PNG by entering its path; Builder copies the file
   into its private attachment store and leaves the original untouched. Inspect
   the displayed text, context summary and images. Remove any image you do not
   want included. The next preparation replaces its previous automatic world
   screenshot while retaining selected reference images. Only **Confirm and send to Codex** transmits the
   reviewed request. The current world context is checked again before sending.
5. Inspect a returned proposal and explicitly accept it to make it the active
   project. The addon renders guidance; the player places or removes blocks
   manually. Reopen a saved project only when deliberately choosing it for the
   current selection/world.

## Data and privacy

Codex runs as a local process, but confirmed requests use the configured model
provider and are not offline. The Builder screen states before connection that
local Codex sends prompts, selected geometry and chosen images to OpenAI, and
images may retain names visible in the world. Consent is explicit, defaults off,
is limited to the current Builder session and can be revoked at any time. A
confirmed request can include your text, the
selected area's block IDs and state properties, unknown-cell locations, the
current accepted plan, and every image still visible in the attachment list.
The world screenshot is captured before HUD rendering; names or signs visible
in the world itself can still appear. Reference images are opt-in. Inventory,
chat, containers and arbitrary block-entity data are not included.

The UI separates preparation from transmission. Review the request context and
image thumbnails, remove attachments when needed, and confirm only when the
displayed data is appropriate to send. Cancellation stops the owned request
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

Builder's isolated policy disables shell/file tools, MCP integrations, plugins,
apps, hooks, web search, skills/instructions and network access for the model
thread. It requires the expected read-only sandbox and explicit approval
policy before a request. Unsupported or uninspectable policy state, missing
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

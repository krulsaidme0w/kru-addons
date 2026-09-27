# Task 3 — manual Academy daycare actions

Status: production edits stable. Final focused Java 21 build and **30 tests passed**, including two tests against the pinned native jar (zero skips), after the controller handoff adjustment and P2 confirmation-readiness fix. Final worker run: **2026-09-21 20:47:55 UTC**. Controller owns the final unrestricted suite/remap and integration review. No installation, commits, subagents, legacy edits, controller edits, or build.gradle edits were performed.

## Public integration API

`dev.krul.cobblemon.pcui.daycare.ManualDaycareActions` exposes exactly the agreed entry points:

```java
public ManualDaycareActions();
public boolean supports(Object screen);
public String request(Object screen, boolean insert, java.util.function.BooleanSupplier valid);
public void tick();
public boolean busy();
public String status();
public void reset();
public boolean ownsScreen(Object screen);
```

The controller supplies the `academy-daycare-controls` setting, disabled by default. Constructing the adapter performs no Minecraft action. The bundled profile is used only when the controller explicitly calls this adapter. The fast `valid` predicate checks retained session/world/connection identity and current closure epochs against the controller's accepted revision. It is enforced by request/tick before mutations, **not called by ownsScreen**.

Keep the retained root through `ownsScreen(currentScreen)` **before** generic session refresh, then call `tick()`. Passing null for the inert OverlayInteractionScreen is supported; the native port normalizes that screen to null as well. Bounded null gaps after root/picker closure preserve the pending request. Outside a pending request, ownership is false. World/session invalidation, unrelated handlers/screens, changed pen/right card, replacement picker instance, changed confirmed dialogue input ID, or timeout stop the transaction. A 30-second process-monotonic deadline bounds the whole request. Unknown outcomes block further requests until explicit reset; reset does not undo a server action or cancel a packet already sent.

`ownsScreen` is pure local frame/transition/timeout evidence. It deliberately remains usable when the controller has not yet accepted the close revision associated with a new expected screen. The controller must still check world/connection and close epochs: local GAP ownership never authorizes ignoring a new close epoch. Per final controller review, only the controller can accept one container-close increment on a new nonnull verified owned screen, never a GAP/same picker or PC closure. A false final predicate makes tick stop without sending any selection/confirmation.

`supports(root)` recognizes a visible Academy pen or its retained hidden root while the same native handler remains current. Before the opening click, `openLeft` checks handler identity, current-screen/root relationship, cursor emptiness, current left role, player and connection evidence again. No screen is restored. No command, pen iteration, automated GUI opening, breeding planning or right-parent operation exists.

## Native operation and acknowledgement

- In: current observed left slot 28, profile button 0 / `PICKUP`. Capture the first occupied live party UUID, preserving physical party order across holes. Accept only the expected native PartySelectGUI with profile title `Daycare`. Select a unique species/aspects/level projection among both live party and picker entries, using the actual configuration DTO object. Duplicate projections, missing/disabled selection, storage changes, or unavailable getters block. No slot-index guessing or retry selection.
- Out: current observed left slot 28, profile button 1 / **`QUICK_MOVE`**, matching legacy `LiveMeteorDaycareClient.withdraw`. A regression first failed with PICKUP and passes with QUICK_MOVE. The user explicitly chooses the visible left card; it does not supply a UUID.
- Confirmation: only a pending native DialogueScreen, with the expected operation phase, daycare context, operation-specific text and exactly one positive plus one negative profile option. Dispatch one InputToDialoguePacket using the current input UUID and actual option value through the native screen's `sendToServer`. Retain screen/input identity; never confirm a second dialogue. Title alone does not authorize confirmation.
- Recognized confirmation readiness is a separate read-only capability: READY, WAITING, or UNAVAILABLE. Gradual text (`renderInput=false`) or an outstanding native server update yields WAITING without calling the dispatcher. The original 30-second request deadline still applies. Pin the recognized screen/input identity while waiting, recheck session validity immediately before dispatch, and consume confirmation only as dispatch begins. Missing getters/mismatches and boolean false or exceptions from dispatch remain terminal Unknown; they never grant a retry.
- In ACK: same pen, unchanged right-card fingerprint, occupied left card matching selected projection, exactly the selected UUID removed from the combined PC/party set, and no added UUID. Moving that UUID to PC is not insertion success.
- Out ACK: same pen, unchanged right card, empty left role, no removed storage UUID, exactly one new UUID in PC/party, and its species/aspects matching the previously visible card. Multiple new UUIDs or absent updates remain pending until bounded Unknown. A fresh same-pen root may prove a direct return without a dialogue transition.
- The same native ClientParty and ClientPC objects must survive the transaction. Duplicate UUIDs invalidate the storage snapshot. The adapter reads client-visible live storage and never calls PokemonItem.asPokemon or invents a card UUID.

## Focused test evidence / TDD

The test-driven-development skill and its writing-good-tests reference were read before implementation. Behavioral tests exercised the real transaction/rule/reflective-storage code; a fake native boundary supplies immutable observed frames and records semantic dispatch arguments. Minecraft screens/network are not simulated as proof of live operation.

Observed cycles:

1. Transaction API skeleton: **10 tests, 7 expected assertion failures**; then 10 passed.
2. Native profile/picker rules plus owned-null-gap regression: **15 tests, 5 expected assertion failures**; then these passed.
3. Live storage reader skeleton: **17 tests, 1 expected assertion failure**; then 17 passed.
4. Native pinned-jar ABI checks: 19 passed, no skips.
5. Out-click regression: **20 tests, 1 failure**, expected QUICK_MOVE vs actual PICKUP; fixed and passed.
6. Changed dialogue input, replacement storage objects, fresh root after direct withdrawal: **23 tests, 3 expected assertion failures**; fixed and passed.
7. Final run after moving button/action mappings into the opt-in resource: **23 tests, zero failures/errors/skips**; compileJava succeeded.
8. Controller handoff regression: **23 tests, 1 expected assertion failure** with the old predicate call inside ownsScreen; removed that call while preserving tick/request checks. Final rerun: **23 passed, zero failures/errors/skips**, compileJava succeeded at 20:39:21 UTC. The regression proves a locally valid expected picker remains detectable with predicate=false, while tick terminates before another native action.
9. P2 gradual-confirmation-rendering regression: **28 tests, 4 expected assertion failures** reproduced the premature abort; the additional native flag-decoder tests brought RED to **30 tests, 6 expected failures**. After adding explicit readiness and deferring dispatch consumption: **30 passed, zero failures/errors/skips**, including both native ABI tests, at 20:47:55 UTC. Tests cover rendering → ready → one dispatch → exact storage ACK; original-deadline timeout with no dispatch; changed input while waiting; invalid session while waiting; boolean dispatch ambiguity without retry; actual native getter-shape decoding for READY/WAITING/UNAVAILABLE.

Commands, from `<repository>/cobblemon-addon`:

```sh
./gradlew compileJava --console=plain
./gradlew -I /tmp/kru-daycare-tests.ZggOts/focused.gradle test --tests 'dev.krul.cobblemon.pcui.daycare.*' --console=plain
```

The temporary init script only sets `sourceSets.test.java.setIncludes(['**/pcui/daycare/**'])` after project evaluation. It avoids compiling concurrent workers' unfinished test types; it is not a repository build change. The initial unfiltered focused attempt encountered the controller's then-missing UiSessionSignals test type. Final integration must omit this init script. Worker runs stop after this report so controller output is not overwritten again.

Final test counts: ManualDaycareActionsTest 19; DaycareNativeRulesTest 7; DaycareStorageReaderTest 2; DaycareNativeAbiTest 2. Only existing deprecation notices (BattleMatchupPanel and Gradle 9 compatibility) appeared.

## Scoped rereview request — P2 readiness fix

Relay to **Planck `01a0c5b2-ec66-7ee2-8638-5429efcd524f`** after the GREEN result above. Agent messaging is not callable in this worker session; no reviewer request has been sent through tools. The controller's cross-flow PASS predates and excludes this readiness fix.

Review only these changes, relative to the preceding Task 3 handoff:

- `DaycarePort.java`: package-private ConfirmationReadiness enum and read-only `confirmationReadiness(Object screen, boolean insert)` boundary; public ManualDaycareActions API unchanged.
- `NativeDaycarePort.java`: revalidate current handler/screen and recognized operation-specific dialogue before checking readiness. Decode existing pinned `getWaitingForServerUpdate()` and `renderInput()` getters strictly. Only actual boolean readiness states can return WAITING; unavailable getters return UNAVAILABLE. Boolean `confirm` keeps its final validation and no-retry failure semantics.
- `ManualDaycareActions.java`: pin confirmation screen/input identity on first recognized observation; WAITING leaves `confirmed=false` and returns without dispatch or deadline reset. READY rechecks the session predicate, marks consumed immediately before calling `confirm`, then waits for the existing exact storage ACK. UNAVAILABLE, dispatch false/exception, invalidation, replacement input, or deadline expiry stop safely. `ownsScreen` still never calls the session predicate.
- `ManualDaycareActionsTest.java`: five added regressions for delayed readiness, deadline, changed input, dispatch ambiguity and invalidation. `DaycareNativeRulesTest.java`: two added native getter-boundary tests. This report records RED → GREEN and retained live limitations.

Requested rereview checks: no confirmation consumption during read-only WAITING; exactly one dispatch after readiness; no retry on boolean/exception ambiguity; original timeout remains bounded; same-screen input replacement cannot inherit ownership; handler and session guards remain enforced before mutation. No host/controller/profile/packet ABI changes are included. Source is stable and this worker will not run more tests while the controller integrates.

## Pinned ABI and packet evidence

Jar inspected with `javap -p` / `javap -c -p` and ASM tests:

`/path/to/instance/minecraft/mods/Abes-Hutts-Cobblemon-1.21.1.1.7.3-1.0.5.jar`

SHA-256: `dc8c72beee009e0e5303ab3e0bb4e8bbdfde2e556c99eb2fe6c3a8f036437c22`.

- `CobblemonClient.INSTANCE.getStorage()` → ClientStorageManager; `getParty()` → ClientParty and `getPcStores()` → Map. The manager has **no getPc()** accessor. Reader requires exactly one available PC store.
- ClientParty.getSlots(), ClientPC.getBoxes(), ClientBox.getSlots() return List. Pokemon.getUuid()/getSpecies()/getLevel()/getAspects() and Species.getResourceIdentifier() provide live identity/projection. The supported native shape is six party slots and thirty per box.
- PartySelectGUI.getConfig()/getClosed(); PartySelectConfiguration.getTitle()/getPokemon()/getOnSelect(); PartySelectPokemonDTO.getPokemonProperties()/getAspects()/getEnabled(); PokemonProperties.getSpecies()/getLevel() all match exact tested descriptors.
- `PartySelectGUI._init_$lambda$1(UUID,List,PartySelectGUI,PartySelectPokemonDTO)` calls List.indexOf on the actual DTO, constructs exactly one `PartyPokemonSelectedPacket(UUID,int)`, sends it via CobblemonNetwork, then calls closeProperly(). The implementation invokes this configuration callback through `kotlin.jvm.functions.Function2.invoke(Object,Object)` instead of reproducing a packet index guess.
- Pinned PartySelectPokemonDTO constructor extracts species, level, nickname, pokeball and status; it carries aspects separately and has no Pokémon UUID. Ambiguous visible identity therefore cannot authorize insertion.
- `InputToDialoguePacket(UUID,String)` exists. `DialogueScreen.sendToServer(NetworkPacket)` invokes exactly one NetworkPacket.sendToServer() and sets waitingForServerUpdate. getDialogueDTO(), getCurrentPageDTO(), getLines(), getDialogueInput(), getInputId(), getDialogueOptionWidgets(), option getText()/getValue()/getSelectable(), renderInput() and getWaitingForServerUpdate() descriptors are tested.
- `CobblemonItemComponents.POKEMON_ITEM` is a component-type field (`class_9331`); PokemonItemComponent.getSpecies()/getAspects() exist. Typed Yarn ComponentType/ItemStack access is remapped by Loom; reflective Minecraft method-name guessing is not used.
- Legacy GenericContainerScreen / GenericContainerScreenHandler intermediary IDs class_476 / class_1707 match the typed MC 1.21.1 boundary. Native root click uses current handler syncId and the verified left-slot button/action tuple. Packet emission itself was **not live-captured**.

The ABI tests accept `KRU_COBBLEMON_RUNTIME_JAR` for other environments, default to the pinned path above, and explicitly skip if no jar is available. Both ran without skips here.

## Changed files and provenance

All Java paths below are relative to `cobblemon-addon/src/main/java/dev/krul/cobblemon/pcui/daycare/`:

- ManualDaycareActions.java — public facade, bounded transaction and exact-delta ACK.
- DaycarePort.java — immutable observations and native boundary contract.
- NativeDaycarePort.java — current-handler root clicks, picker callback and dialogue dispatch.
- AcademyDaycareProfile.java — bundled profile loader and semantic recognition.
- DaycareNativeRules.java — unique live-party/picker matching.
- DaycareStorageReader.java — fail-closed live reflective storage decoder.
- DaycareReflection.java — strict public getter access.

Tests under `cobblemon-addon/src/test/java/dev/krul/cobblemon/pcui/daycare/`: ManualDaycareActionsTest.java, DaycareNativeRulesTest.java, DaycareStorageReaderTest.java, DaycareNativeAbiTest.java.

Resource: `cobblemon-addon/src/main/resources/profiles/academy-daycare-controls.properties`. Report: this file.

Read-only design/contract references from legacy cobbhelperv2: LiveMeteorDaycareClient, DaycareParentLayout, CobblemonPartySelectClicker, CobblemonDialogueClicker, DaycareConfirmationClicker, DaycareParentReader, CobblemonGuiClassifier and DaycareScreenAdapter. This is new scoped code informed by those contracts, not copied classes or a Breeding migration. Adaptations are intentionally stricter: no retries/navigation, no ambiguous slot fallback, no title-only arbitrary widget confirmation, no card-generated UUID, exact storage ACK and one bounded request. Existing source/license notices were not removed or changed.

## Explicit limitations

No Minecraft live session was run. Compilation, transaction tests and native bytecode prove the implemented client boundary, **not Academy's current server acceptance** or that a detached connection remains open.

The bundled profile is deliberately narrow: suffix U+0E51–U+0E56, a 54/90-slot generic container excluding backpack controls, slot 28/34, air as the proven empty left role, pokemon_model as occupied, native picker title exactly Daycare, and English daycare/action/yes-no style dialogue content. Non-air placeholder cards, localized/changed wording, arbitrary widget-based Daycare confirmation, or unrecognized picker content stop safely. These profile text patterns have not been confirmed in a new live capture; if Academy's current wording differs, capture it read-only before broadening recognition.

Missing PC store (for example before any PC data has been received), multiple PC stores, incomplete slot/profile getters, or duplicate UUIDs disable the request. No command or GUI is opened to remedy missing storage. A server that does not return a fresh root or update the retained live root and storage cannot yield a verified ACK; the action times out without retry.

The server protocol does not expose a shared causal transaction token linking the root slot click to the next picker/dialogue. Expected phase, native type, handler lineage, profile semantics, live party matching and accepted screen/input identity are the available checks. An indistinguishable server-supplied replacement with the exact same signature cannot be causally distinguished from the expected transition. Final live acceptance should inspect hidden In/Out, picker autoclose/null gap, confirmation, server root return, exact storage delta and unrelated-menu cancellation.

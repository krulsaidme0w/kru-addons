# Task 2 — manual PC actions

Implemented in `<repository>` only. Legacy project and installed mods were read-only. No installs, commits, subagents, UI/session edits, daycare edits, or full-suite run.

## Public API and controller contract

Class: `dev.krul.cobblemon.pcui.ManualPcActions`

```java
public ManualPcActions();
public boolean supports(Object screen);
public boolean live(Object screen);
public String request(Object screen, boolean intoPc, java.util.function.BooleanSupplier valid);
public void tick();
public boolean busy();
public String status();
public void reset();
```

Call on the Minecraft client thread. `intoPc=true` deposits the first occupied party position into the first free position of the current box; `false` withdraws the first occupied current-box position into the first free party position. Ordering is native list index, zero-based. No other box is scanned, selected, or used as a client fallback.

`supports` recognizes the native PC screen class. `live` proves current native storage object identity, without scanning slots. It does **not** prove a surviving server link:

```text
CobblemonClient.INSTANCE.getStorage().getPcStores().get(screen.getPc().getUuid()) == screen.getPc()
CobblemonClient.INSTANCE.getStorage().getParty() == screen.getParty()
```

There is no `ClientStorageManager.getPC()` in the pinned jar. The controller additionally owns native closure/unlink signals and connection/world/session epochs. Supply their validity predicate to `request`; it is checked before planning, again before sending, immediately before the native dispatch, and before/after each pending observation. A retained screen is exclusively an access/metadata handle. Nothing restores it, dispatches widget clicks, mutates cached widgets, or changes live storage optimistically.

One request arms one pending transaction before dispatch. `tick()` only observes; double-clicks while pending do not send anything. ACK requires the exact selected UUID at the exact requested destination, absence of that UUID from the source storage, unchanged native PC/party identities, and no duplicate UUIDs in the observed party/current box. A transient duplicate waits; no optimistic ACK is inferred from source disappearance. Pending observations stay pinned to the original box even if screen metadata changes.

The 20-second timeout uses `System.nanoTime()`. Timeout, invalidation, destination mismatch, pending storage failure, and an uncertain send stop the transaction and latch a failure. Further requests cannot send until `reset()`. Call `reset()` only for session teardown/change; do not automatically clear an uncertain result to retry. Empty/full/unavailable preflight performs no send. Native last-party-deposit policy is honored; selection-only/hidden-party/pasture PC configurations are rejected.

## Changed files

- `cobblemon-addon/src/main/java/dev/krul/cobblemon/pcui/ManualPcActions.java`
- `cobblemon-addon/src/main/java/dev/krul/cobblemon/pcui/NativePcAccess.java`
- `cobblemon-addon/src/test/java/dev/krul/cobblemon/pcui/ManualPcActionsTest.java`
- `cobblemon-addon/src/test/java/dev/krul/cobblemon/pcui/NativePcAccessTest.java`
- `cobblemon-addon/src/test/java/dev/krul/cobblemon/pcui/ManualPcAbiTest.java`
- This report.

Existing `PcReflection` is consumed unchanged. No project build/test filters were changed.

## Native ABI evidence

Inspected with `jar tf`, `javap`, `javap -c -private`, and executable ASM tests against:

```text
/path/to/instance/minecraft/mods/Abes-Hutts-Cobblemon-1.21.1.1.7.3-1.0.5.jar
SHA-256 dc8c72beee009e0e5303ab3e0bb4e8bbdfde2e556c99eb2fe6c3a8f036437c22
```

This jar has **no class named `TransferPokemonPacket`**. Its native `StorageWidget` uses these verified concrete APIs:

```java
new com.cobblemon.mod.common.api.storage.party.PartyPosition(int slot);
new com.cobblemon.mod.common.api.storage.pc.PCPosition(int box, int slot);
new com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket(
    UUID pokemonID, PartyPosition source, PCPosition destination);
new com.cobblemon.mod.common.net.messages.server.storage.pc.MovePCPokemonToPartyPacket(
    UUID pokemonID, PCPosition source, PartyPosition destination);
packet.sendToServer(); // public void; delegates to NetworkPacket.sendToServer()
```

Read ABI verified by exact descriptors:

- `PCGUI.getPc(): ClientPC`, `getParty(): ClientParty`, `getStorage(): StorageWidget`, `getConfiguration(): PCGUIConfiguration`.
- `StorageWidget.getBox(): int` is used only as current-box metadata.
- `ClientPC.getBoxes(): List`, `ClientBox.getSlots(): List`, `ClientParty.getSlots(): List`, `Pokemon.getUuid(): UUID`.
- Inherited `ClientStorage.getUuid(): UUID`.
- Public static `CobblemonClient.INSTANCE`; `getStorage(): ClientStorageManager`; `ClientStorageManager.getPcStores(): Map` and `getParty(): ClientParty`.
- `PCGUIConfiguration.getShowParty(): boolean`, `getSelectOverride(): kotlin.jvm.functions.Function3`.
- Public static `ServerSettings.INSTANCE`; `getPreventCompletePartyDeposit(): boolean`.

All slot reads come from the live native storage objects. The helper accesses only `getBoxes().get(currentBox)`, never iterates boxes. Before dispatch it rereads current storage/capacity and rejects changes since planning. Constructors are resolved by exact parameter types; there is no first-constructor heuristic, GUI fallback, or semantic retry.

The ASM contract tests also prove native widget calls to those constructors, network dispatch delegation, and native server handler calls to UUID validation/PC link lookup. Native handlers reject a mismatched source UUID or missing link. **They may choose a different free destination server-side if the requested destination is occupied when the request arrives.** The client pre-send recheck reduces this race but cannot make the packet a server-side compare-and-set. If it happens, this implementation fails closed on mismatch/timeout and does not search other boxes or resend.

## TDD and focused command evidence

Read `the test-driven-development skill` and its complete `writing-good-tests.md` before implementation.

1. Added the public shell and behavior tests first. Ran:

   ```sh
   ./gradlew test --tests dev.krul.cobblemon.pcui.ManualPcActionsTest --console=plain
   ```

   RED: compiled and executed 16 tests, 14 expected failures against the shell (missing transfer, pending state, ACK, capacity feedback and timeout behavior).

2. Implemented the pure pending/planning behavior. Added native-boundary tests against an unavailable native adapter shell. Isolated test compilation with a temporary init script when another worker's TDD test referred to not-yet-created `UiSessionSignals`.

   RED: 26 tests, 8 failures: 7 native adapter failures and one invalid full-box fixture which inadvertently duplicated a party UUID. Corrected that fixture to use a distinct UUID, then implemented native reflection. GREEN: 26 tests, zero failures.

3. Added three pinned-runtime ABI characterization tests. Concurrent controller UI code temporarily referenced a daycare no-arg constructor not yet present. Extended the **temporary** init script to compile the three PC source dependencies only and use isolated output. No other worker files were changed.

Final focused command, from `cobblemon-addon`:

```sh
COBBLEMON_RUNTIME_JAR='/path/to/instance/minecraft/mods/Abes-Hutts-Cobblemon-1.21.1.1.7.3-1.0.5.jar' \
./gradlew -I /tmp/kru-pc-tests.q7aDfC/focused.gradle test \
  --tests dev.krul.cobblemon.pcui.ManualPcActionsTest \
  --tests dev.krul.cobblemon.pcui.NativePcAccessTest \
  --tests dev.krul.cobblemon.pcui.ManualPcAbiTest --console=plain
```

Result: `BUILD SUCCESSFUL`; **29 tests, zero failures/errors/skips** (16 transaction tests, 10 native adapter tests, 3 actual pinned-jar ABI tests). XML evidence is in `/tmp/kru-pc-tests.q7aDfC/build/test-results/test/`. Java runtime was OpenJDK 21.0.12; Loom 1.7.4. Existing Gradle deprecation warnings remain.

Temporary init script contents, for reproducibility:

```groovy
allprojects {
    afterEvaluate {
        layout.buildDirectory = file('/tmp/kru-pc-tests.q7aDfC/build')
        sourceSets.main.java.setIncludes([
            'dev/krul/cobblemon/pcui/ManualPcActions.java',
            'dev/krul/cobblemon/pcui/NativePcAccess.java',
            'dev/krul/cobblemon/pcui/PcReflection.java'])
        sourceSets.test.java.setIncludes([
            'dev/krul/cobblemon/pcui/ManualPcActionsTest.java',
            'dev/krul/cobblemon/pcui/NativePcAccessTest.java',
            'dev/krul/cobblemon/pcui/ManualPcAbiTest.java'])
    }
}
```

The controller must run integration/full tests/remap without this script or exclusions. This worker's focused success is not an integrated build claim.

## Reference provenance and limits

Selectively read legacy `breeding/client/CobblemonPcClicker.java`, particularly its `sendTransferPacketPositions` method (lines 266–293) and current-box/live-PC discussion. Used it to locate native packet class names, then independently verified descriptors and native dispatch in the pinned jar. No legacy class or algorithm was copied. No upstream class was copied into the addon. The new small helper deliberately omits legacy widget clicks, screen navigation, all-box fallback, reflection-by-arity, and automation state. Existing addon GPL-3.0 licensing remains unchanged.

No live Minecraft connection or transfer was exercised. Static ABI proof and native-shaped in-memory behavioral fixtures do not prove server acceptance, packet arrival, live event ordering, or that a remote server retains its PC link after local Close. Controller-owned closure/unlink signal observation remains required; native storage map identity can outlive a closed link. Full integration, distribution/remapping, and live Close/Exit interaction verification remain controller responsibilities. No test claims coverage of Pokémon outside the selected box and party.

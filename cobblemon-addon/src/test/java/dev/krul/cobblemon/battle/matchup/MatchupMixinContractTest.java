package dev.krul.cobblemon.battle.matchup;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Checks our optional boundary against real class bytes; not a claim of live mixin application. */
class MatchupMixinContractTest {
    @Test void tooltipDrawsOnAnExplicitLayerAboveNativeThreeDimensionalPortraits() throws Exception {
        try (var input = getClass().getResourceAsStream("/dev/krul/cobblemon/battle/matchup/BattleMatchupPanel$1.class")) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            var overlay = node.methods.stream().filter(m -> m.name.equals("beginOverlay")).findFirst();
            assertTrue(overlay.isPresent(), "Native adapter must implement the higher overlay layer");
            assertTrue(java.util.Arrays.stream(overlay.get().instructions.toArray()).anyMatch(i ->
                i instanceof org.objectweb.asm.tree.MethodInsnNode m && m.name.equals("translate")), "Overlay must not share model depth");
        }
    }
    @Test void overlaysUseSharedInputRenderHooksInsteadOfMutatingNativeBattlePane() throws Exception {
        try (var resource = getClass().getResourceAsStream("/kru-cobblemon.mixins.json")) {
            var config = JsonParser.parseReader(new InputStreamReader(resource)).getAsJsonObject();
            var clients = config.getAsJsonArray("client").asList().stream().map(v -> v.getAsString()).toList();
            assertFalse(clients.contains("CobblemonBattleGuiMixin"), "Old pane-resizing extension must not run");
            for (String hook : java.util.List.of("OverlayRenderMixin", "OverlayMouseMixin", "OverlayKeyboardMixin")) {
                assertTrue(clients.contains(hook), "Missing common overlay hook " + hook);
                try (var bytes = getClass().getResourceAsStream("/dev/krul/cobblemon/mixin/" + hook + ".class")) {
                    assertNotNull(bytes);
                    var node = new ClassNode();
                    new ClassReader(bytes).accept(node, 0);
                    assertTrue(node.methods.stream().flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                        .anyMatch(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("dev/krul/cobblemon/gui/overlay/OverlayHost")));
                }
            }
        }
    }

    @Test @EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
    void installedForkHasTheExactRenderInputAndRepositoryContracts() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            ClassNode gui = read(jar, "client/gui/battle/BattleGUI");
            require(gui, "method_25394", "(Lnet/minecraft/class_332;IIF)V");
            require(gui, "method_25402", "(DDI)Z");
            // Scroll override is intentionally added to a target inheriting it from Screen.
            assertFalse(gui.methods.stream().anyMatch(m -> m.name.equals("method_25401")));
            require(gui, "getMessagePane", "()Lcom/cobblemon/mod/common/client/gui/battle/widgets/BattleMessagePane;");
            require(read(jar, "client/battle/ClientBattleInformationRepository"), "getActors", "()Ljava/util/Map;");
            require(read(jar, "client/battle/ClientBattleInformationRepository"), "getBattles", "()Ljava/util/Map;");
            require(read(jar, "client/battle/ClientBattle"), "getMustChoose", "()Z");
            require(read(jar, "client/battle/SingleActionRequest"), "getResponse", "()Lcom/cobblemon/mod/common/battles/ShowdownActionResponse;");
            require(read(jar, "battles/ShowdownPokemon"), "getActive", "()Z");
            require(read(jar, "battles/ShowdownPokemon"), "getUuid", "()Ljava/util/UUID;");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "getBattleLogX", "()D");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "getBattleLogWidth", "()I");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "setBattleLogX", "(D)V");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "setBattleLogY", "(D)V");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "setBattleLogWidth", "(I)V");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane$Companion"), "setBattleLogHeight", "(I)V");
            require(read(jar, "client/gui/battle/widgets/BattleMessagePane"), "correctBattleText", "()V");
            require(read(jar, "net/messages/client/battle/BattlePokemonDTO"), "getActiveBattlePokemonDTO",
                "()Lcom/cobblemon/mod/common/net/messages/client/battle/BattleInitializePacket$ActiveBattlePokemonDTO;");
        }
    }
    private static ClassNode read(ZipFile jar, String name) throws Exception {
        try (var input = jar.getInputStream(jar.getEntry("com/cobblemon/mod/common/" + name + ".class"))) {
            var node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_CODE); return node;
        }
    }
    private static void require(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)), type.name + "." + name + descriptor);
    }
}

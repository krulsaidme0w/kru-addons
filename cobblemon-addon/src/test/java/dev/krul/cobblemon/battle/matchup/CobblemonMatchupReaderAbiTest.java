package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Checks the actual packet boundary without bootstrapping Minecraft registries. */
class CobblemonMatchupReaderAbiTest {
    private static final String ROOT = "com/cobblemon/mod/common/";
    private static final Path JAR = Path.of(System.getenv().getOrDefault("COBBLEMON_RUNTIME_JAR", ""));

    @Test void onlyRequestPokemonCarriesSerializedCurrentTypes() throws Exception {
        assumeTrue(Files.isRegularFile(JAR), "Set COBBLEMON_RUNTIME_JAR to check the native ABI");
        try (var jar = new JarFile(JAR.toFile())) {
            for (String projection : List.of("client/battle/ClientBattlePokemon",
                "net/messages/client/battle/BattleInitializePacket$ActiveBattlePokemonDTO")) {
                var node = read(jar, projection);
                assertFalse(node.methods.stream().anyMatch(m -> m.name.equals("getTypes") || m.name.equals("getTerastallized")));
                method(node, "getUuid", "()Ljava/util/UUID;");
            }
            var pokemon = read(jar, "battles/ShowdownPokemon");
            method(pokemon, "getTypes", "()Ljava/util/List;");
            method(pokemon, "getUuid", "()Ljava/util/UUID;");
            for (String codec : List.of("saveToBuffer", "loadFromBuffer")) {
                var method = pokemon.methods.stream().filter(m -> m.name.equals(codec)).findFirst().orElseThrow();
                assertTrue(Arrays.stream(method.instructions.toArray()).anyMatch(n -> n instanceof FieldInsnNode f
                    && f.getOpcode() == Opcodes.GETFIELD && f.owner.equals(ROOT + "battles/ShowdownPokemon")
                    && f.name.equals("types") && f.desc.equals("Ljava/util/List;")), codec + " must carry types independently of baseTypes");
            }
            assertNull(jar.getJarEntry(ROOT + "net/messages/client/battle/BattlePokemonDTO.class"),
                "Cobblemon 1.8.1 has no repository BattlePokemonDTO; don't treat its old tera contract as current");
            var request = read(jar, "client/battle/SingleActionRequest");
            method(request, "getMoveSet", "()L" + ROOT + "battles/ShowdownMoveset;");
            method(request, "getSide", "()L" + ROOT + "battles/ShowdownSide;");
            method(request, "getResponse", "()L" + ROOT + "battles/ShowdownActionResponse;");
        }
    }

    @Test void revealInstallsPacketRealUuidInLiveSlotWithoutRewritingRepository() throws Exception {
        assumeTrue(Files.isRegularFile(JAR));
        try (var jar = new JarFile(JAR.toFile())) {
            var handler = method(read(jar, "client/net/battle/BattleReplacePokemonHandler"), "handle",
                "(L" + ROOT + "net/messages/client/battle/BattleReplacePokemonPacket;Lnet/minecraft/class_310;)V");
            List<String> calls = new ArrayList<>();
            for (var instruction : handler.instructions) if (instruction instanceof MethodInsnNode m) calls.add(m.owner + "." + m.name);
            assertTrue(calls.contains(ROOT + "net/messages/client/battle/BattleReplacePokemonPacket.getRealPokemon"));
            assertTrue(calls.contains(ROOT + "net/messages/client/battle/BattleInitializePacket$ActiveBattlePokemonDTO.getUuid"));
            assertTrue(calls.contains(ROOT + "client/battle/ActiveClientBattlePokemon.setBattlePokemon"));
            assertFalse(calls.stream().anyMatch(c -> c.contains("ClientBattleInformationRepository")),
                "A reveal may leave the repository projection stale; reader must use the exact live UUID");
        }
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc))
            .findFirst().orElseThrow(() -> new AssertionError(node.name + "." + name + desc));
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(ROOT + name + ".class");
        assertNotNull(entry, name);
        try (var bytes = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            return node;
        }
    }
}

package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DiscoveryObservationAbiTest {

    @Test void installedSpawnMapSupportsExactReaderChain() throws Exception {
        Path path = Path.of(System.getenv().getOrDefault("COBBLENAV_RUNTIME_JAR", ""));
        assumeTrue(Files.isRegularFile(path));
        try (var jar = new JarFile(path.toFile())) {
            var packet = read(jar, "com/metacontent/cobblenav/networking/packet/client/SpawnMapPacket");
            method(packet, "getId", "()Lnet/minecraft/class_2960;");
            method(packet, "getBucketName", "()Ljava/lang/String;");
            method(packet, "getSpawnDataList", "()Ljava/util/List;");
            method(read(jar, "com/metacontent/cobblenav/spawndata/SpawnData"), "getRenderable",
                "()Lcom/cobblemon/mod/common/pokemon/RenderablePokemon;");
        }
    }

    @Test void payloadAccessorIsTypedSoLoomCanRemapItAtRuntime() throws Exception {
        var node = new ClassNode();
        try (var bytes = CobblemonSpawnMapPacketReader.class.getResourceAsStream("CobblemonSpawnMapPacketReader.class")) {
            new ClassReader(Objects.requireNonNull(bytes)).accept(node, 0);
        }
        var reader = method(node, "read", "(Lnet/minecraft/network/packet/Packet;)Ldev/krul/cobblemon/discovery/CobblemonSpawnMapPacketReader$SpawnMap;");
        assertTrue(Arrays.stream(reader.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode m
            && m.owner.equals("net/minecraft/network/packet/s2c/common/CustomPayloadS2CPacket") && m.name.equals("payload")),
            "Reflecting on a named Minecraft method cannot survive production remapping");
    }

    @Test void installedPokemonSpawnSynchronizesEntityOwnerAndTrackedLevel() throws Exception {
        Path path = Path.of(System.getenv().getOrDefault("COBBLEMON_RUNTIME_JAR", ""));
        assumeTrue(Files.isRegularFile(path));
        try (var jar = new JarFile(path.toFile())) {
            String root = "com/cobblemon/mod/common/";
            method(read(jar, root + "pokemon/RenderablePokemon"), "getSpecies", "()L" + root + "pokemon/Species;");
            method(read(jar, root + "pokemon/Species"), "getResourceIdentifier", "()Lnet/minecraft/class_2960;");
            method(read(jar, root + "entity/pokemon/PokemonEntity"), "getLABEL_LEVEL", "()Lnet/minecraft/class_2940;");
            var apply = method(read(jar, root + "net/messages/client/spawn/SpawnPokemonPacket"), "applyData",
                "(L" + root + "entity/pokemon/PokemonEntity;Lnet/minecraft/class_638;)V");
            var calls = Arrays.stream(apply.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
            assertTrue(calls.stream().anyMatch(m -> m.name.equals("method_6174") && m.desc.equals("(Ljava/util/UUID;)V")));
            assertTrue(calls.stream().anyMatch(m -> m.name.equals("getLABEL_LEVEL")));
            assertTrue(calls.stream().anyMatch(m -> m.name.equals("method_12778")), "Spawn applies tracked data");
            for (String setter : List.of("setUuid", "setSpecies", "setGender", "setShiny", "setForm", "setForcedAspects"))
                assertTrue(calls.stream().anyMatch(m -> m.name.equals(setter)), setter);
        }
    }

    private static ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name + ".class");
        assertNotNull(entry, name);
        try (var bytes = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            return node;
        }
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
            .orElseThrow(() -> new AssertionError(node.name + "." + name + desc));
    }
}

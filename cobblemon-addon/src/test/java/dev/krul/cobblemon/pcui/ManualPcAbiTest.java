package dev.krul.cobblemon.pcui;

import java.util.Arrays;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Real runtime class bytes: verifies precisely the optional reflection/packet boundary we call. */
@EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
class ManualPcAbiTest {
    private static final String ROOT = "com/cobblemon/mod/common/";
    private static final String PC = "L" + ROOT + "api/storage/pc/PCPosition;";
    private static final String PARTY = "L" + ROOT + "api/storage/party/PartyPosition;";

    @Test void packetConstructorOrderMatchesNativePcWidgetAndNetworkDispatch() throws Exception {
        try (var jar = jar()) {
            method(read(jar, "api/storage/pc/PCPosition"), "<init>", "(II)V");
            method(read(jar, "api/storage/party/PartyPosition"), "<init>", "(I)V");
            var widget = read(jar, "client/gui/pc/StorageWidget");
            for (boolean intoPc : new boolean[]{true, false}) {
                String name = "net/messages/server/storage/pc/"
                    + (intoPc ? "MovePartyPokemonToPCPacket" : "MovePCPokemonToPartyPacket");
                String descriptor = "(Ljava/util/UUID;" + (intoPc ? PARTY + PC : PC + PARTY) + ")V";
                var packet = read(jar, name);
                method(packet, "<init>", descriptor);
                method(packet, "sendToServer", "()V");
                assertTrue(calls(widget, ROOT + name, "<init>", descriptor), "Native widget must use the same packet ABI");
                assertTrue(calls(packet, ROOT + "api/net/NetworkPacket", "sendToServer", "()V"));
            }
        }
    }
    @Test void liveStorageAndMetadataGettersMatchPinnedJar() throws Exception {
        try (var jar = jar()) {
            var gui = read(jar, "client/gui/pc/PCGUI");
            method(gui, "getPc", "()L" + ROOT + "client/storage/ClientPC;");
            method(gui, "getParty", "()L" + ROOT + "client/storage/ClientParty;");
            method(gui, "getStorage", "()L" + ROOT + "client/gui/pc/StorageWidget;");
            method(gui, "getConfiguration", "()L" + ROOT + "client/gui/pc/PCGUIConfiguration;");
            method(read(jar, "client/gui/pc/StorageWidget"), "getBox", "()I");
            method(read(jar, "client/storage/ClientStorage"), "getUuid", "()Ljava/util/UUID;");
            method(read(jar, "client/storage/ClientPC"), "getBoxes", "()Ljava/util/List;");
            method(read(jar, "client/storage/ClientParty"), "getSlots", "()Ljava/util/List;");
            method(read(jar, "client/storage/ClientBox"), "getSlots", "()Ljava/util/List;");
            method(read(jar, "pokemon/Pokemon"), "getUuid", "()Ljava/util/UUID;");
            var client = read(jar, "client/CobblemonClient");
            singleton(client);
            method(client, "getStorage", "()L" + ROOT + "client/storage/ClientStorageManager;");
            var manager = read(jar, "client/storage/ClientStorageManager");
            method(manager, "getPcStores", "()Ljava/util/Map;");
            method(manager, "getParty", "()L" + ROOT + "client/storage/ClientParty;");
            var config = read(jar, "client/gui/pc/PCGUIConfiguration");
            method(config, "getShowParty", "()Z");
            method(config, "getSelectOverride", "()Lkotlin/jvm/functions/Function3;");
            var settings = read(jar, "client/settings/ServerSettings");
            singleton(settings);
            method(settings, "getPreventCompletePartyDeposit", "()Z");
        }
    }
    @Test void serverHandlersValidateUuidAndRequireNativePcLinkButCanChooseFallbackDestination() throws Exception {
        try (var jar = jar()) {
            for (boolean intoPc : new boolean[]{true, false}) {
                String stem = intoPc ? "MovePartyPokemonToPC" : "MovePCPokemonToParty";
                var handler = read(jar, "net/serverhandling/storage/pc/" + stem + "Handler");
                assertTrue(calls(handler, ROOT + "net/messages/server/storage/pc/" + stem + "Packet",
                    "getPokemonID", "()Ljava/util/UUID;"));
                assertTrue(calls(handler, ROOT + "api/storage/pc/link/PCLinkManager", "getPC",
                    "(Lnet/minecraft/class_3222;)L" + ROOT + "api/storage/pc/PCStore;"));
                assertTrue(calls(handler, ROOT + (intoPc ? "api/storage/pc/PCStore" : "api/storage/party/PlayerPartyStore"),
                    "getFirstAvailablePosition", "()" + (intoPc ? PC : PARTY)),
                    "Documented limitation: occupied destination races can be redirected server-side");
            }
        }
    }
    private static ZipFile jar() throws Exception { return new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR")); }
    private static ClassNode read(ZipFile jar, String path) throws Exception {
        var entry = jar.getEntry(ROOT + path + ".class");
        assertNotNull(entry, path);
        try (var bytes = jar.getInputStream(entry)) {
            var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
        }
    }
    private static void method(ClassNode node, String name, String descriptor) {
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
            && (m.access & Opcodes.ACC_PUBLIC) != 0), node.name + "." + name + descriptor);
    }
    private static void singleton(ClassNode node) {
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("INSTANCE") && f.desc.equals("L" + node.name + ";")
            && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)));
    }
    private static boolean calls(ClassNode node, String owner, String name, String descriptor) {
        return node.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
            .anyMatch(i -> i instanceof MethodInsnNode call && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor));
    }
}

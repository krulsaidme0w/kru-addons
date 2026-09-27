package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.Arrays;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class UiPacketToolsTest {
    @Test void inventoryWhitelistDoesNotExpandToEveryCobblemonPacket() {
        String root = "com.cobblemon.mod.common.net.messages.server.";
        for (String name : new String[]{"storage.pc.MovePCPokemonPacket", "storage.pc.MovePCPokemonToPartyPacket",
            "storage.pc.MovePartyPokemonToPCPacket", "storage.pc.SwapPCPokemonPacket", "storage.SwapPCPartyPokemonPacket",
            "storage.party.MovePartyPokemonPacket", "storage.party.SwapPartyPokemonPacket",
            "storage.pc.RequestRenamePCBoxPacket", "pokemon.update.SetNicknamePacket"})
            assertTrue(UiPacketTools.inventoryPayload(root + name), name);
        for (String name : new String[]{"storage.pc.UnlinkPlayerFromPCPacket", "storage.pc.ReleasePCPokemonPacket",
            "storage.party.ReleasePartyPokemonPacket", "storage.pc.SortPCBoxPacket", "RequestMoveSwapPacket",
            "storage.pc.UnknownPacket", "storage.pc.MovePCPokemonPacket$Companion"})
            assertFalse(UiPacketTools.inventoryPayload(root + name), name);
        assertFalse(UiPacketTools.inventoryPayload("other.mod.MovePCPokemonPacket"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
    void installedPcUnlinkUsesNoArgumentConstructorAndServerSuppliesPlayerUuid() throws Exception {
        String root = "com/cobblemon/mod/common/";
        String unlink = root + "net/messages/server/storage/pc/UnlinkPlayerFromPCPacket";
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            var packet = read(jar, unlink);
            assertTrue(packet.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals("()V")));
            assertTrue(packet.methods.stream().anyMatch(m -> m.name.equals("sendToServer") && m.desc.equals("()V")));
            assertFalse(packet.methods.stream().anyMatch(m -> m.name.equals("<init>") && m.desc.equals("(Ljava/util/UUID;)V")));
            var gui = read(jar, root + "client/gui/pc/PCGUI");
            var close = gui.methods.stream().filter(m -> m.name.equals("closeNormally") && m.desc.equals("(Z)V")).findFirst().orElseThrow();
            assertTrue(Arrays.stream(close.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
                && call.owner.equals(unlink) && call.name.equals("<init>") && call.desc.equals("()V")));
            var handler = read(jar, root + "net/serverhandling/storage/pc/UnlinkPlayerFromPCHandler");
            assertTrue(handler.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray())).anyMatch(i ->
                i instanceof MethodInsnNode call && call.owner.equals(root + "api/storage/pc/link/PCLinkManager")
                    && call.name.equals("removeLink") && call.desc.equals("(Ljava/util/UUID;)V")));
            long whitelisted = jar.stream().filter(e -> e.getName().endsWith(".class"))
                .map(e -> e.getName().substring(0, e.getName().length() - 6).replace('/', '.'))
                .filter(UiPacketTools::inventoryPayload).count();
            assertEquals(9, whitelisted, "Every allowed payload must exist in the installed ABI");
        }
    }

    private static ClassNode read(ZipFile jar, String path) throws Exception {
        var entry = jar.getEntry(path + ".class"); assertNotNull(entry, path);
        try (var bytes = jar.getInputStream(entry)) {
            var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
        }
    }
}

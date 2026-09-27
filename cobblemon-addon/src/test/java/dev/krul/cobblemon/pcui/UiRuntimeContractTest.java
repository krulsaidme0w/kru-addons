package dev.krul.cobblemon.pcui;

import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Boundary checks use real pinned class bytes, without bootstrapping the game registries. */
class UiRuntimeContractTest {
    @Test void commandAndContainerOperationsAreLinkedForProductionRemapping() throws Exception {
        var actions = calls(read("dev/krul/cobblemon/pcui/UiToolsContent"));
        assertTrue(actions.contains("net/minecraft/client/network/ClientPlayNetworkHandler.sendChatCommand(Ljava/lang/String;)V"));
        var close = calls(read("dev/krul/cobblemon/pcui/ContainerMenuAdapter"));
        assertTrue(close.contains("net/minecraft/client/MinecraftClient.setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"));
        assertTrue(close.contains("net/minecraft/client/gui/screen/Screen.close()V"));
    }

    @Test void vanillaLocalRemovalIsDistinctFromServerNotifiedClose() throws Exception {
        var screen = read("net/minecraft/client/gui/screen/ingame/HandledScreen");
        var close = screen.methods.stream().filter(m -> m.name.equals("close")).findFirst().orElseThrow();
        var removed = screen.methods.stream().filter(m -> m.name.equals("removed")).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(close.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
            && call.owner.equals("net/minecraft/client/network/ClientPlayerEntity") && call.name.equals("closeHandledScreen")));
        assertFalse(java.util.Arrays.stream(removed.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call
            && (call.name.equals("closeHandledScreen") || call.name.equals("sendPacket"))));
    }

    @Test @EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
    void installedPcExposesExplicitBooleanCloseAndIsNotAHandledScreen() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            var entry = jar.getEntry("com/cobblemon/mod/common/client/gui/pc/PCGUI.class");
            assertNotNull(entry);
            var node = new ClassNode();
            try (var bytes = jar.getInputStream(entry)) { new ClassReader(bytes).accept(node, 0); }
            assertEquals("net/minecraft/class_437", node.superName);
            assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("getPc") && m.desc.startsWith("()L")));
            assertTrue(node.methods.stream().anyMatch(m -> m.name.equals("closeNormally") && m.desc.equals("(Z)V")));
        }
    }
    private static ClassNode read(String path) throws Exception {
        try (var bytes = UiRuntimeContractTest.class.getResourceAsStream("/" + path + ".class")) {
            assertNotNull(bytes);
            var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
        }
    }
    private static Set<String> calls(ClassNode node) {
        Set<String> result = new HashSet<>();
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc);
        return result;
    }
}

package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import static org.junit.jupiter.api.Assertions.*;

/** Narrow characterization of the vanilla 1.21.1 boundary used by the render/input bridge. */
class OverlayHookAbiTest {
    @Test void drawHookRunsOnceAndBeforeVanillaRestoresGuiModelView() throws Exception {
        var type = read("net/minecraft/client/render/GameRenderer");
        var render = type.methods.stream().filter(m -> m.name.equals("render")
            && m.desc.equals("(Lnet/minecraft/client/render/RenderTickCounter;Z)V")).findFirst().orElseThrow();
        var instructions = render.instructions.toArray();
        int draws = 0, drawIndex = -1, finalPop = -1;
        for (int i = 0; i < instructions.length; i++) if (instructions[i] instanceof MethodInsnNode call) {
            if (call.owner.equals("net/minecraft/client/gui/DrawContext") && call.name.equals("draw") && call.desc.equals("()V")) {
                draws++; drawIndex = i;
            }
            if (call.owner.equals("org/joml/Matrix4fStack") && call.name.equals("popMatrix")) finalPop = i;
        }
        assertEquals(1, draws, "An unqualified draw injection would render twice if this boundary changed");
        assertTrue(finalPop > drawIndex, "Panel rendering requires the native GUI model-view scope");
    }
    @Test void globalInputHooksMatchExactVanillaCallbacks() throws Exception {
        var mouse = read("net/minecraft/client/Mouse");
        assertTrue(mouse.methods.stream().anyMatch(m -> m.name.equals("onMouseButton") && m.desc.equals("(JIII)V")));
        assertTrue(mouse.methods.stream().anyMatch(m -> m.name.equals("onMouseScroll") && m.desc.equals("(JDD)V")));
        assertTrue(read("net/minecraft/client/Keyboard").methods.stream()
            .anyMatch(m -> m.name.equals("onKey") && m.desc.equals("(JIIII)V")));
        assertTrue(read("net/minecraft/client/Keyboard").methods.stream()
            .anyMatch(m -> m.name.equals("onChar") && m.desc.equals("(JII)V")));
    }
    private ClassNode read(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
}

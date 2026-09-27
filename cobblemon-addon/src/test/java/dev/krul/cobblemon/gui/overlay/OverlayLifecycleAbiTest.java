package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native lifecycle boundary: Meteor replays callbacks on join/leave unless opted out.
 * Inspect compiled classes without starting a Minecraft client or mocking its registries. */
class OverlayLifecycleAbiTest {
    @Test void overlayOptsOutOfJoinLeaveCallbackReplay() throws Exception {
        var constructor = read("dev/krul/cobblemon/gui/overlay/OverlayModule").methods.stream()
            .filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        boolean outsideWorldLifecycle = false;
        for (var instruction : constructor.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                && field.name.equals("runInMainMenu") && field.desc.equals("Z")) {
                var value = instruction.getPrevious();
                while (value != null && value.getOpcode() < 0) value = value.getPrevious();
                outsideWorldLifecycle = value != null && value.getOpcode() == Opcodes.ICONST_1;
            }
        }
        assertTrue(outsideWorldLifecycle,
            "Join/leave must not replay manual overlay activation and reopen a dismissed chart");
    }

    @Test void pinnedMeteorSkipsBothCallbacksForWorldIndependentModules() throws Exception {
        var modules = read("meteordevelopment/meteorclient/systems/modules/Modules");
        for (String event : new String[]{"onGameJoined", "onGameLeft"}) {
            var method = modules.methods.stream().filter(m -> m.name.equals(event)).findFirst().orElseThrow();
            String callback = event.equals("onGameJoined") ? "onActivate" : "onDeactivate";
            int call = -1, guard = -1, skip = -1;
            var code = method.instructions;
            for (int i = 0; i < code.size(); i++) {
                var instruction = code.get(i);
                if (instruction instanceof FieldInsnNode field && field.name.equals("runInMainMenu")
                    && field.getOpcode() == Opcodes.GETFIELD) {
                    var branch = instruction.getNext();
                    while (branch != null && branch.getOpcode() < 0) branch = branch.getNext();
                    if (branch instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNE) {
                        guard = i; skip = code.indexOf(jump.label);
                    }
                }
                if (instruction instanceof MethodInsnNode invoked && invoked.name.equals(callback)) call = i;
            }
            assertTrue(guard >= 0 && guard < call && skip > call,
                event + " must skip the lifecycle callback when runInMainMenu is true");
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (var stream = OverlayLifecycleAbiTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            var result = new ClassNode();
            new ClassReader(stream).accept(result, 0);
            return result;
        }
    }
}

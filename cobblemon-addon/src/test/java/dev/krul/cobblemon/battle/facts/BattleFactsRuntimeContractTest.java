package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.matchup.CobblemonMatchupReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABI and linked-call checks against real class bytes; no game/OpenGL initialization.
 * Plain JUnit cannot bootstrap Minecraft registries: SimpleRegistry needs launch-time access
 * transformations for RegistryEntry.Reference.setRegistryKey. Check the actual remappable
 * ItemStack call here, without a fake item or changes to vanilla/bootstrap/build configuration.
 */
class BattleFactsRuntimeContractTest {
    private static final String NATIVE = "com/cobblemon/mod/common/";

    @Test void emptyItemCheckIsABytecodeLinkThatLoomCanRemap() throws Exception {
        Set<String> calls = new HashSet<>(), literals = new HashSet<>(), checkedTypes = new HashSet<>();
        try (var bytes = CobblemonMatchupReader.class.getResourceAsStream("CobblemonMatchupReader.class")) {
            assertNotNull(bytes);
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            if (opcode == Opcodes.INVOKEVIRTUAL) calls.add(owner + "." + name + desc);
                        }
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.INSTANCEOF) checkedTypes.add(type);
                        }
                        @Override public void visitLdcInsn(Object value) {
                            if (value instanceof String text) literals.add(text);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(checkedTypes.contains("net/minecraft/item/ItemStack"));
        assertTrue(calls.contains("net/minecraft/item/ItemStack.isEmpty()Z"));
        assertFalse(literals.contains("isEmpty"), "Reflective Minecraft names are not remapped");
        assertFalse(literals.contains("getBoosts"), "ShowdownPokemon has no such getter");
    }

    @Test @EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
    void runtimeStageMapsAreIntegerStatMapsAndBuffsAreDoubleMultipliers() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            String stages = "()Ljava/util/Map<L" + NATIVE + "api/pokemon/stats/Stat;Ljava/lang/Integer;>;";
            assertEquals(stages, signatures(jar, "client/battle/ClientBattlePokemon").get("getStatChanges"));
            assertEquals(stages, signatures(jar, "net/messages/client/battle/BattleInitializePacket$ActiveBattlePokemonDTO").get("getStatChanges"));
            assertEquals("()Ljava/util/Map<L" + NATIVE + "api/pokemon/stats/Stats;Ljava/lang/Double;>;",
                signatures(jar, "net/messages/client/battle/BattlePokemonDTO").get("getBuffs"));
            assertEquals("()Ljava/lang/String;", signatures(jar, "api/pokemon/stats/Stat").get("getShowdownId"));
            assertFalse(signatures(jar, "battles/ShowdownPokemon").containsKey("getBoosts"));
        }
    }

    private static Map<String, String> signatures(ZipFile jar, String owner) throws Exception {
        var entry = jar.getEntry(NATIVE + owner + ".class");
        assertNotNull(entry, owner);
        Map<String, String> signatures = new HashMap<>();
        try (var bytes = jar.getInputStream(entry)) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_PUBLIC) != 0) signatures.put(name, signature == null ? descriptor : signature);
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        }
        return signatures;
    }
}

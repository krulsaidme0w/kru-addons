package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.PokemonType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Real installed bytes only: ABI evidence, not a claim of successful OpenGL rendering. */
@EnabledIfEnvironmentVariable(named = "COBBLEMON_RUNTIME_JAR", matches = ".+")
class MatchupNativeIconsContractTest {
    private static final String NATIVE = "com/cobblemon/mod/common/";

    @Test void installedForkExposesRequiredSharedModelCleanup() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            String model = "client/render/models/blockbench/PosableModel";
            require(jar, model, "resetLayerContext", "()V", false);
            require(jar, model, "setDefault", "()V", false);
            require(jar, model, "getCurrentState", "()L" + NATIVE + "client/render/models/blockbench/PosableState;", false);
            require(jar, model, "getBufferProvider", "()Lnet/minecraft/class_4597;", false);
            require(jar, model, "getCurrentLayers", "()Ljava/lang/Iterable;", false);
            assertTrue(read(jar, model).fields.stream().anyMatch(f -> f.name.equals("context")
                && f.desc.equals("L" + NATIVE + "client/render/models/blockbench/repository/RenderContext;")
                && (f.access & Opcodes.ACC_PUBLIC) != 0 && (f.access & (Opcodes.ACC_FINAL | Opcodes.ACC_STATIC)) == 0));
        }
    }

    // Wrong overloads, primitive boxing, or Kotlin synthetic constructor assumptions
    // would make the optional portrait capability unavailable on this installed fork.
    @Test void installedForkProvidesTheExactPortraitBoundary() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            require(jar, "api/pokemon/PokemonSpecies", "getByIdentifier",
                "(Lnet/minecraft/class_2960;)L" + NATIVE + "pokemon/Species;", true);
            require(jar, "pokemon/RenderablePokemon", "<init>",
                "(L" + NATIVE + "pokemon/Species;Ljava/util/Set;Lnet/minecraft/class_1799;)V", false);
            require(jar, "client/render/models/blockbench/FloatingState", "<init>", "()V", false);
            require(jar, "client/gui/PokemonGuiUtilsKt", "drawProfilePokemon",
                "(L" + NATIVE + "pokemon/RenderablePokemon;Lnet/minecraft/class_4587;Lorg/joml/Quaternionf;L"
                    + NATIVE + "entity/PoseType;L" + NATIVE + "client/render/models/blockbench/PosableState;FFZZFFFFFF)V", true);
            requireField(jar, "entity/PoseType", "PROFILE", "L" + NATIVE + "entity/PoseType;");
        }
    }

    // This fork adds isStellar: upstream's nine-argument TypeIcon constructor is incompatible.
    @Test void installedForkProvidesTheSmallNonStellarTypeBoundary() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            require(jar, "client/gui/TypeIcon", "<init>",
                "(Ljava/lang/Number;Ljava/lang/Number;L" + NATIVE + "api/types/ElementalType;L"
                    + NATIVE + "api/types/ElementalType;ZZFFFZ)V", false);
            require(jar, "client/gui/TypeIcon", "render", "(Lnet/minecraft/class_332;)V", false);
            for (var type : PokemonType.values()) {
                requireField(jar, "api/types/ElementalTypes", type.name(), "L" + NATIVE + "api/types/ElementalType;");
            }
        }
    }

    // Repository.getPoser silently falls back to Substitute. Check the exact
    // species resolver and real texture availability before calling the renderer.
    @Test void installedForkSupportsCheckingAssetsWithoutSubstituteFallback() throws Exception {
        try (var jar = new ZipFile(System.getenv("COBBLEMON_RUNTIME_JAR"))) {
            String repository = "client/render/models/blockbench/repository/VaryingModelRepository";
            String resolver = "client/render/VaryingRenderableResolver";
            String state = "L" + NATIVE + "client/render/models/blockbench/PosableState;";
            requireField(jar, repository, "INSTANCE", "L" + NATIVE + repository + ";");
            require(jar, repository, "getVariations", "()Ljava/util/Map;", false);
            require(jar, resolver, "getPoser", "(" + state + ")L" + NATIVE + "client/render/models/blockbench/PosableModel;", false);
            require(jar, resolver, "getTexture", "(" + state + ")Lnet/minecraft/class_2960;", false);
            require(jar, resolver, "getSprite", "(" + state + "L" + NATIVE + "client/render/SpriteType;)Lnet/minecraft/class_2960;", false);
            require(jar, "client/render/models/blockbench/PosableState", "setCurrentAspects", "(Ljava/util/Set;)V", false);
            require(jar, "client/util/ClientDistributionUtilsKt", "exists", "(Lnet/minecraft/class_2960;)Z", true);
            requireField(jar, "client/render/SpriteType", "PROFILE", "L" + NATIVE + "client/render/SpriteType;");
        }
    }

    private static void require(ZipFile jar, String owner, String name, String descriptor, boolean isStatic) throws Exception {
        var node = read(jar, owner);
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
            && (m.access & Opcodes.ACC_PUBLIC) != 0 && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic),
            node.name + "." + name + descriptor);
    }

    private static void requireField(ZipFile jar, String owner, String name, String descriptor) throws Exception {
        var node = read(jar, owner);
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)
            && (f.access & access) == access), node.name + "." + name);
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(NATIVE + name + ".class");
        assertNotNull(entry, name);
        try (var input = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}

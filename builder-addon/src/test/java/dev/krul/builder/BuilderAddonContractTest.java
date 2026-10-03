package dev.krul.builder;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class BuilderAddonContractTest {
    private JarFile archive() throws Exception {
        return new JarFile(System.getProperty("builder.archive"));
    }

    @Test
    void packagedBuilderWindowKeepsBoundsAndStatusWithoutActionControls() throws Exception {
        try (var jar = archive()) {
            Set<String> screen = stringConstants(jar, "dev/krul/builder/ui/BuilderScreen.class");
            for (String removed : List.of("Scan / Rescan", "Overlay: ", "Collapse", "Allow", "Reject", "Revoke pairing"))
                assertFalse(screen.contains(removed), "obsolete Builder action remains visible: " + removed);
            Set<String> module = stringConstants(jar, "dev/krul/builder/minecraft/BuilderModule.class");
            assertTrue(module.containsAll(Set.of("X1", "Y1", "Z1", "X2", "Y2", "Z2")));
            assertFalse(module.contains("Apply"), "bounds should auto-apply after typing");
            assertFalse(module.contains("Clear"), "bounds should be edited directly instead of cleared by a button");
            assertFalse(module.contains("mark-builder-first"));
            assertFalse(module.contains("mark-builder-second"));
            assertTrue(screen.contains("Builder"));
        }
    }

    private static Set<String> stringConstants(JarFile jar, String className) throws Exception {
        var entry = jar.getJarEntry(className);
        assertNotNull(entry, className);
        Set<String> values = new HashSet<>();
        try (var stream = jar.getInputStream(entry)) {
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) {
                            if (value instanceof String text) values.add(text);
                        }
                    };
                }
            }, 0);
        }
        return values;
    }

    @Test
    void shippedMetadataDeclaresOnlyTheIndependentClientContract() throws Exception {
        try (var jar = archive(); var reader = new InputStreamReader(
            jar.getInputStream(jar.getJarEntry("fabric.mod.json")), StandardCharsets.UTF_8)) {
            var mod = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(1, mod.get("schemaVersion").getAsInt());
            assertEquals("kru-builder", mod.get("id").getAsString());
            assertEquals("KRU Builder", mod.get("name").getAsString());
            assertEquals("0.1.0-alpha.1", mod.get("version").getAsString());
            assertEquals("client", mod.get("environment").getAsString());
            assertEquals("GPL-3.0-or-later", mod.get("license").getAsString());
            assertEquals(List.of("krul"), mod.getAsJsonArray("authors").asList().stream()
                .map(e -> e.getAsString()).toList());
            var entrypoints = mod.getAsJsonObject("entrypoints");
            assertEquals(Set.of("meteor"), entrypoints.keySet());
            assertEquals(List.of("dev.krul.builder.BuilderAddon"), entrypoints.getAsJsonArray("meteor")
                .asList().stream().map(e -> e.getAsString()).toList());
            var depends = mod.getAsJsonObject("depends");
            assertEquals(Set.of("minecraft", "java", "fabricloader", "meteor-client"), depends.keySet());
            assertEquals("1.21.1", depends.get("minecraft").getAsString());
            assertEquals(">=21", depends.get("java").getAsString());
            assertEquals(">=0.16.14", depends.get("fabricloader").getAsString());
            assertEquals("0.5.8", depends.get("meteor-client").getAsString());
            assertFalse(mod.has("jars"));
        }
    }

    @Test
    void archiveContainsEntrypointAndNoticesWithoutBundledDependencies() throws Exception {
        try (var jar = archive()) {
            for (var required : List.of("dev/krul/builder/BuilderAddon.class",
                "dev/krul/builder/BuilderCategory.class", "LICENSE", "NOTICE", "THIRD_PARTY.md")) {
                assertNotNull(jar.getJarEntry(required), required);
            }
            for (var entry : jar.stream().toList()) {
                String name = entry.getName();
                assertFalse(name.endsWith(".jar"), name);
                if (name.endsWith(".class")) {
                    assertTrue(name.startsWith("dev/krul/builder/"), name);
                }
            }
            for (String removed : List.of("dev/krul/builder/agent/CodexProcess.class",
                "dev/krul/builder/archive/ConversationArchiveStore.class",
                "dev/krul/builder/ui/BuilderController.class",
                "dev/krul/builder/visual/AttachmentPolicy.class")) {
                assertNull(jar.getJarEntry(removed), "legacy embedded-chat class must not ship: " + removed);
            }
            assertNotNull(jar.getJarEntry("dev/krul/builder/project/BuildPlan.class"));
            assertNotNull(jar.getJarEntry("dev/krul/builder/minecraft/BuilderBridgeDataSource.class"));
        }
    }

    @Test
    void packagedEntrypointUsesMeteorCategoryCallbackAndRegistersBuilderModule() throws Exception {
        try (var jar = archive()) {
            var entry = jar.getJarEntry("dev/krul/builder/BuilderAddon.class");
            assertNotNull(entry, "Meteor entrypoint class must ship");
            try (var stream = jar.getInputStream(entry)) {
                var reader = new ClassReader(stream);
                assertEquals("meteordevelopment/meteorclient/addons/MeteorAddon", reader.getSuperName());
                var registrations = new ArrayList<String>();
                var categoryReads = new ArrayList<String>();
                var initializations = new ArrayList<String>();
                var packageNames = new ArrayList<Object>();
                var methods = new ArrayList<String>();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if ((access & Opcodes.ACC_PUBLIC) != 0) methods.add(name + descriptor);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                                if (name.equals("onRegisterCategories") && opcode == Opcodes.GETSTATIC) {
                                    categoryReads.add(owner + "." + field + ":" + desc);
                                }
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String method,
                                                        String desc, boolean isInterface) {
                                String call = owner + "." + method + desc;
                                if (name.equals("onRegisterCategories")) registrations.add(call);
                                if (name.equals("onInitialize")) initializations.add(call);
                            }

                            @Override
                            public void visitLdcInsn(Object value) {
                                if (name.equals("getPackage")) packageNames.add(value);
                            }
                        };
                    }
                }, 0);
                assertTrue(methods.containsAll(List.of("<init>()V", "onRegisterCategories()V",
                    "onInitialize()V", "getPackage()Ljava/lang/String;")));
                assertEquals(List.of("meteordevelopment/meteorclient/systems/modules/Modules.registerCategory"
                    + "(Lmeteordevelopment/meteorclient/systems/modules/Category;)V"), registrations);
                assertEquals(List.of("dev/krul/builder/BuilderCategory.BUILDER:"
                    + "Lmeteordevelopment/meteorclient/systems/modules/Category;"), categoryReads);
                assertEquals(List.of(
                    "meteordevelopment/meteorclient/systems/modules/Modules.get()Lmeteordevelopment/meteorclient/systems/modules/Modules;",
                    "dev/krul/builder/minecraft/BuilderModule.<init>()V",
                    "meteordevelopment/meteorclient/systems/modules/Modules.add(Lmeteordevelopment/meteorclient/systems/modules/Module;)V"
                ), initializations);
                assertEquals(List.of("dev.krul.builder"), packageNames);
            }
        }
    }

    @Test
    void packagedCategoryConstructsTheBuilderMeteorCategory() throws Exception {
        try (var jar = archive()) {
            var entry = jar.getJarEntry("dev/krul/builder/BuilderCategory.class");
            assertNotNull(entry, "Builder category class must ship");
            var names = new ArrayList<Object>();
            var constructors = new ArrayList<String>();
            try (var stream = jar.getInputStream(entry)) {
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        if (!name.equals("<clinit>")) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitLdcInsn(Object value) {
                                names.add(value);
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String method,
                                                        String desc, boolean isInterface) {
                                constructors.add(owner + "." + method + desc);
                            }
                        };
                    }
                }, 0);
            }
            assertEquals(List.of("Builder"), names);
            assertEquals(List.of("meteordevelopment/meteorclient/systems/modules/Category.<init>"
                + "(Ljava/lang/String;)V"), constructors);
        }
    }
}

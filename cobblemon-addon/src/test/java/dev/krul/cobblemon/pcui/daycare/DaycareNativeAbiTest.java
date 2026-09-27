package dev.krul.cobblemon.pcui.daycare;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Bytecode proof against the pinned native jar; never loads its Minecraft classes. */
class DaycareNativeAbiTest {
    private static final String PREFIX = "com/cobblemon/mod/common/";
    private static final Path PINNED = Path.of(System.getenv().getOrDefault("KRU_COBBLEMON_RUNTIME_JAR", ""));

    @Test void publicReaderAndDispatchDescriptorsMatchPinnedJar() throws Exception {
        assumeTrue(Files.isRegularFile(PINNED), "Pinned runtime absent; set KRU_COBBLEMON_RUNTIME_JAR");
        try (var jar = new JarFile(PINNED.toFile())) {
            method(jar, "client/CobblemonClient", "getStorage", "()L" + PREFIX + "client/storage/ClientStorageManager;");
            method(jar, "client/storage/ClientStorageManager", "getParty", "()L" + PREFIX + "client/storage/ClientParty;");
            method(jar, "client/storage/ClientStorageManager", "getPcStores", "()Ljava/util/Map;");
            method(jar, "client/storage/ClientParty", "getSlots", "()Ljava/util/List;");
            method(jar, "client/storage/ClientPC", "getBoxes", "()Ljava/util/List;");
            method(jar, "client/storage/ClientBox", "getSlots", "()Ljava/util/List;");
            method(jar, "pokemon/Pokemon", "getUuid", "()Ljava/util/UUID;");
            method(jar, "pokemon/Pokemon", "getSpecies", "()L" + PREFIX + "pokemon/Species;");
            method(jar, "pokemon/Pokemon", "getLevel", "()I");
            method(jar, "pokemon/Pokemon", "getAspects", "()Ljava/util/Set;");
            method(jar, "pokemon/Species", "getResourceIdentifier", "()Lnet/minecraft/class_2960;");
            method(jar, "client/gui/interact/partyselect/PartySelectGUI", "getConfig", "()L" + PREFIX + "client/gui/interact/partyselect/PartySelectConfiguration;");
            method(jar, "client/gui/interact/partyselect/PartySelectGUI", "getClosed", "()Z");
            method(jar, "client/gui/interact/partyselect/PartySelectConfiguration", "getTitle", "()Lnet/minecraft/class_5250;");
            method(jar, "client/gui/interact/partyselect/PartySelectConfiguration", "getPokemon", "()Ljava/util/List;");
            method(jar, "client/gui/interact/partyselect/PartySelectConfiguration", "getOnSelect", "()Lkotlin/jvm/functions/Function2;");
            method(jar, "api/callback/PartySelectPokemonDTO", "getPokemonProperties", "()L" + PREFIX + "api/pokemon/PokemonProperties;");
            method(jar, "api/callback/PartySelectPokemonDTO", "getAspects", "()Ljava/util/Set;");
            method(jar, "api/callback/PartySelectPokemonDTO", "getEnabled", "()Z");
            method(jar, "api/pokemon/PokemonProperties", "getSpecies", "()Ljava/lang/String;");
            method(jar, "api/pokemon/PokemonProperties", "getLevel", "()Ljava/lang/Integer;");
            method(jar, "item/components/PokemonItemComponent", "getSpecies", "()Lnet/minecraft/class_2960;");
            method(jar, "item/components/PokemonItemComponent", "getAspects", "()Ljava/util/Set;");
            assertTrue(read(jar, "CobblemonItemComponents").fields.stream().anyMatch(f -> f.name.equals("POKEMON_ITEM")
                && f.desc.equals("Lnet/minecraft/class_9331;")));
            method(jar, "client/gui/dialogue/DialogueScreen", "getDialogueDTO", "()L" + PREFIX + "net/messages/client/dialogue/dto/DialogueDTO;");
            method(jar, "client/gui/dialogue/DialogueScreen", "getDialogueOptionWidgets", "()Ljava/util/List;");
            method(jar, "client/gui/dialogue/DialogueScreen", "getWaitingForServerUpdate", "()Z");
            method(jar, "client/gui/dialogue/DialogueScreen", "renderInput", "()Z");
            method(jar, "net/messages/client/dialogue/dto/DialogueDTO", "getCurrentPageDTO", "()L" + PREFIX + "net/messages/client/dialogue/dto/DialoguePageDTO;");
            method(jar, "net/messages/client/dialogue/dto/DialogueDTO", "getDialogueInput", "()L" + PREFIX + "net/messages/client/dialogue/dto/DialogueInputDTO;");
            method(jar, "net/messages/client/dialogue/dto/DialoguePageDTO", "getLines", "()Ljava/util/List;");
            method(jar, "net/messages/client/dialogue/dto/DialogueInputDTO", "getInputId", "()Ljava/util/UUID;");
            method(jar, "client/gui/dialogue/widgets/DialogueOptionWidget", "getText", "()Lnet/minecraft/class_5250;");
            method(jar, "client/gui/dialogue/widgets/DialogueOptionWidget", "getValue", "()Ljava/lang/String;");
            method(jar, "client/gui/dialogue/widgets/DialogueOptionWidget", "getSelectable", "()Z");
            method(jar, "net/messages/server/dialogue/InputToDialoguePacket", "<init>", "(Ljava/util/UUID;Ljava/lang/String;)V");
            method(jar, "client/gui/dialogue/DialogueScreen", "sendToServer", "(L" + PREFIX + "api/net/NetworkPacket;)V");
        }
    }

    @Test void nativeCallbacksUseDtoIdentityAndSetWaitingWithoutSecondSemanticRequest() throws Exception {
        assumeTrue(Files.isRegularFile(PINNED));
        try (var jar = new JarFile(PINNED.toFile())) {
            var picker = read(jar, "client/gui/interact/partyselect/PartySelectGUI");
            var callback = picker.methods.stream().filter(m -> m.name.equals("_init_$lambda$1")).findFirst().orElseThrow();
            assertEquals(1, calls(callback, "java/util/List", "indexOf"));
            assertEquals(1, calls(callback, PREFIX + "net/messages/server/callback/party/PartyPokemonSelectedPacket", "<init>"));
            assertEquals(1, calls(callback, PREFIX + "client/gui/interact/partyselect/PartySelectGUI", "closeProperly"));
            var send = method(jar, "client/gui/dialogue/DialogueScreen", "sendToServer", "(L" + PREFIX + "api/net/NetworkPacket;)V");
            assertEquals(1, calls(send, PREFIX + "api/net/NetworkPacket", "sendToServer"));
            assertTrue(java.util.Arrays.stream(send.instructions.toArray()).anyMatch(n -> n instanceof FieldInsnNode f
                && f.name.equals("waitingForServerUpdate") && f.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD));
        }
    }

    private long calls(MethodNode method, String owner, String name) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode m
            && m.owner.equals(owner) && m.name.equals(name)).count();
    }
    private MethodNode method(JarFile jar, String type, String name, String descriptor) throws Exception {
        return read(jar, type).methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
            .findFirst().orElseThrow(() -> new AssertionError(type + "." + name + descriptor));
    }
    private ClassNode read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(PREFIX + name + ".class"); assertNotNull(entry, name);
        try (var in = jar.getInputStream(entry)) { var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node; }
    }
}

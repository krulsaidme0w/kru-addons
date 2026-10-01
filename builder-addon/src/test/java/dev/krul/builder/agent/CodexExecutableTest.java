package dev.krul.builder.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CodexExecutableTest {
    @TempDir Path instance;

    @Test void defaultUsesInstalledNativeExecutableWithoutRelyingOnPath() throws Exception {
        Path binary = instance.resolve("kru-builder/bin/codex.exe");
        Files.createDirectories(binary.getParent());
        Files.createFile(binary);
        assertEquals(binary.toAbsolutePath().toString(), CodexExecutable.resolve(instance, "codex", true));
    }

    @Test void explicitChoiceIsNeverOverridden() {
        assertEquals("D:\\tools\\codex.exe", CodexExecutable.resolve(instance, "D:\\tools\\codex.exe", true));
    }

    @Test void missingInstallationPreservesConfiguredLookup() {
        assertEquals("codex", CodexExecutable.resolve(instance, "codex", true));
        assertEquals("codex", CodexExecutable.resolve(instance, "codex", false));
    }
}

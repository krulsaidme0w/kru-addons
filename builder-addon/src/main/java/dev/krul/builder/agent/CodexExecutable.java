package dev.krul.builder.agent;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;

/** Selects an explicitly installed, instance-local CLI without changing global configuration. */
public final class CodexExecutable {
    private CodexExecutable() {}

    public static String resolve(Path instance, String configured, boolean windows) {
        if ("codex".equals(configured)) {
            Path local = instance.resolve("kru-builder/bin").resolve(windows ? "codex.exe" : "codex");
            if (Files.isRegularFile(local, LinkOption.NOFOLLOW_LINKS))
                return local.toAbsolutePath().normalize().toString();
        }
        return configured;
    }
}

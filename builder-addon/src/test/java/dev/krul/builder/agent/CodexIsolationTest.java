package dev.krul.builder.agent;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodexIsolationTest {
    @TempDir Path temporary;

    @Test void createsOwnedProfileAndEmptyWorkingDirectory() throws Exception {
        var layout = CodexIsolation.prepare(temporary);
        assertEquals(temporary.resolve("kru-builder/codex-home").toRealPath(), layout.home());
        assertEquals(temporary.resolve("kru-builder/empty").toRealPath(), layout.cwd());
        assertTrue(Files.isDirectory(layout.home()));
        assertTrue(Files.isDirectory(layout.cwd()));
        assertTrue(isEmpty(layout.cwd()));
    }

    @Test void refusesContentsInWorkingDirectoryAndUnsafeProfileFiles() throws Exception {
        var layout = CodexIsolation.prepare(temporary);
        Files.writeString(layout.cwd().resolve("AGENTS.md"), "untrusted");
        assertThrows(CodexIsolation.IncompatibleException.class, () -> CodexIsolation.prepare(temporary));
    }

    @Test void startupArgumentsPinCliAndDisableAllAmbientTools() {
        assertEquals(java.util.List.of("app-server", "--stdio", "--strict-config",
            "-c", "features.hooks=false", "-c", "features.plugins=false", "-c", "features.apps=false",
            "-c", "features.multi_agent=false", "-c", "features.image_generation=false",
            "-c", "features.code_mode=false", "-c", "features.code_mode_host=false",
            "-c", "features.memories=false", "-c", "skills.include_instructions=false",
            "-c", "skills.bundled.enabled=false", "-c", "orchestrator.skills.enabled=false",
            "-c", "notify=[]", "-c", "web_search=\"disabled\"", "-c", "project_doc_max_bytes=0"), CodexIsolation.arguments());
    }

    @Test void childEnvironmentDoesNotInheritCredentialsOrRemoteEndpoints() {
        var env = CodexIsolation.childEnvironment(Map.of("PATH", "/bin", "OPENAI_API_KEY", "secret",
            "CODEX_HOME", "/personal", "OPENAI_BASE_URL", "https://evil.invalid", "LANG", "C.UTF-8"),
            temporary.resolve("kru-builder/codex-home"));
        assertEquals("/bin", env.get("PATH"));
        assertEquals("C.UTF-8", env.get("LANG"));
        assertEquals(temporary.resolve("kru-builder/codex-home").toString(), env.get("CODEX_HOME"));
        assertFalse(env.containsKey("OPENAI_API_KEY"));
        assertFalse(env.containsKey("OPENAI_BASE_URL"));
        assertFalse(env.containsValue("/personal"));
    }

    @Test void rejectsEffectiveConfigThatReEnablesToolsOrChangesProvider() {
        var config = safeConfig();
        var safe = config;
        assertDoesNotThrow(() -> CodexIsolation.validateEffectiveConfig(safe));
        var reenabled = safeConfig();
        reenabled.getAsJsonObject("features").addProperty("plugins", true);
        assertThrows(CodexIsolation.IncompatibleException.class,
            () -> CodexIsolation.validateEffectiveConfig(reenabled));
        var unsafeProvider = safeConfig();
        unsafeProvider.addProperty("model_provider", "custom");
        assertThrows(CodexIsolation.IncompatibleException.class,
            () -> CodexIsolation.validateEffectiveConfig(unsafeProvider));
    }

    @Test void rejectsAnyConfiguredMcpServerAndUnknownLayerSource() {
        var config = safeConfig();
        config.add("mcp_servers", new JsonObject());
        config.getAsJsonObject("mcp_servers").add("personal", new JsonObject());
        assertThrows(CodexIsolation.IncompatibleException.class,
            () -> CodexIsolation.validateEffectiveConfig(config));
        assertThrows(CodexIsolation.IncompatibleException.class,
            () -> CodexIsolation.validateLayers(new JsonObject()));
    }

    private static JsonObject safeConfig() {
        var config = new JsonObject();
        var features = new JsonObject();
        for (var key : new String[]{"hooks", "plugins", "apps", "multi_agent", "code_mode",
                "code_mode_host", "image_generation", "memories"}) features.addProperty(key, false);
        config.add("features", features);
        config.addProperty("web_search", "disabled");
        config.addProperty("approval_policy", "never");
        config.addProperty("sandbox_mode", "read-only");
        config.addProperty("project_doc_max_bytes", 0);
        config.add("notify", new com.google.gson.JsonArray());
        var skills = new JsonObject(); skills.addProperty("include_instructions", false);
        var bundled = new JsonObject(); bundled.addProperty("enabled", false); skills.add("bundled", bundled);
        config.add("skills", skills);
        var orchestrator = new JsonObject(); orchestrator.addProperty("enabled", false);
        config.add("orchestrator", new JsonObject()); config.getAsJsonObject("orchestrator").add("skills", orchestrator);
        return config;
    }

    private static boolean isEmpty(Path directory) throws Exception {
        try (var files = Files.list(directory)) { return files.findAny().isEmpty(); }
    }
}

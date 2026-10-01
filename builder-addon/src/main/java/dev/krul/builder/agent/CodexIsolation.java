package dev.krul.builder.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the narrow process boundary used by Builder's Codex transport. */
public final class CodexIsolation {
    public static final String PINNED_VERSION = "codex-cli 0.153.4";
    private static final Set<String> LAYER_TYPES = Set.of("packagedDefaults", "user", "sessionFlags",
        "system", "mdm", "enterpriseManaged", "legacyManagedConfigTomlFromFile",
        "legacyManagedConfigTomlFromMdm");
    private static final List<String> CONFIG_OVERRIDES = List.of(
        "features.hooks=false", "features.plugins=false", "features.apps=false",
        "features.multi_agent=false", "features.image_generation=false", "features.code_mode=false",
        "features.code_mode_host=true", "features.memories=false", "skills.include_instructions=false",
        "skills.bundled.enabled=false", "orchestrator.skills.enabled=false", "notify=[]",
        "web_search=\"disabled\"", "project_doc_max_bytes=0",
        "approval_policy=\"never\"", "sandbox_mode=\"read-only\"");

    private CodexIsolation() {}

    public record Layout(Path home, Path cwd) {}

    public static final class IncompatibleException extends IOException {
        public IncompatibleException(String message) { super(message); }
        public IncompatibleException(String message, Throwable cause) { super(message, cause); }
    }

    /** Creates addon-owned locations. Existing login/cache data is permitted in home. */
    public static Layout prepare(Path instanceDirectory) throws IOException {
        Path instance = instanceDirectory.toAbsolutePath().normalize();
        ensureNoSymlink(instance);
        Path owned = instance.resolve("kru-builder");
        createOwnedDirectory(owned);
        Path home = owned.resolve("codex-home");
        Path cwd = owned.resolve("empty");
        createOwnedDirectory(home);
        createOwnedDirectory(cwd);
        for (String forbidden : List.of("config.toml", "AGENTS.md", "AGENTS.override.md", "skills", "plugins", "marketplaces")) {
            if (Files.exists(home.resolve(forbidden), LinkOption.NOFOLLOW_LINKS))
                throw new IncompatibleException("Builder Codex profile contains unsupported " + forbidden);
        }
        try (var entries = Files.list(cwd)) {
            if (entries.findAny().isPresent()) throw new IncompatibleException("Builder Codex working directory is not empty");
        }
        return new Layout(home.toRealPath(), cwd.toRealPath());
    }

    private static void ensureNoSymlink(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path part : path) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
                throw new IncompatibleException("Builder Codex path contains a symbolic link");
        }
    }

    private static void createOwnedDirectory(Path path) throws IOException {
        ensureNoSymlink(path);
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IncompatibleException("Builder Codex path is not an owned directory");
    }

    public static List<String> arguments() {
        var args = new ArrayList<String>(List.of("app-server", "--stdio", "--strict-config"));
        for (String override : CONFIG_OVERRIDES) { args.add("-c"); args.add(override); }
        return List.copyOf(args);
    }

    /** Explicit allowlist; parent HOME, Codex settings, auth and provider tokens never cross. */
    public static Map<String, String> childEnvironment(Map<String, String> parent, Path home) {
        var result = new java.util.HashMap<String, String>();
        for (String key : List.of("PATH", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP", "SystemRoot", "WINDIR")) {
            String value = parent.get(key);
            if (value != null && !value.isBlank()) result.put(key, value);
        }
        result.put("CODEX_HOME", home.toAbsolutePath().normalize().toString());
        result.put("HOME", home.toAbsolutePath().normalize().toString());
        result.put("USERPROFILE", home.toAbsolutePath().normalize().toString());
        return Map.copyOf(result);
    }

    public static void validateVersion(String versionLine) throws IncompatibleException {
        if (!PINNED_VERSION.equals(versionLine == null ? null : versionLine.trim()))
            throw new IncompatibleException("Unsupported Codex CLI version; expected " + PINNED_VERSION);
    }

    /** Validates the effective config object returned by config/read without retaining/logging it. */
    public static void validateEffectiveConfig(JsonObject config) throws IncompatibleException {
        if (config == null) throw new IncompatibleException("Codex effective configuration is unavailable");
        JsonObject features = object(config, "features");
        for (String feature : List.of("hooks", "plugins", "apps", "multi_agent", "code_mode",
                "image_generation", "memories")) requireBoolean(features, feature, false);
        requireBoolean(features, "code_mode_host", true);
        requireString(config, "web_search", "disabled");
        requireString(config, "approval_policy", "never");
        requireString(config, "sandbox_mode", "read-only");
        JsonElement projectDocs = config.get("project_doc_max_bytes");
        if (projectDocs == null || !projectDocs.isJsonPrimitive() || !projectDocs.getAsJsonPrimitive().isNumber()
                || projectDocs.getAsInt() != 0)
            throw new IncompatibleException("Codex project instruction budget is not disabled");
        requireEmptyArray(config, "notify");
        JsonObject skills = object(config, "skills");
        requireBoolean(skills, "include_instructions", false);
        requireBoolean(object(skills, "bundled"), "enabled", false);
        requireBoolean(object(object(config, "orchestrator"), "skills"), "enabled", false);
        JsonElement mcp = config.get("mcp_servers");
        if (mcp != null && (!mcp.isJsonObject() || !mcp.getAsJsonObject().isEmpty()))
            throw new IncompatibleException("Configured MCP servers are not allowed for Builder");
        for (String field : List.of("model_provider", "openai_base_url", "chatgpt_base_url", "model_instructions_file",
                "instructions", "developer_instructions", "compact_prompt")) {
            JsonElement value = config.get(field);
            // Native Windows CLI serializes its built-in endpoint instead of null.
            if (field.equals("chatgpt_base_url") && value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()
                    && Set.of("https://chatgpt.com/backend-api/", "https://chatgpt.com/backend-api").contains(value.getAsString())) continue;
            if (value != null && !value.isJsonNull() && !(value.isJsonPrimitive() && value.getAsString().isBlank()))
                throw new IncompatibleException("Custom Codex provider or instruction override is not allowed: " + field);
        }
        for (String field : List.of("hooks", "plugins")) {
            JsonElement value = config.get(field);
            if (value != null && !value.isJsonNull() && !(value.isJsonObject() && value.getAsJsonObject().isEmpty()))
                throw new IncompatibleException("Configured Codex hooks or plugins are not allowed");
        }
    }

    public static void validateLayers(JsonObject response) throws IncompatibleException {
        if (response == null || !response.has("layers") || !response.get("layers").isJsonArray())
            throw new IncompatibleException("Codex configuration layers are unavailable");
        JsonArray layers = response.getAsJsonArray("layers");
        for (JsonElement layer : layers) {
            if (!layer.isJsonObject() || !layer.getAsJsonObject().has("name"))
                throw new IncompatibleException("Codex configuration layer is malformed");
            JsonElement name = layer.getAsJsonObject().get("name");
            if (!name.isJsonObject() || !name.getAsJsonObject().has("type")
                    || !LAYER_TYPES.contains(name.getAsJsonObject().get("type").getAsString()))
                throw new IncompatibleException("Unknown Codex configuration layer");
            if (layer.getAsJsonObject().has("disabledReason")
                    && !layer.getAsJsonObject().get("disabledReason").isJsonNull())
                throw new IncompatibleException("Codex configuration layer is disabled or uninspectable");
            if (!layer.getAsJsonObject().has("config") || !layer.getAsJsonObject().get("config").isJsonObject())
                throw new IncompatibleException("Codex configuration layer contents are unavailable");
        }
    }

    private static JsonObject object(JsonObject parent, String key) throws IncompatibleException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new IncompatibleException("Codex policy is missing " + key);
        return value.getAsJsonObject();
    }
    private static void requireBoolean(JsonObject parent, String key, boolean expected) throws IncompatibleException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
                || value.getAsBoolean() != expected)
            throw new IncompatibleException("Codex policy does not disable " + key);
    }
    private static void requireString(JsonObject parent, String key, String expected) throws IncompatibleException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !expected.equals(value.getAsString()))
            throw new IncompatibleException("Codex policy has incompatible " + key);
    }
    private static void requireEmptyArray(JsonObject parent, String key) throws IncompatibleException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray() || !value.getAsJsonArray().isEmpty())
            throw new IncompatibleException("Codex policy has incompatible " + key);
    }
}

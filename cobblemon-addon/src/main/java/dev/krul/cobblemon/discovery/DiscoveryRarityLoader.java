package dev.krul.cobblemon.discovery;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import java.util.regex.Pattern;

public final class DiscoveryRarityLoader {
    private static final Pattern SPAWN_POOL = Pattern.compile("(?:.*/)?spawn_pool_world/.+\\.json");

    public DiscoveryRarityIndex load(ResourceManager resourceManager) {
        DiscoveryRarityIndex.Builder builder = DiscoveryRarityIndex.builder();
        if (resourceManager == null) return builder.build();

        try {
            Map<Identifier, Resource> resources = resourceManager.findResources("spawn_pool_world", id ->
                SPAWN_POOL.matcher(id.getPath()).matches());
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) parse(entry.getValue(), builder);
        } catch (RuntimeException ignored) {
            // Client resource managers may not expose data-pack resources.
        }

        loadBundledCobblemonData(builder);
        return builder.build();
    }

    private static void loadBundledCobblemonData(DiscoveryRarityIndex.Builder builder) {
        FabricLoader.getInstance().getModContainer("cobblemon").ifPresent(container -> {
            for (Path root : container.getRootPaths()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) continue;
                try (Stream<Path> paths = Files.walk(data)) {
                    paths.filter(DiscoveryRarityLoader::isSpawnPool)
                        .forEach(path -> parsePath(path, builder));
                } catch (Exception ignored) {
                    // A broken or unavailable mod root must not disable Discovery.
                }
            }
        });
    }

    private static boolean isSpawnPool(Path path) {
        String value = path.toString().replace('\\', '/');
        return value.contains("/spawn_pool_world/") && value.endsWith(".json");
    }

    private static void parsePath(Path path, DiscoveryRarityIndex.Builder builder) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parse(reader, builder);
        } catch (Exception ignored) {
            // One malformed spawn pool must not stop other pools from loading.
        }
    }

    private static void parse(Resource resource, DiscoveryRarityIndex.Builder builder) {
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            parse(reader, builder);
        } catch (Exception ignored) {
            // One malformed spawn pool must not stop other pools from loading.
        }
    }

    private static void parse(Reader reader, DiscoveryRarityIndex.Builder builder) {
        try {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject() || root.getAsJsonObject().has("enabled")
                && !root.getAsJsonObject().get("enabled").getAsBoolean()) return;
            JsonElement spawns = root.getAsJsonObject().get("spawns");
            if (spawns == null || !spawns.isJsonArray()) return;

            for (JsonElement element : spawns.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                var spawn = element.getAsJsonObject();
                if (!spawn.has("type") || !"pokemon".equalsIgnoreCase(spawn.get("type").getAsString())
                    || !spawn.has("pokemon") || !spawn.has("bucket")) continue;
                String species = spawn.get("pokemon").getAsString().trim().split("\\s+", 2)[0];
                builder.add(species, RarityClassifier.classify(spawn.get("bucket").getAsString()));
            }
        } catch (Exception ignored) {
            // One malformed spawn entry must not stop other entries from loading.
        }
    }
}


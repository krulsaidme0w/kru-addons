package dev.krul.builder.catalog;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned JSON codec for local registry metadata. Preview bytes are stored separately. */
public final class BlockCatalogSnapshotCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_BLOCKS = 250_000;
    private static final Gson GSON = new Gson();

    public record Snapshot(BlockCatalogFingerprint fingerprint, List<BlockCatalog.BlockEntry> blocks) {
        public Snapshot { Objects.requireNonNull(fingerprint); blocks = List.copyOf(blocks); if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Too many block entries"); }
    }

    public byte[] encode(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        JsonObject root = new JsonObject(); root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.add("fingerprint", fingerprint(snapshot.fingerprint()));
        JsonArray blocks = new JsonArray();
        for (var block : snapshot.blocks()) {
            JsonObject item = new JsonObject(); item.addProperty("id", block.id()); item.addProperty("namespace", block.namespace()); item.addProperty("label", block.label());
            JsonObject properties = new JsonObject();
            block.properties().forEach((name, values) -> { JsonArray list = new JsonArray(); values.forEach(list::add); properties.add(name, list); });
            item.add("properties", properties);
            JsonObject defaults = new JsonObject(); block.defaultState().forEach(defaults::addProperty);
            item.add("defaultState", defaults); item.addProperty("hasBlockEntity", block.hasBlockEntity()); blocks.add(item);
        }
        root.add("blocks", blocks);
        byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Block catalog snapshot exceeds size limit");
        return bytes;
    }

    public Snapshot decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid block catalog snapshot size");
        return decode(new String(bytes, StandardCharsets.UTF_8));
    }

    public Snapshot decode(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Block catalog snapshot exceeds size limit");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject(); fields(root, Set.of("schemaVersion", "fingerprint", "blocks"));
            if (!root.get("schemaVersion").getAsJsonPrimitive().isNumber() || root.get("schemaVersion").getAsInt() != SCHEMA_VERSION)
                throw new IllegalArgumentException("Unsupported catalog schema version");
            BlockCatalogFingerprint fingerprint = parseFingerprint(root.getAsJsonObject("fingerprint"));
            JsonArray source = root.getAsJsonArray("blocks");
            if (source == null || source.size() > MAX_BLOCKS) throw new IllegalArgumentException("Invalid block entry count");
            List<BlockCatalog.BlockEntry> entries = new ArrayList<>(source.size());
            Set<String> ids = new HashSet<>();
            for (JsonElement element : source) {
                JsonObject item = element.getAsJsonObject(); fields(item, Set.of("id", "namespace", "label", "properties", "defaultState", "hasBlockEntity"));
                JsonObject propertyObject = item.getAsJsonObject("properties"); var properties = new TreeMap<String, List<String>>();
                for (String key : propertyObject.keySet()) {
                    JsonArray values = propertyObject.getAsJsonArray(key);
                    if (key.length() > 128 || values.size() > 1024) throw new IllegalArgumentException("Invalid state property metadata");
                    List<String> strings = new ArrayList<>(); for (JsonElement value : values) strings.add(value.getAsString());
                    properties.put(key, strings);
                }
                JsonObject defaultsJson = item.getAsJsonObject("defaultState"); var defaults = new TreeMap<String, String>();
                for (String key : defaultsJson.keySet()) defaults.put(key, defaultsJson.get(key).getAsString());
                var entry = new BlockCatalog.BlockEntry(item.get("id").getAsString(), item.get("namespace").getAsString(), item.get("label").getAsString(),
                    properties, defaults, item.get("hasBlockEntity").getAsBoolean());
                if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate block ID");
                entries.add(entry);
            }
            return new Snapshot(fingerprint, entries);
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("Malformed block catalog snapshot", malformed); }
    }

    private static JsonObject fingerprint(BlockCatalogFingerprint value) {
        JsonObject result = new JsonObject(); result.addProperty("minecraftVersion", value.minecraftVersion()); result.addProperty("loaderVersion", value.loaderVersion()); result.addProperty("sha256", value.sha256());
        JsonObject mods = new JsonObject(); value.mods().forEach(mods::addProperty); result.add("mods", mods);
        JsonArray packs = new JsonArray(); value.resourcePacks().forEach(packs::add); result.add("resourcePacks", packs); return result;
    }
    private static BlockCatalogFingerprint parseFingerprint(JsonObject json) {
        fields(json, Set.of("minecraftVersion", "loaderVersion", "mods", "resourcePacks", "sha256"));
        JsonObject modsJson = json.getAsJsonObject("mods"); var mods = new TreeMap<String, String>();
        for (String key : modsJson.keySet()) mods.put(key, modsJson.get(key).getAsString());
        List<String> packs = new ArrayList<>(); for (JsonElement item : json.getAsJsonArray("resourcePacks")) packs.add(item.getAsString());
        BlockCatalogFingerprint computed = BlockCatalogFingerprint.create(json.get("minecraftVersion").getAsString(), json.get("loaderVersion").getAsString(), mods, packs);
        if (!computed.sha256().equals(json.get("sha256").getAsString())) throw new IllegalArgumentException("Catalog fingerprint checksum mismatch");
        return computed;
    }
    private static void fields(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unsupported catalog field: " + key);
    }
}

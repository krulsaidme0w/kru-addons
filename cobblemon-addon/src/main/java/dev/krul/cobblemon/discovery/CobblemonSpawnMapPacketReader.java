package dev.krul.cobblemon.discovery;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Reads CobbleNav's optional server spawn-map payload without linking the mod at compile time. */
public final class CobblemonSpawnMapPacketReader {
    private CobblemonSpawnMapPacketReader() {
    }

    public static SpawnMap read(Packet<?> packet) {
        if (!(packet instanceof CustomPayloadS2CPacket customPayload)) return SpawnMap.empty();
        return readPayload(customPayload.payload());
    }

    static SpawnMap readPayload(Object payload) {
        Object id = invoke(payload, "getId");
        if (id == null || !id.toString().equalsIgnoreCase("cobblenav:spawn_map")) return SpawnMap.empty();

        String bucket = string(invoke(payload, "getBucketName"));
        // CobbleNav 2.2.5 has no whole-pool probability. Keep the legacy accessor, explicitly unknown.
        float chance = Float.NaN;
        Object rawEntries = invoke(payload, "getSpawnDataList");
        if (!(rawEntries instanceof Collection<?> entries)) return new SpawnMap(bucket, chance, List.of());

        List<SpawnRarityEntry> result = new ArrayList<>();
        for (Object checkedEntry : entries) {
            Object renderable = invoke(checkedEntry, "getRenderable");
            if (renderable == null) renderable = invoke(checkedEntry, "getPokemon");
            String species = species(renderable);
            if (species.isBlank()) species = species(checkedEntry);
            String entryBucket = string(invoke(checkedEntry, "getBucket"));
            if (entryBucket.isBlank()) entryBucket = string(invoke(checkedEntry, "getBucketName"));
            SpawnRarity rarity = RarityClassifier.classify(entryBucket.isBlank() ? bucket : entryBucket);
            if (!species.isBlank() && rarity != SpawnRarity.UNKNOWN) {
                result.add(new SpawnRarityEntry(species, rarity));
            }
        }
        return new SpawnMap(bucket, chance, List.copyOf(result));
    }

    private static String species(Object result) {
        if (result == null) return "";
        Object pokemonSpecies = invoke(result, "getSpecies");
        Object identifier = invoke(pokemonSpecies, "getResourceIdentifier");
        return string(identifier);
    }

    private static Object invoke(Object receiver, String name) {
        if (receiver == null) return null;
        try {
            Method method = receiver.getClass().getMethod(name);
            return method.invoke(receiver);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** chance is NaN when unavailable; never infer a pool probability from received entries. */
    public record SpawnMap(String bucket, float chance, List<SpawnRarityEntry> entries) {
        public SpawnMap {
            bucket = bucket == null ? "" : bucket;
            entries = List.copyOf(entries);
        }
        public static SpawnMap empty() {
            return new SpawnMap("", Float.NaN, List.of());
        }

        public boolean found() {
            return !bucket.isBlank() || !entries.isEmpty();
        }
    }

    public record SpawnRarityEntry(String species, SpawnRarity spawnRarity) {
    }
}

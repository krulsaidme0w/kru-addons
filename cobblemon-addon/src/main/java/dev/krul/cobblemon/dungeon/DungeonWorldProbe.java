package dev.krul.cobblemon.dungeon;

import dev.krul.cobblemon.discovery.CobblemonObservationAdapter;
import dev.krul.cobblemon.discovery.PokemonObservation;
import meteordevelopment.meteorclient.mixin.ChunkAccessor;
import meteordevelopment.meteorclient.mixin.ClientChunkManagerAccessor;
import meteordevelopment.meteorclient.mixin.ClientChunkMapAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/** Read-only adapter over data already resident in the Minecraft client. */
public final class DungeonWorldProbe {
    private final CobblemonObservationAdapter pokemonAdapter = new CobblemonObservationAdapter();
    private final dev.krul.cobblemon.discovery.DiscoveryRarityIndex rarityIndex;
    private ScanCursor runtimeCursor = new ScanCursor();
    private String runtimeDimension = "";
    private final BlockEntityMetadataCache blockEntityMetadataCache = new BlockEntityMetadataCache(4096);

    public DungeonWorldProbe() {
        this(dev.krul.cobblemon.discovery.DiscoveryRarityIndex.empty());
    }

    public DungeonWorldProbe(dev.krul.cobblemon.discovery.DiscoveryRarityIndex rarityIndex) {
        this.rarityIndex = rarityIndex == null ? dev.krul.cobblemon.discovery.DiscoveryRarityIndex.empty() : rarityIndex;
    }

    public ProbeResult scan(MinecraftClient client, long now) {
        if (client != null && client.world != null) {
            String dimension = client.world.getRegistryKey().getValue().toString();
            if (!dimension.equals(runtimeDimension)) {
                runtimeDimension = dimension;
                runtimeCursor = new ScanCursor();
                blockEntityMetadataCache.clear();
            }
        }
        // Preserve completion in this result; the next scheduled same-dimension call starts a new pass.
        if (runtimeCursor.complete()) runtimeCursor = new ScanCursor();
        return scan(client, now, runtimeCursor, 128).result();
    }

    /** Resumable runtime pass over stable snapshots captured at pass initialization. */
    public BudgetedResult scan(MinecraftClient client, long now, ScanCursor cursor, int budget) {
        return scan(client, now, cursor, budget, true);
    }

    public BudgetedResult scan(MinecraftClient client, long now, ScanCursor cursor, int budget, boolean includeCrosshair) {
        if (client == null || client.world == null || client.player == null) {
            return new BudgetedResult(new ProbeResult("unavailable", 0, List.of(), true), cursor, true);
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        cursor.restartIfComplete();
        cursor.initializeBackingSlots(loadedChunkSlots(client), client.world.getEntities(),
            includeCrosshair ? crosshairTarget(client) : null);
        List<DungeonObservation> observations = new ArrayList<>();
        int remaining = Math.max(1, budget);
        while (remaining > 0) {
            ScanCursor.Work work = cursor.nextWork();
            if (work == null) break;
            remaining--;
            Object value = work.value();
            if (value instanceof Chunk chunk) {
                cursor.loadedChunkObjects.add(chunk);
                observations.add(chunkObservation(dimension, new ChunkData(chunk.getPos().x, chunk.getPos().z), now));
            } else if (value instanceof BlockEntity blockEntity) {
                String registryId = String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()));
                List<String> updateKeys = blockEntityMetadataCache.read(dimension, registryId, blockEntity.getPos(), blockEntity,
                    () -> readUpdateKeys(blockEntity));
                observations.add(blockEntityObservation(dimension, new BlockEntityData(registryId, blockEntity.getClass().getName(),
                    blockEntity.getPos(), readBlockEntityName(blockEntity), updateKeys), now));
            } else if (value instanceof Entity entity) {
                String registryId = String.valueOf(Registries.ENTITY_TYPE.getId(entity.getType()));
                observations.add(entityObservation(dimension, new EntityData(registryId, entity.getClass().getName(), entity.getId(),
                    entity.getUuidAsString(), entity.getX(), entity.getY(), entity.getZ(),
                    entity.hasCustomName() ? entity.getCustomName().getString() : entity.getName().getString(),
                    DungeonEntityFacts.facts(entity)), now));
                pokemonAdapter.read(entity, now).ifPresent(value2 -> {
                    PokemonData data = DungeonPokemonFacts.fromEntity(entity, value2, rarityIndex).orElseGet(() -> new PokemonData(value2.species(), "",
                        0, value2.shiny(), value2.radiant(), List.of(), entity.getId(), entity.getUuidAsString(), entity.getClass().getName(),
                        entity.getX(), entity.getY(), entity.getZ(), Map.of()));
                    observations.add(pokemonObservation(dimension, data, now));
                });
            } else if (value instanceof CrosshairData target) {
                CrosshairData resolved = resolveCrosshairBlock(target, cursor.loadedChunkObjects);
                if (resolved != null && (!resolved.registryId().isBlank() || resolved.kind() != DungeonObservation.Kind.BLOCK)) {
                    observations.add(crosshairObservation(dimension, resolved, now));
                }
            }
        }
        if (cursor.phase >= 5) cursor.phase = 6;
        boolean complete = cursor.phase >= 6;
        return new BudgetedResult(new ProbeResult(dimension, cursor.loadedChunkObjects.size(), observations, complete), cursor, complete);
    }

    private static AtomicReferenceArray<WorldChunk> loadedChunkSlots(MinecraftClient client) {
        ClientChunkManagerAccessor manager = (ClientChunkManagerAccessor) client.world.getChunkManager();
        ClientChunkMapAccessor map = (ClientChunkMapAccessor) (Object) manager.getChunks();
        return map.getChunks();
    }

    ProbeResult fromLoadedData(String dimension, int loadedChunks, Iterable<ChunkData> chunks,
                               Iterable<BlockEntityData> blockEntities, Iterable<EntityData> entities,
                               Iterable<PokemonData> pokemon, CrosshairData crosshair, long now) {
        Map<String, DungeonObservation> observations = new LinkedHashMap<>();
        for (ChunkData chunk : chunks) add(observations, chunkObservation(dimension, chunk, now));
        for (BlockEntityData blockEntity : blockEntities) add(observations, blockEntityObservation(dimension, blockEntity, now));
        for (EntityData entity : entities) add(observations, entityObservation(dimension, entity, now));
        for (PokemonData value : pokemon) add(observations, pokemonObservation(dimension, value, now));
        if (crosshair != null) add(observations, crosshairObservation(dimension, crosshair, now));
        return new ProbeResult(dimension, Math.max(0, loadedChunks), List.copyOf(observations.values()));
    }

    BudgetedResult fromLoadedData(String dimension, int loadedChunks, Iterable<ChunkData> chunks,
                                  Iterable<BlockEntityData> blockEntities, Iterable<EntityData> entities,
                                  Iterable<PokemonData> pokemon, CrosshairData crosshair, long now,
                                  ScanCursor cursor, int budget) {
        cursor.restartIfComplete();
        cursor.initialize(chunks, blockEntities, entities, pokemon, crosshair);
        List<DungeonObservation> observations = new ArrayList<>();
        int remaining = Math.max(1, budget);
        while (remaining > 0) {
            ScanCursor.Work work = cursor.nextWork();
            if (work == null) break;
            remaining--;
            Object value = work.value();
            if (value instanceof ChunkData chunk) observations.add(chunkObservation(dimension, chunk, now));
            else if (value instanceof BlockEntityData blockEntity) observations.add(blockEntityObservation(dimension, blockEntity, now));
            else if (value instanceof EntityData entity) observations.add(entityObservation(dimension, entity, now));
            else if (value instanceof PokemonData pokemonData) observations.add(pokemonObservation(dimension, pokemonData, now));
            else if (value instanceof CrosshairData target) observations.add(crosshairObservation(dimension, target, now));
        }
        if (cursor.phase >= 5) cursor.phase = 6;
        boolean complete = cursor.phase >= 6;
        return new BudgetedResult(new ProbeResult(dimension, Math.max(0, loadedChunks), observations, complete), cursor, complete);
    }

    private static void add(Map<String, DungeonObservation> observations, DungeonObservation observation) {
        observations.put(observation.identity(), observation);
    }

    private static String readBlockEntityName(BlockEntity blockEntity) {
        try {
            Method method = blockEntity.getClass().getMethod("getCustomName");
            Object value = method.invoke(blockEntity);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    /**
     * One consumed block-entity work unit may serialize this exact object once to read only
     * allowlisted key names. It never serializes a second object or retains NBT values.
     */
    private static List<String> readUpdateKeys(BlockEntity blockEntity) {
        try {
            Object world = blockEntity.getClass().getMethod("getWorld").invoke(blockEntity);
            Object lookup = world == null ? null : world.getClass().getMethod("getRegistryManager").invoke(world);
            for (Method method : blockEntity.getClass().getMethods()) {
                if (!method.getName().equals("toInitialChunkDataNbt") || method.getParameterCount() != 1) continue;
                Object nbt = method.invoke(blockEntity, lookup);
                Object keys = nbt.getClass().getMethod("getKeys").invoke(nbt);
                if (keys instanceof Iterable<?> iterable) return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .filter(value -> value != null).map(Object::toString).limit(32).toList();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional compatibility evidence. Missing runtime methods are not an error.
        }
        return List.of();
    }

    /** Session-bounded identity cache: a replacement object at the same position is serialized again. */
    static final class BlockEntityMetadataCache {
        private final int maximumEntries;
        private final IdentityHashMap<Object, Entry> entries = new IdentityHashMap<>();
        private final ArrayDeque<Object> insertionOrder = new ArrayDeque<>();

        BlockEntityMetadataCache(int maximumEntries) {
            if (maximumEntries < 1) throw new IllegalArgumentException("maximumEntries must be positive");
            this.maximumEntries = maximumEntries;
        }

        List<String> read(String dimension, String registryId, BlockPos position, Object liveObject,
                          Supplier<List<String>> serializer) {
            Entry entry = entries.get(liveObject);
            if (entry != null && entry.matches(dimension, registryId, position)) return entry.keys;
            List<String> serialized = serializer.get();
            List<String> keys = List.copyOf(serialized == null ? List.of() : serialized);
            entries.put(liveObject, new Entry(dimension, registryId, position, keys));
            insertionOrder.removeIf(candidate -> candidate == liveObject);
            insertionOrder.addLast(liveObject);
            while (entries.size() > maximumEntries) entries.remove(insertionOrder.removeFirst());
            return keys;
        }

        void clear() {
            entries.clear();
            insertionOrder.clear();
        }

        private record Entry(String dimension, String registryId, BlockPos position, List<String> keys) {
            boolean matches(String dimension, String registryId, BlockPos position) {
                return this.dimension.equals(dimension) && this.registryId.equals(registryId)
                    && java.util.Objects.equals(this.position, position);
            }
        }
    }

    public void clearSessionCache() {
        blockEntityMetadataCache.clear();
    }

    private static DungeonObservation chunkObservation(String dimension, ChunkData data, long now) {
        String identity = dimension + "|chunk|" + data.x() + "," + data.z();
        return new DungeonObservation(DungeonObservation.Kind.CHUNK, dimension, identity, "", "", "", null,
            Map.of("chunkX", Integer.toString(data.x()), "chunkZ", Integer.toString(data.z())), now);
    }

    private static DungeonObservation blockEntityObservation(String dimension, BlockEntityData data, long now) {
        String pos = position(data.position());
        String identity = dimension + "|block-entity|" + data.registryId() + "|" + pos;
        Map<String, String> facts = new LinkedHashMap<>();
        int keyCount = 0;
        for (String key : data.updateKeys()) {
            if (SAFE_NBT_KEYS.contains(key) && keyCount++ < 32) facts.put("nbtKey:" + key, "present");
        }
        facts.put("position", pos);
        return new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, dimension, identity, data.registryId(),
            data.className(), data.name(), data.position(), facts, now);
    }

    private static DungeonObservation entityObservation(String dimension, EntityData data, long now) {
        String identity = dimension + "|entity|" + data.uuid();
        return new DungeonObservation(DungeonObservation.Kind.ENTITY, dimension, identity, data.registryId(),
            data.className(), data.name(), flooredPosition(data.x(), data.y(), data.z()), data.facts(), now);
    }

    private static DungeonObservation pokemonObservation(String dimension, PokemonData data, long now) {
        String identity = dimension + "|pokemon|" + (data.uuid().isBlank()
            ? data.species() + "|" + data.x() + "," + data.y() + "," + data.z() : data.uuid());
        return new DungeonObservation(DungeonObservation.Kind.POKEMON, dimension, identity, bounded(data.species().contains(":") ? data.species() : "cobblemon:" + data.species()),
            bounded(data.className()), bounded(data.species()), flooredPosition(data.x(), data.y(), data.z()), DungeonPokemonFacts.facts(data), now);
    }

    private static BlockPos flooredPosition(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static DungeonObservation crosshairObservation(String dimension, CrosshairData data, long now) {
        String pos = position(data.position());
        String identity = dimension + "|crosshair|" + data.kind().name().toLowerCase() + "|" + data.registryId() + "|" + pos;
        return new DungeonObservation(DungeonObservation.Kind.CROSSHAIR, dimension, identity, data.registryId(),
            data.className(), data.name(), data.position(), Map.of("targetKind", data.kind().name().toLowerCase()), now);
    }

    private CrosshairData crosshairTarget(MinecraftClient client) {
        HitResult target = client.crosshairTarget;
        if (target == null || target.getType() == HitResult.Type.MISS) return null;
        if (target instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            return new CrosshairData(DungeonObservation.Kind.ENTITY,
                String.valueOf(Registries.ENTITY_TYPE.getId(entity.getType())), entity.getClass().getName(),
                entity.getBlockPos(), entity.getName().getString());
        }
        if (target instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            // The actual state lookup is deferred until the matching loaded chunk is consumed.
            return new CrosshairData(DungeonObservation.Kind.BLOCK, "", "", pos, "");
        }
        return null;
    }

    private static CrosshairData resolveCrosshairBlock(CrosshairData target, List<Chunk> chunks) {
        if (target == null || target.kind() != DungeonObservation.Kind.BLOCK) return target;
        BlockPos pos = target.position();
        for (Chunk chunk : chunks) {
            if (chunk.getPos().x == pos.getX() >> 4 && chunk.getPos().z == pos.getZ() >> 4) {
                var block = chunk.getBlockState(pos).getBlock();
                String blockId = String.valueOf(Registries.BLOCK.getId(block));
                return new CrosshairData(DungeonObservation.Kind.BLOCK, blockId, block.getClass().getName(), pos,
                    block.getName().getString());
            }
        }
        return null;
    }

    private static String position(BlockPos pos) {
        return pos == null ? "unknown" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public record ProbeResult(String dimension, int loadedChunks, List<DungeonObservation> observations, boolean complete) {
        public ProbeResult(String dimension, int loadedChunks, List<DungeonObservation> observations) {
            this(dimension, loadedChunks, observations, false);
        }

        public ProbeResult {
            observations = observations == null ? List.of() : List.copyOf(observations);
        }
    }

    public record ChunkData(int x, int z) {}
    public record BlockEntityData(String registryId, String className, BlockPos position, String name, List<String> updateKeys) {
        public BlockEntityData {
            registryId = bounded(registryId);
            className = bounded(className);
            name = bounded(name);
            updateKeys = updateKeys == null ? List.of() : updateKeys.stream().limit(32).map(DungeonWorldProbe::bounded).toList();
        }
    }
    public record EntityData(String registryId, String className, int entityId, String uuid, double x, double y, double z, String name, Map<String, String> facts) {
        public EntityData {
            registryId = bounded(registryId);
            className = bounded(className);
            uuid = bounded(uuid);
            name = bounded(name);
            facts = boundedFacts(facts);
        }
    }
    public record PokemonData(String registryId, String form, int level, Boolean shiny, Boolean radiant, List<String> aspects,
                              int entityId, String uuid, String className, double x, double y, double z,
                              Map<String, String> extraFacts) {
        public PokemonData(String registryId, String form, int level, Boolean shiny, Boolean radiant, List<String> aspects,
                           int entityId, double x, double y, double z) {
            this(registryId, form, level, shiny, radiant, aspects, entityId, "", "PokemonEntity", x, y, z, Map.of());
        }

        public PokemonData(String registryId, String form, int level, Boolean shiny, Boolean radiant, List<String> aspects,
                           double x, double y, double z) {
            this(registryId, form, level, shiny, radiant, aspects, -1, "", "PokemonEntity", x, y, z, Map.of());
        }

        public PokemonData {
            registryId = bounded(registryId);
            form = bounded(form);
            aspects = aspects == null ? List.of() : List.copyOf(aspects);
            aspects = aspects.stream().limit(16).map(DungeonWorldProbe::bounded).toList();
            extraFacts = boundedFacts(extraFacts);
            uuid = bounded(uuid);
            className = className == null || className.isBlank() ? "PokemonEntity" : bounded(className);
        }

        public String species() { return registryId.startsWith("cobblemon:") ? registryId.substring("cobblemon:".length()) : registryId; }
    }
    public record CrosshairData(DungeonObservation.Kind kind, String registryId, String className, BlockPos position, String name) {
        public CrosshairData {
            kind = kind == null ? DungeonObservation.Kind.BLOCK : kind;
            registryId = bounded(registryId);
            className = bounded(className);
            name = bounded(name);
        }
    }

    public static final class ScanCursor {
        private List<?>[] sources;
        private int[] sourceIndexes;
        private List<Chunk> backingChunks;
        private int backingSlotIndex;
        private CrosshairData crosshair;
        private int phase;
        private final List<Chunk> loadedChunkObjects = new ArrayList<>();
        private List<BlockEntity> currentBlockEntities = List.of();
        private int currentBlockEntityIndex;

        private void initialize(Iterable<ChunkData> chunks, Iterable<BlockEntityData> blockEntities,
                                Iterable<EntityData> entities, Iterable<PokemonData> pokemon, CrosshairData crosshair) {
            if (sources != null) return;
            sources = new List<?>[] { snapshot(chunks), snapshot(blockEntities), snapshot(entities), snapshot(pokemon) };
            sourceIndexes = new int[sources.length];
            this.crosshair = crosshair;
        }

        void initializeBackingSlots(AtomicReferenceArray<? extends Chunk> chunks, Iterable<?> entities, CrosshairData crosshair) {
            if (sources != null) return;
            backingChunks = new ArrayList<>(chunks.length());
            for (int i = 0; i < chunks.length(); i++) {
                Chunk chunk = chunks.get(i);
                backingChunks.add(chunk);
            }
            sources = new List<?>[] { List.of(), List.of(), snapshot(entities), List.of() };
            sourceIndexes = new int[sources.length];
            this.crosshair = crosshair;
        }

        private void restartIfComplete() {
            if (!complete()) return;
            sources = null;
            sourceIndexes = null;
            backingChunks = null;
            backingSlotIndex = 0;
            crosshair = null;
            phase = 0;
            loadedChunkObjects.clear();
            currentBlockEntities = List.of();
            currentBlockEntityIndex = 0;
        }

        boolean complete() {
            return phase >= 6;
        }

        Work nextWork() {
            while (phase < 4) {
                if (phase == 0 && currentBlockEntityIndex < currentBlockEntities.size()) {
                    return new Work(true, currentBlockEntities.get(currentBlockEntityIndex++));
                }
                if (phase == 0 && backingChunks != null) {
                    if (backingSlotIndex < backingChunks.size()) {
                        Chunk chunk = backingChunks.get(backingSlotIndex++);
                        currentBlockEntities = chunk == null ? List.of() : snapshot(((ChunkAccessor) chunk).getBlockEntities().values());
                        currentBlockEntityIndex = 0;
                        return new Work(true, chunk);
                    }
                    phase++;
                    continue;
                }
                if (sourceIndexes[phase] < sources[phase].size()) {
                    return new Work(true, sources[phase].get(sourceIndexes[phase]++));
                }
                if (phase == 0) {
                    phase++;
                    continue;
                }
                phase++;
            }
            if (phase == 4) {
                phase++;
                if (crosshair != null) return new Work(true, crosshair);
            }
            return null;
        }

        private static <T> List<T> snapshot(Iterable<? extends T> source) {
            List<T> snapshot = new ArrayList<>();
            for (T value : source) {
                snapshot.add(value);
            }
            return List.copyOf(snapshot);
        }

        record Work(boolean consumed, Object value) {}
    }

    public record BudgetedResult(ProbeResult result, ScanCursor cursor, boolean complete) {}

    private static final Set<String> SAFE_NBT_KEYS = Set.of("Items", "CustomName", "LootTable", "LootTableSeed",
        "id", "x", "y", "z", "Lock", "TransferCooldown", "SpawnPotentials", "SpawnData");

    static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    private static Map<String, String> boundedFacts(Map<String, String> facts) {
        if (facts == null || facts.isEmpty()) return Map.of();
        Map<String, String> bounded = new LinkedHashMap<>();
        facts.forEach((key, value) -> bounded.put(bounded(key), bounded(value)));
        return Map.copyOf(bounded);
    }
}

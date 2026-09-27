package dev.krul.cobblemon.observation;

import dev.krul.cobblemon.discovery.*;
import meteordevelopment.meteorclient.mixin.ClientChunkManagerAccessor;
import meteordevelopment.meteorclient.mixin.ClientChunkMapAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static dev.krul.cobblemon.observation.FactValue.*;

/** All live access stays on the client thread; never requests chunks or sends commands. */
public final class ClientObservationSource implements ObservationRuntime.Source {
    private final MinecraftClient mc;
    private final PokemonFactReader.RegistryCache registry = new PokemonFactReader.RegistryCache();
    private ObservationSnapshot.Scope scope;
    private Object resources;
    private DiscoveryRarityIndex reference = DiscoveryRarityIndex.empty();
    public ClientObservationSource(MinecraftClient mc) { this.mc = mc; }
    public void reset() { registry.clear(); scope = null; resources = null; reference = DiscoveryRarityIndex.empty(); }

    @Override public ObservationRuntime.Cursor open(ObservationSnapshot.Scope scope, double radius) {
        if (!scope.equals(this.scope) || resources != mc.getResourceManager()) {
            registry.clear(); this.scope = scope; resources = mc.getResourceManager();
            reference = new DiscoveryRarityLoader().load(mc.getResourceManager());
        }
        var manager = (ClientChunkManagerAccessor) mc.world.getChunkManager();
        var slots = ((ClientChunkMapAccessor) (Object) manager.getChunks()).getChunks();
        // No copying entity lists, slot arrays or block-entity maps at cursor initialization.
        return new LoadedObservationCursor<Entity, BlockEntity>(mc.world.getEntities(), slots.length(), slot -> {
            var chunk = slots.get(slot);
            if (chunk == null || mc.player == null) return Collections.emptyIterator();
            double dx = chunk.getPos().getCenterX() - mc.player.getX(), dz = chunk.getPos().getCenterZ() - mc.player.getZ();
            if (dx * dx + dz * dz > (radius + 16) * (radius + 16)) return Collections.emptyIterator();
            return chunk.getBlockEntities().values().iterator();
        }, (entity, now) -> entity.squaredDistanceTo(mc.player) <= radius * radius ? entity(entity, now) : null,
            (block, now) -> block(block, now));
    }

    private ObservationRuntime.Entry entity(Entity entity, long now) {
        Map<String, FactValue> facts = new LinkedHashMap<>();
        var pokemon = PokemonFactReader.read(entity, scope, now, registry);
        if (pokemon.isPresent()) facts.putAll(pokemon.get().facts());
        else {
            put(facts, "kind", entity instanceof ItemEntity ? "ITEM" : "ENTITY");
            put(facts, "uuid", entity.getUuidAsString());
            put(facts, "registry_id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            put(facts, "name", entity.getName().getString());
        }
        position(facts, entity.getX(), entity.getY(), entity.getZ());
        if (entity instanceof ItemEntity item) item(facts, item.getStack(), false);
        if (entity instanceof LivingEntity living) item(facts, living.getEquippedStack(EquipmentSlot.HEAD), true);
        if (pokemon.isPresent()) {
            FactValue species = facts.get("species");
            if (species != null && species.available()) {
                var resolution = DiscoveryRarityRuntime.evidence().resolve((String) species.value(), reference);
                facts.put("serverBucket", resolution.serverBuckets().isEmpty()
                    ? unavailable(Type.STRING, Source.SERVER_MAP, Availability.NOT_SENT)
                    : known(Type.STRING, Source.SERVER_MAP, resolution.rarity().name()));
                facts.put("referenceBucket", resolution.localBuckets().isEmpty()
                    ? unavailable(Type.STRING, Source.REGISTRY_REFERENCE, Availability.NOT_SENT)
                    : known(Type.STRING, Source.REGISTRY_REFERENCE, resolution.localBuckets().stream().sorted().map(Enum::name).reduce((a,b) -> a + "," + b).orElseThrow()));
            }
        }
        return new ObservationRuntime.Entry(new ObservationSnapshot(ObservationSnapshot.Identity.entity(scope, entity.getUuid()), now, facts), entity.getId());
    }
    private ObservationRuntime.Entry block(BlockEntity block, long now) {
        if (block.isRemoved()) return null;
        Map<String, FactValue> facts = new LinkedHashMap<>();
        put(facts, "kind", "BLOCK_ENTITY");
        put(facts, "registry_id", Registries.BLOCK_ENTITY_TYPE.getId(block.getType()).toString());
        if (block instanceof net.minecraft.util.Nameable named && named.hasCustomName()) put(facts, "name", named.getName().getString());
        BlockPos pos = block.getPos(); position(facts, pos.getX(), pos.getY(), pos.getZ());
        return new ObservationRuntime.Entry(new ObservationSnapshot(ObservationSnapshot.Identity.block(scope, pos.getX(), pos.getY(), pos.getZ()), now, facts), -1);
    }
    private void position(Map<String, FactValue> facts, double x, double y, double z) {
        put(facts, "x", x); put(facts, "y", y); put(facts, "z", z);
        put(facts, "dimension", mc.world.getRegistryKey().getValue().toString());
        put(facts, "distance", Math.sqrt(mc.player.squaredDistanceTo(x, y, z)));
    }
    private static void item(Map<String, FactValue> facts, ItemStack stack, boolean head) {
        if (stack.isEmpty()) return;
        put(facts, head ? "head_item_id" : "item_id", Registries.ITEM.getId(stack.getItem()).toString());
        var model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (model != null) put(facts, head ? "head_model_data" : "model_data", model.value());
    }
    private static void put(Map<String, FactValue> facts, String key, Object value) {
        Type type = FactSchema.field(key).orElseThrow().type();
        try { facts.put(key, known(type, Source.LIVE, value)); }
        catch (IllegalArgumentException invalid) { facts.put(key, unavailable(type, Source.LIVE, Availability.INVALID)); }
    }
    @Override public boolean loaded(ObservationRuntime.Entry entry) {
        if (mc.world == null || !entry.observation().identity().scope().equals(scope)) return false;
        if (entry.entityId() >= 0) {
            Entity entity = mc.world.getEntityById(entry.entityId());
            return entity != null && !entity.isRemoved() && entity.getUuidAsString().equals(entry.observation().identity().subjectId());
        }
        BlockPos pos = position(entry);
        var chunk = mc.world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().containsKey(pos);
    }
    private static BlockPos position(ObservationRuntime.Entry entry) {
        var facts = entry.observation().scalarFacts();
        return BlockPos.ofFloored((double) facts.get("x"), (double) facts.get("y"), (double) facts.get("z"));
    }
    @Override public ObservationRuntime.Entry inspect(ObservationRuntime.Entry entry, long now) {
        if (!loaded(entry)) return null;
        if (entry.entityId() >= 0) return entity(mc.world.getEntityById(entry.entityId()), now);
        var pos = position(entry);
        var chunk = mc.world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : block(chunk.getBlockEntities().get(pos), now);
    }
}

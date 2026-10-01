package dev.krul.builder.minecraft;

import dev.krul.builder.catalog.BlockCatalog;
import dev.krul.builder.catalog.BlockCatalogFingerprint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.util.*;

/** Reads only registry metadata on Minecraft's client thread. */
public final class ClientBlockCatalog {
    private final MinecraftClient client;
    private BlockCatalog cached;
    private BlockCatalogFingerprint cachedFingerprint;
    public ClientBlockCatalog(MinecraftClient client) { this.client = Objects.requireNonNull(client); }
    public BlockCatalog snapshot() {
        requireClientThread();
        BlockCatalogFingerprint fingerprint = fingerprint();
        if (cached == null || !fingerprint.sha256().equals(cachedFingerprint.sha256())) {
            cached = create(Registries.BLOCK, id -> FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getName()).orElse(id));
            cachedFingerprint = fingerprint;
        }
        return cached;
    }
    public BlockCatalogFingerprint fingerprint() {
        requireClientThread();
        var loader = FabricLoader.getInstance();
        var mods = new TreeMap<String, String>();
        loader.getAllMods().forEach(mod -> mods.put(mod.getMetadata().getId(), mod.getMetadata().getVersion().getFriendlyString()));
        String loaderVersion = loader.getModContainer("fabricloader").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        List<String> packs = client.getResourcePackManager().getEnabledIds().stream().sorted().toList();
        return BlockCatalogFingerprint.create(net.minecraft.SharedConstants.VERSION_NAME, loaderVersion, mods, packs);
    }
    public Optional<BlockCatalog.BlockDetails> describe(String exactId) {
        requireClientThread();
        if (exactId == null) return Optional.empty();
        Identifier id = Identifier.tryParse(exactId);
        if (id == null || !Registries.BLOCK.containsId(id)) return Optional.empty();
        return create(List.of(Registries.BLOCK.get(id)), namespace -> namespace).describe(exactId);
    }
    private void requireClientThread() { if (!client.isOnThread()) throw new IllegalStateException("Block catalog must run on the Minecraft client thread"); }
    static BlockCatalog create(Iterable<Block> blocks, java.util.function.Function<String,String> labels) {
        var entries = new ArrayList<BlockCatalog.BlockEntry>();
        for (Block block : blocks) {
            Identifier id = Registries.BLOCK.getId(block);
            if (id == null) continue;
            Map<String,List<String>> properties = new TreeMap<>();
            for (Property<?> property : block.getStateManager().getProperties()) properties.put(property.getName(), values(property));
            Map<String, String> defaultState = new TreeMap<>();
            var state = block.getDefaultState();
            for (Property<?> property : block.getStateManager().getProperties()) defaultState.put(property.getName(), value(state, property));
            entries.add(new BlockCatalog.BlockEntry(id.toString(), id.getNamespace(), block.getName().getString(), properties,
                defaultState, state.hasBlockEntity()));
        }
        return new BlockCatalog(entries, labels);
    }
    private static <T extends Comparable<T>> List<String> values(Property<T> property) { return property.getValues().stream().map(property::name).sorted().toList(); }
    private static <T extends Comparable<T>> String value(net.minecraft.block.BlockState state, Property<T> property) { return property.name(state.get(property)); }
}

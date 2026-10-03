package dev.krul.builder.minecraft;

import dev.krul.builder.catalog.ItemCatalog;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Function;

/** Reads registry-backed item names and metadata on Minecraft's client thread. */
public final class ClientItemCatalog {
    private final MinecraftClient client;
    private ItemCatalog cached;

    public ClientItemCatalog(MinecraftClient client) { this.client = Objects.requireNonNull(client); }

    public ItemCatalog snapshot() {
        requireClientThread();
        if (cached == null) cached = create(Registries.ITEM, namespace -> FabricLoader.getInstance().getModContainer(namespace)
            .map(container -> container.getMetadata().getName()).orElse(namespace));
        return cached;
    }

    public Optional<ItemCatalog.ItemEntry> describe(String exactId) {
        requireClientThread();
        if (exactId == null) return Optional.empty();
        Identifier id = Identifier.tryParse(exactId);
        if (id == null || !Registries.ITEM.containsId(id)) return Optional.empty();
        Item item = Registries.ITEM.get(id);
        return Optional.of(entry(id, item));
    }

    private void requireClientThread() {
        if (!client.isOnThread()) throw new IllegalStateException("Item catalog must run on the Minecraft client thread");
    }

    static ItemCatalog create(Iterable<Item> items, Function<String, String> namespaceLabels) {
        List<ItemCatalog.ItemEntry> entries = new ArrayList<>();
        for (Item item : items) {
            Identifier id = Registries.ITEM.getId(item);
            if (id != null) entries.add(entry(id, item));
        }
        return new ItemCatalog(entries, namespaceLabels);
    }

    private static ItemCatalog.ItemEntry entry(Identifier id, Item item) {
        ItemStack stack = new ItemStack(item);
        String label = stack.getName().getString();
        return new ItemCatalog.ItemEntry(id.toString(), id.getNamespace(), label, item.getClass().getName(),
            item.getMaxCount(), stack.getMaxDamage());
    }
}

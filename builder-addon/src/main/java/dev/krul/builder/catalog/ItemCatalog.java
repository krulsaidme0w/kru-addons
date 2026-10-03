package dev.krul.builder.catalog;

import java.util.*;
import java.util.function.Function;

/** Immutable, bounded index of the active client's item registry metadata. */
public final class ItemCatalog {
    public static final int PAGE_SIZE = 20, MAX_PAGE = 10_000, MAX_QUERY = 128;

    public record ItemEntry(String id, String namespace, String label, String itemClass, int maxStackCount, int maxDamage) {
        public ItemEntry(String id, String namespace, String label, String itemClass) {
            this(id, namespace, label, itemClass, 64, 0);
        }
        public ItemEntry {
            Objects.requireNonNull(id); Objects.requireNonNull(namespace); Objects.requireNonNull(label); Objects.requireNonNull(itemClass);
            if (!id.startsWith(namespace + ":") || id.length() > 256 || label.length() > 512 || itemClass.length() > 256
                || maxStackCount < 0 || maxDamage < 0)
                throw new IllegalArgumentException("Invalid item metadata");
        }
    }

    public record ModEntry(String namespace, String label, int itemCount) {}
    public record ModPage(List<ModEntry> mods, int total, Integer nextPage) {
        public ModPage { mods = List.copyOf(mods); }
    }
    public record ItemPage(List<ItemEntry> items, int total, Integer nextPage) {
        public ItemPage { items = List.copyOf(items); }
    }

    private final List<ItemEntry> entries;
    private final Map<String, ItemEntry> byId;
    private final List<ModEntry> mods;

    public ItemCatalog(List<ItemEntry> source, Function<String, String> namespaceLabels) {
        Objects.requireNonNull(source); Objects.requireNonNull(namespaceLabels);
        var sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparing(ItemEntry::namespace).thenComparing(ItemEntry::id));
        var index = new LinkedHashMap<String, ItemEntry>(); var counts = new TreeMap<String, Integer>();
        for (ItemEntry entry : sorted) {
            if (index.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate item ID");
            counts.merge(entry.namespace(), 1, Integer::sum);
        }
        entries = List.copyOf(sorted); byId = Collections.unmodifiableMap(index);
        mods = counts.entrySet().stream().map(entry -> new ModEntry(entry.getKey(),
            Objects.requireNonNullElse(namespaceLabels.apply(entry.getKey()), entry.getKey()), entry.getValue())).toList();
    }

    public ModPage listMods(int page) {
        checkPage(page); int start = offset(page, mods.size()); int end = Math.min(start + PAGE_SIZE, mods.size());
        return new ModPage(mods.subList(start, end), mods.size(), end < mods.size() ? page + 1 : null);
    }

    public ItemPage search(String query, Optional<String> namespace, int page) {
        Objects.requireNonNull(query); Objects.requireNonNull(namespace); checkPage(page);
        if (query.length() > MAX_QUERY) throw new IllegalArgumentException("Query exceeds 128 characters");
        if (query.isBlank() && namespace.isEmpty()) throw new IllegalArgumentException("Empty query requires a namespace");
        String needle = query.toLowerCase(Locale.ROOT); String selectedNamespace = namespace.orElse(null);
        List<ItemEntry> matches = entries.stream().filter(entry -> selectedNamespace == null || entry.namespace().equals(selectedNamespace))
            .filter(entry -> needle.isBlank() || entry.id().toLowerCase(Locale.ROOT).contains(needle)
                || entry.label().toLowerCase(Locale.ROOT).contains(needle)).toList();
        int start = offset(page, matches.size()); int end = Math.min(start + PAGE_SIZE, matches.size());
        return new ItemPage(matches.subList(start, end), matches.size(), end < matches.size() ? page + 1 : null);
    }

    public Optional<ItemEntry> describe(String exactId) {
        return exactId == null ? Optional.empty() : Optional.ofNullable(byId.get(exactId));
    }

    private static int offset(int page, int size) {
        long value = (long) page * PAGE_SIZE;
        return value >= size ? size : (int) value;
    }
    private static void checkPage(int page) {
        if (page < 0 || page > MAX_PAGE) throw new IllegalArgumentException("Page is outside the supported range");
    }
}

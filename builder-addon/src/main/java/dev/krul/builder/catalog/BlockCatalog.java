package dev.krul.builder.catalog;

import java.util.*;

/** Immutable, bounded index of the current client's block registry metadata. */
public final class BlockCatalog {
    public static final int PAGE_SIZE = 20, MAX_PAGE = 10_000, MAX_QUERY = 128;
    public record BlockEntry(String id, String namespace, String label, Map<String, List<String>> properties,
                             Map<String, String> defaultState, boolean hasBlockEntity) {
        public BlockEntry(String id, String namespace, String label, Map<String, List<String>> properties) {
            this(id, namespace, label, properties, Map.of(), false);
        }
        public BlockEntry {
            Objects.requireNonNull(id); Objects.requireNonNull(namespace); Objects.requireNonNull(label);
            if (!id.startsWith(namespace + ":") || id.length() > 256) throw new IllegalArgumentException("Invalid registry ID");
            var copy = new TreeMap<String,List<String>>();
            Objects.requireNonNull(properties).forEach((key, values) -> copy.put(Objects.requireNonNull(key), List.copyOf(values)));
            properties = Collections.unmodifiableMap(copy);
            defaultState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(defaultState)));
            if (!properties.keySet().containsAll(defaultState.keySet())) throw new IllegalArgumentException("Default state has unknown properties");
            for (var entry : defaultState.entrySet()) if (!properties.get(entry.getKey()).contains(entry.getValue()))
                throw new IllegalArgumentException("Default state contains an illegal property value");
        }
    }
    public record ModEntry(String namespace, String label, int blockCount) { }
    public record ModPage(List<ModEntry> mods, int total, Integer nextPage) { public ModPage { mods = List.copyOf(mods); } }
    public record BlockPage(List<BlockEntry> blocks, int total, Integer nextPage) { public BlockPage { blocks = List.copyOf(blocks); } }
    public record BlockDetails(String id, String label, Map<String,List<String>> properties, Map<String,String> defaultState, boolean hasBlockEntity) {
        public BlockDetails(String id, String label, Map<String,List<String>> properties) { this(id, label, properties, Map.of(), false); }
        public BlockDetails {
            var copy = new TreeMap<String,List<String>>(); properties.forEach((k,v)->copy.put(k,List.copyOf(v))); properties=Collections.unmodifiableMap(copy);
            defaultState = Collections.unmodifiableMap(new TreeMap<>(defaultState));
        }
    }
    private final List<BlockEntry> entries;
    private final Map<String, BlockEntry> byId;
    private final List<ModEntry> mods;

    public BlockCatalog(List<BlockEntry> source) {
        this(source, namespace -> namespace);
    }
    public BlockCatalog(List<BlockEntry> source, java.util.function.Function<String,String> namespaceLabels) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(namespaceLabels);
        var sorted = new ArrayList<>(source); sorted.sort(Comparator.comparing(BlockEntry::namespace).thenComparing(BlockEntry::id));
        var index = new LinkedHashMap<String,BlockEntry>(); var counts = new TreeMap<String,Integer>();
        for (BlockEntry entry : sorted) {
            if (index.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate block ID");
            counts.merge(entry.namespace(), 1, Integer::sum);
        }
        entries = List.copyOf(sorted); byId = Collections.unmodifiableMap(index);
        mods = counts.entrySet().stream().map(e -> new ModEntry(e.getKey(), Objects.requireNonNullElse(namespaceLabels.apply(e.getKey()), e.getKey()), e.getValue())).toList();
    }
    public ModPage listMods(int page) {
        checkPage(page); int start = offset(page, mods.size()); int end = Math.min(start + PAGE_SIZE, mods.size());
        return new ModPage(mods.subList(start,end), mods.size(), end < mods.size() ? page + 1 : null);
    }
    public BlockPage search(String query, Optional<String> namespace, int page) {
        Objects.requireNonNull(query); Objects.requireNonNull(namespace); checkPage(page);
        if (query.length() > MAX_QUERY) throw new IllegalArgumentException("Query exceeds 128 characters");
        if (query.isBlank() && namespace.isEmpty()) throw new IllegalArgumentException("Empty query requires a namespace");
        String needle = query.toLowerCase(Locale.ROOT); String ns = namespace.orElse(null);
        List<BlockEntry> matches = entries.stream().filter(e -> ns == null || e.namespace().equals(ns))
            .filter(e -> needle.isBlank() || e.id().toLowerCase(Locale.ROOT).contains(needle) || e.label().toLowerCase(Locale.ROOT).contains(needle)).toList();
        int start = offset(page, matches.size()); int end = Math.min(start + PAGE_SIZE, matches.size());
        return new BlockPage(matches.subList(start,end), matches.size(), end < matches.size() ? page + 1 : null);
    }
    public Optional<BlockDetails> describe(String exactId) {
        if (exactId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(exactId)).map(e -> new BlockDetails(e.id(), e.label(), e.properties(), e.defaultState(), e.hasBlockEntity()));
    }
    public List<BlockEntry> entries() { return entries; }
    private static int offset(int page,int size) { long value=(long)page*PAGE_SIZE; return value >= size ? size : (int)value; }
    private static void checkPage(int page) { if (page < 0 || page > MAX_PAGE) throw new IllegalArgumentException("Page is outside the supported range"); }
}

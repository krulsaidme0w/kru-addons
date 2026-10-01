package dev.krul.builder.catalog;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCatalogTest {
    @Test void pagesModsInStablePagesOfTwenty() {
        List<BlockCatalog.BlockEntry> entries = new ArrayList<>();
        for (int i=0;i<41;i++) entries.add(entry("mod"+i, "block", "Block", Map.of()));
        BlockCatalog catalog = new BlockCatalog(entries);
        var first = catalog.listMods(0); var next = catalog.listMods(first.nextPage());
        var last = catalog.listMods(next.nextPage());
        assertEquals(20, first.mods().size()); assertEquals(20, next.mods().size()); assertEquals(1,last.mods().size());
        assertEquals(41, first.total()); assertNull(last.nextPage());
    }
    @Test void searchesStableIdsAndLocalizedLabelsWithinNamespace() {
        BlockCatalog catalog = new BlockCatalog(List.of(entry("a", "blue", "Stone", Map.of()), entry("b", "blue", "Stone", Map.of()), entry("b", "red", "Stone", Map.of())));
        var byName = catalog.search("stone", Optional.empty(), 0);
        assertEquals(List.of("a:blue", "b:blue", "b:red"), byName.blocks().stream().map(BlockCatalog.BlockEntry::id).toList());
        assertEquals(List.of("b:blue", "b:red"), catalog.search("", Optional.of("b"), 0).blocks().stream().map(BlockCatalog.BlockEntry::id).toList());
        assertTrue(catalog.search("", Optional.of("B"), 0).blocks().isEmpty());
    }
    @Test void exactDescribeAndBoundedInputs() {
        BlockCatalog catalog = new BlockCatalog(List.of(entry("minecraft", "stone", "Stone", Map.of("axis", List.of("x","y","z")))));
        assertEquals(List.of("x","y","z"), catalog.describe("minecraft:stone").orElseThrow().properties().get("axis"));
        assertTrue(catalog.describe("minecraft:ston").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> catalog.search("", Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> catalog.search("x".repeat(129), Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> catalog.listMods(-1));
        assertThrows(IllegalArgumentException.class, () -> catalog.listMods(10_001));
    }
    @Test void exactDescriptionIncludesVerifiedDefaultStateAndBlockEntityCapability() {
        var entry = new BlockCatalog.BlockEntry("demo:chest", "demo", "Chest", Map.of("facing", List.of("north", "south")), Map.of("facing", "north"), true);
        var detail = new BlockCatalog(List.of(entry)).describe("demo:chest").orElseThrow();
        assertEquals(Map.of("facing", "north"), detail.defaultState());
        assertTrue(detail.hasBlockEntity());
        assertThrows(IllegalArgumentException.class, () -> new BlockCatalog.BlockEntry("demo:x", "demo", "x",
            Map.of("axis", List.of("x", "y")), Map.of("axis", "z"), false));
    }
    private static BlockCatalog.BlockEntry entry(String namespace,String path,String name,Map<String,List<String>> properties) { return new BlockCatalog.BlockEntry(namespace+":"+path,namespace,name,properties); }
}

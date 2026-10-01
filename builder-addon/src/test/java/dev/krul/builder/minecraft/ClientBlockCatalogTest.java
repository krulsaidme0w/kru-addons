package dev.krul.builder.minecraft;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientBlockCatalogTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    @Test void registryIdsAndAllValidPropertyValuesAreExposed() {
        var catalog = ClientBlockCatalog.create(Registries.BLOCK, id -> "mod:" + id);
        var stairs = catalog.describe("minecraft:oak_stairs").orElseThrow();
        assertEquals("minecraft:oak_stairs", stairs.id());
        assertEquals(4, stairs.properties().get("facing").size());
        assertTrue(stairs.properties().get("shape").contains("inner_left"));
        assertTrue(catalog.search("oak_stairs", java.util.Optional.empty(), 0).blocks().stream().anyMatch(b -> b.id().equals("minecraft:oak_stairs")));
    }
    @Test void exactUnknownIdDoesNotFuzzyResolve() {
        var catalog = ClientBlockCatalog.create(java.util.List.of(Blocks.STONE), id -> id);
        assertTrue(catalog.describe("minecraft:ston").isEmpty());
    }
}

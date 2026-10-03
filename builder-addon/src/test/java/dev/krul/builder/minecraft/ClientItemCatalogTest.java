package dev.krul.builder.minecraft;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientItemCatalogTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test void registryCatalogReportsExactIdLocalizedNameAndUsefulStackMetadata() {
        var catalog = ClientItemCatalog.create(List.of(Items.STICK, Items.DIAMOND_SWORD), id -> id);
        var stick = catalog.describe("minecraft:stick").orElseThrow();
        var sword = catalog.describe("minecraft:diamond_sword").orElseThrow();
        assertEquals(Registries.ITEM.getId(Items.STICK).toString(), stick.id());
        assertFalse(stick.label().isBlank());
        assertEquals(64, stick.maxStackCount());
        assertTrue(sword.maxDamage() > 0);
        assertTrue(catalog.describe("minecraft:stickish").isEmpty());
    }
}

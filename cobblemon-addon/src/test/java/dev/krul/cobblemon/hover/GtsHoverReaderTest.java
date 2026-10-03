package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GtsHoverReaderTest {
    @Test void readsDetailsFromOnePokemonListingAndIgnoresNonListings() {
        var listing = GtsHoverReader.fromListing("cobblemon:pokemon_model", "Mimikyu Lv. 100", "cobblemon:mimikyu", List.of("shiny", "female"),
            List.of("Mimikyu ♀ Lv. 100", "Ghost/Fairy", "IVs: 186/186 (100%)", "HP 31", "Atk 31", "Def 31",
                "SpA 31", "SpD 31", "Spe 31", "Ability: Disguise", "Nature: Jolly", "Held Item: Life Orb",
                "Moves:", "Play Rough - Shadow Claw")).orElseThrow();
        var details = listing;
        assertEquals("Mimikyu", details.species());
        assertEquals("Disguise", details.ability());
        assertEquals("female", details.gender());
        assertTrue(details.shiny());
        assertEquals(6, details.ivs().size());
        assertEquals(List.of("Play Rough", "Shadow Claw"), details.moves());
        assertEquals(IvTintTier.IV_95, details.ivTier());

        assertTrue(GtsHoverReader.fromListing("minecraft:paper", "Next page", "", List.of(), List.of()).isEmpty());
        assertTrue(GtsHoverReader.isGtsTitle("Global Trade System"));
        assertFalse(GtsHoverReader.isGtsTitle("Ender Chest"));
    }

    @Test void incompleteOrOutOfRangeIvsNeverReceiveTint() {
        var partial = GtsHoverReader.fromListing("cobblemon:pokemon_model", "Mimikyu Lv. 1", "cobblemon:mimikyu", List.of(),
            List.of("Mimikyu Lv. 1", "Ghost/Fairy", "IVs: 62/186 (33%)", "HP 31", "Atk 31")).orElseThrow();
        assertTrue(partial.ivs().isEmpty());
        assertEquals(IvTintTier.NONE, partial.ivTier());
    }

    @Test void acceptsTheStatGlyphsUsedByAcademyTooltips() {
        var details = GtsHoverReader.fromListing("cobblemon:pokemon_model", "Mimikyu", "cobblemon:mimikyu", List.of(),
            List.of("Mimikyu Lv. 1", "Ghost/Fairy", "IVs: 186/186 (100%)", "โ 31", "เ 31", "แ 31", "ใ 31", "ไ 31", "ๅ 31"))
            .orElseThrow();
        assertEquals(List.of(31, 31, 31, 31, 31, 31), details.ivs());
    }
}

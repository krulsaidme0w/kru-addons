package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GtsHoverReaderTest {
    @Test void readsDeltaClientListingPayloadWithoutItemTooltip() {
        var details = GtsHoverReader.fromDeltaPokemon(new DeltaPokemon());
        assertNotNull(details);
        assertEquals("Mimikyu", details.displayName());
        assertEquals(IvTintTier.IV_90, details.ivTier());
        assertTrue(details.shiny());
        assertEquals(List.of("Ghost", "Fairy"), details.types());
    }

    public static final class DeltaPokemon {
        public String getSpeciesId() { return "cobblemon:mimikyu"; }
        public String getName() { return "Mimikyu"; }
        public List<String> getAspects() { return List.of("shiny", "female"); }
        public String getPrimaryType() { return "Ghost"; }
        public String getSecondaryType() { return "Fairy"; }
        public List<Integer> getIvs() { return List.of(31, 25, 30, 28, 26, 31); }
        public List<String> getMoves() { return List.of("Play Rough", "Shadow Sneak"); }
        public String getTeraType() { return "Ghost"; }
        public String getAbility() { return "Disguise"; }
        public String getNature() { return "Jolly"; }
        public String getHeldItem() { return "None"; }
        public String getCaughtBall() { return "Ultra Ball"; }
        public String getSkin() { return ""; }
        public double getSize() { return 1.0; }
        public int getLevel() { return 50; }
        public String getGender() { return "Female"; }
    }
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

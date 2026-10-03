package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PokemonHoverDetailsTest {
    @Test void onlyCompleteValidIvSetsReceiveAQualityTint() {
        var details = new PokemonHoverDetails("Mimikyu", "Mimikyu", "", 1, "", false,
            List.of("Ghost", "Fairy"), "", "", "", "", "", "", "",
            List.of(31, 31, 31, 31, 31, 31), List.of(), List.of());
        assertEquals(IvTintTier.IV_95, details.ivTier());
        assertEquals(IvTintTier.NONE, details.withIvs(List.of(31, 31, 31, 31, 31)).ivTier());
        assertEquals(IvTintTier.NONE, details.withIvs(List.of(31, 31, 31, 31, 31, 32)).ivTier());
    }
}

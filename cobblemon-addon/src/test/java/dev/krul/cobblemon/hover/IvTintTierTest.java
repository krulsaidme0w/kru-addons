package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IvTintTierTest {
    @Test void usesHighestReachedIvPercentageThreshold() {
        assertEquals(IvTintTier.NONE, IvTintTier.forPercentage(59));
        assertEquals(IvTintTier.IV_60, IvTintTier.forPercentage(60));
        assertEquals(IvTintTier.IV_80, IvTintTier.forPercentage(80));
        assertEquals(IvTintTier.IV_90, IvTintTier.forPercentage(90));
        assertEquals(IvTintTier.IV_95, IvTintTier.forPercentage(95));
    }
}

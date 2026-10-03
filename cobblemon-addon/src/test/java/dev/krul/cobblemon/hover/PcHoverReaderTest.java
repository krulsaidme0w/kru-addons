package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PcHoverReaderTest {
    @Test void resolvesOnlyTheSlotContainingThePointer() {
        Object pokemon = new Object();
        var slot = new Slot(pokemon, 12, 24, 32, 32);
        var hit = PcHoverReader.findHovered(List.of(slot), 31, 44).orElseThrow();
        assertSame(pokemon, hit.pokemon());
        assertEquals(new HoverCardLayout.Rect(12, 24, 32, 32), hit.bounds());
        assertTrue(PcHoverReader.findHovered(List.of(slot), 44, 44).isEmpty());
    }
    public static final class Slot {
        private final Object pokemon; private final int x, y, width, height;
        public Slot(Object pokemon, int x, int y, int width, int height) { this.pokemon = pokemon; this.x = x; this.y = y; this.width = width; this.height = height; }
        public Object getPokemon() { return pokemon; } public int getX() { return x; } public int getY() { return y; }
        public int getWidth() { return width; } public int getHeight() { return height; }
    }
}

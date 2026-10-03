package dev.krul.cobblemon.hover;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HoverCardLayoutTest {
    @Test void placesCardBesideSlotAndClampsItToViewport() {
        var slot = new HoverCardLayout.Rect(180, 30, 20, 20);
        var placement = HoverCardLayout.place(195, 35, 180, 100, 400, 250, slot);
        assertEquals(207, placement.x());
        assertEquals(47, placement.y());

        var edgeSlot = new HoverCardLayout.Rect(390, 230, 10, 10);
        var edge = HoverCardLayout.place(395, 235, 300, 180, 400, 250, edgeSlot);
        assertEquals(83, edge.x());
        assertEquals(70, edge.y());
    }
}

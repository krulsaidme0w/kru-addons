package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CobblemonPcAdapterTest {
    @Test void lookalikeScreenCannotTriggerProtocolAction() {
        var lookalike = new LookalikeScreen();
        var adapter = new CobblemonPcAdapter();
        assertNull(adapter.inspect(lookalike));
        assertFalse(adapter.closeLocal(lookalike));
        assertFalse(lookalike.closed);
    }

    @Test void absenceOfScreenDisablesPcActions() {
        var adapter = new CobblemonPcAdapter();
        assertNull(adapter.inspect(null));
        assertFalse(adapter.closeLocal(null));
    }

    public static class LookalikeScreen {
        boolean closed;
        public Object getPc() { return this; }
        public Object getParty() { return this; }
        public void closeNormally(boolean unlink) { closed = true; }
    }
}

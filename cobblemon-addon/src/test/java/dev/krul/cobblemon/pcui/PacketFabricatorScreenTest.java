package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketFabricatorScreenTest {
    @Test void preservesUserEditedSyncAndRevisionIncludingSavedHandlerValues() {
        var f = PacketFabricatorScreen.Fields.slot("37", "42", "-999", "1", "PICKUP", "3", 10);
        assertEquals(37, f.syncId()); assertEquals(42, f.revision());
        assertEquals(-999, f.slot()); assertEquals(1, f.button()); assertEquals(3, f.repeat());
    }
    @Test void rejectsMalformedNumbersOverflowAndUnboundedRepetition() {
        for (String value : new String[]{"", "abc", "2147483648", "-1", "128"})
            assertThrows(IllegalArgumentException.class,
                () -> PacketFabricatorScreen.Fields.slot(value, "0", "0", "0", "PICKUP", "1", 10));
        for (String repeat : new String[]{"0", "-1", "101"})
            assertThrows(IllegalArgumentException.class,
                () -> PacketFabricatorScreen.Fields.button("1", "0", repeat));
    }
    @Test void validatesActionButtonAndSlotAgainstRetainedHandlerNotLiveHandler() {
        assertThrows(IllegalArgumentException.class,
            () -> PacketFabricatorScreen.Fields.slot("1", "0", "10", "0", "PICKUP", "1", 10));
        assertThrows(IllegalArgumentException.class,
            () -> PacketFabricatorScreen.Fields.slot("1", "0", "-2", "0", "PICKUP", "1", 10));
        assertThrows(IllegalArgumentException.class,
            () -> PacketFabricatorScreen.Fields.slot("1", "0", "0", "2", "PICKUP", "1", 10));
        assertThrows(IllegalArgumentException.class,
            () -> PacketFabricatorScreen.Fields.slot("1", "0", "0", "0", "UNKNOWN", "1", 10));
        assertEquals(40, PacketFabricatorScreen.Fields.slot("1", "0", "0", "40", "SWAP", "1", 10).button());
        assertEquals(8, PacketFabricatorScreen.Fields.slot("1", "0", "-999", "8", "QUICK_CRAFT", "1", 10).button());
    }
    @Test void buttonPacketIgnoresSlotOnlyFieldsAndUsesVarIntButtonId() {
        var f = PacketFabricatorScreen.Fields.button("300", "1000", "100");
        assertEquals(300, f.syncId()); assertEquals(1000, f.button()); assertEquals(100, f.repeat());
    }
}

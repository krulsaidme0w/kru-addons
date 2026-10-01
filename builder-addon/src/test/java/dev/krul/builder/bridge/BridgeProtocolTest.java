package dev.krul.builder.bridge;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeProtocolTest {
    @Test void roundTripsSupportedVersionedRequest() throws Exception {
        var request = new BridgeProtocol.Request(BridgeProtocol.VERSION, "r1", BridgeProtocol.Method.STATUS,
            null, 0, 0, 0, 0, null);
        assertEquals(request, BridgeProtocol.decodeRequest(BridgeProtocol.encodeRequest(request)));
    }

    @Test void rejectsUnsupportedProtocolVersion() {
        String json = "{\"version\":999,\"id\":\"r1\",\"method\":\"STATUS\",\"bounds\":null,\"page\":0,\"pageSize\":0,\"token\":null}";
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decodeRequest(json));
    }

    @Test void rejectsMalformedAndOversizedJson() {
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decodeRequest("{"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decodeRequest(" ".repeat(BridgeProtocol.MAX_JSON_BYTES + 1)));
    }

    @Test void responseAlwaysCarriesWorldGenerationAndSnapshotRevision() {
        var response = new BridgeProtocol.Response(BridgeProtocol.VERSION, "r1", true, 3, 8, "ok", null, null);
        var decoded = BridgeProtocol.decodeResponse(BridgeProtocol.encodeResponse(response));
        assertEquals(3, decoded.worldGeneration());
        assertEquals(8, decoded.snapshotRevision());
    }
}

package dev.krul.builder.minecraft;

import dev.krul.builder.bridge.BridgeProtocol;
import dev.krul.builder.catalog.ItemCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemDescriptionResponseTest {
    @Test void missingItemModelKeepsExactMetadataAndOmitsImage() {
        var request = new BridgeProtocol.Request(1, "item-1", BridgeProtocol.Method.DESCRIBE_ITEM,
            null, 0, 0, 1, 0, null, null, null, "example:decorative_block", null, null);
        var entry = new ItemCatalog.ItemEntry("example:decorative_block", "example", "Decorative Block", "example.DecorativeItem", 64, 0);
        var response = BuilderBridgeDataSource.itemDescriptionResponse(request, 1, 0, entry, Optional.empty(), "missing-model");
        assertTrue(response.ok());
        assertNull(response.imageBase64());
        var payload = com.google.gson.JsonParser.parseString(response.payload()).getAsJsonObject();
        assertEquals("example:decorative_block", payload.get("id").getAsString());
        assertEquals("Decorative Block", payload.get("name").getAsString());
        assertEquals("missing-model", payload.get("previewState").getAsString());
    }
}

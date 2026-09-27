package dev.krul.builder.model;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StateKeyTest {
    @Test
    void equalityIncludesEveryPropertyAndTheRegistryId() {
        assertNotEquals(new StateKey("minecraft:oak_stairs", Map.of("facing", "east")),
            new StateKey("minecraft:oak_stairs", Map.of("facing", "west")));
        assertNotEquals(new StateKey("minecraft:oak_slab", Map.of("type", "top")),
            new StateKey("minecraft:oak_slab", Map.of("type", "bottom")));
        assertNotEquals(new StateKey("minecraft:water", Map.of("level", "0")),
            new StateKey("minecraft:water", Map.of("level", "1")));
        assertNotEquals(new StateKey("minecraft:stone", Map.of()), new StateKey("minecraft:air", Map.of()));
        assertNotEquals(new StateKey("minecraft:oak_stairs", Map.of()),
            new StateKey("minecraft:oak_stairs", Map.of("facing", "east")));
    }

    @Test
    void propertyInsertionOrderDoesNotChangeStateIdentity() {
        var first = new java.util.LinkedHashMap<String, String>();
        first.put("facing", "east");
        first.put("waterlogged", "false");
        var second = new java.util.LinkedHashMap<String, String>();
        second.put("waterlogged", "false");
        second.put("facing", "east");
        var state = new StateKey("minecraft:oak_stairs", first);
        var reordered = new StateKey("minecraft:oak_stairs", second);
        assertEquals(state, reordered);
        assertEquals(state.hashCode(), reordered.hashCode());
    }

    @Test
    void propertiesAreDefensivelyCopiedAndUnmodifiable() {
        var properties = new HashMap<>(Map.of("facing", "east"));
        var state = new StateKey("minecraft:oak_stairs", properties);
        properties.put("facing", "west");
        assertEquals(Map.of("facing", "east"), state.properties());
        assertThrows(UnsupportedOperationException.class, () -> state.properties().put("facing", "south"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone", "", ":stone", "minecraft:", "Minecraft:stone",
        "minecraft:Stone", "minecraft:oak stairs", "minecraft:stone:extra", "minecraft:stone\n",
        " minecraft:stone", "mine/craft:stone", "minecraft:stöne"})
    void rejectsUnqualifiedOrMalformedRegistryIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> new StateKey(id, Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:air", "example_mod-1.2:building/polished_stone-2"})
    void acceptsQualifiedRegistrySyntaxWithoutClaimingRegistryExistence(String id) {
        assertEquals(id, new StateKey(id, Map.of()).id());
    }

    @Test
    void rejectsNullIdPropertiesKeysAndValues() {
        assertThrows(NullPointerException.class, () -> new StateKey(null, Map.of()));
        assertThrows(NullPointerException.class, () -> new StateKey("minecraft:air", null));
        var nullKey = new HashMap<String, String>();
        nullKey.put(null, "east");
        assertThrows(NullPointerException.class, () -> new StateKey("minecraft:oak_stairs", nullKey));
        var nullValue = new HashMap<String, String>();
        nullValue.put("facing", null);
        assertThrows(NullPointerException.class, () -> new StateKey("minecraft:oak_stairs", nullValue));
    }
}

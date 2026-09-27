package dev.krul.cobblemon.dungeon;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DungeonEntityFactsTest {
    @Test
    void customModelEvidenceSurvivesComponentCountAndTextBounds() {
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < 20; i++) ids.add("academy:" + "a".repeat(80) + i);
        ids.add("minecraft:custom_model_data");

        String components = DungeonEntityFacts.componentSummary(731, ids);

        assertTrue(components.startsWith("minecraft:custom_model_data=731"));
        assertTrue(components.length() <= 256);
        assertEquals("none", DungeonEntityFacts.componentSummary(null, java.util.List.of()));
    }

    @Test
    void preservesOnFireForEveryEntity() {
        assertEquals("false", DungeonEntityFacts.safeFacts(false, "", "", "").get("onFire"));
        assertEquals("true", DungeonEntityFacts.safeFacts(true, "", "", "").get("onFire"));
    }

    @Test
    void itemDisplayIncludesRegistryNameDisplayNameAndCustomModelData() {
        Map<String, String> facts = DungeonEntityFacts.safeFacts(false, "minecraft:iron_sword", "Dungeon blade",
            "minecraft:custom_model_data=731");

        assertEquals("minecraft:iron_sword", facts.get("itemId"));
        assertEquals("Dungeon blade", facts.get("displayName"));
        assertTrue(facts.get("components").contains("custom_model_data=731"));
    }

    @Test
    void boundsAllDiagnosticKeysAndValuesWithoutNbtDump() {
        Map<String, String> facts = DungeonEntityFacts.safeFacts(false, "minecraft:iron_sword", "x".repeat(1000),
            "minecraft:custom_model_data=731");

        assertTrue(facts.size() <= 8);
        assertTrue(facts.keySet().stream().allMatch(key -> key.length() <= 32));
        assertTrue(facts.values().stream().allMatch(value -> value.length() <= 256));
        assertFalse(facts.keySet().stream().anyMatch(key -> key.toLowerCase().contains("nbt")));
        assertFalse(facts.values().stream().anyMatch(value -> value.contains("CompoundTag")));
    }

    @Test
    void armorStandIncludesEquippedItemIdAndCustomModelPerSlot() {
        Map<String, String> facts = DungeonEntityFacts.equipmentFacts(false,Map.of(
            "head","itemId=minecraft:paper;components=minecraft:custom_model_data=731",
            "mainhand","itemId=cobblemon:poke_ball;components="+"x".repeat(1000),
            "unrecognized","ignored"));

        assertTrue(facts.get("equipment:head").contains("itemId=minecraft:paper"));
        assertTrue(facts.get("equipment:head").contains("custom_model_data=731"));
        assertTrue(facts.get("equipment:mainhand").contains("cobblemon:poke_ball"));
        assertFalse(facts.containsKey("equipment:unrecognized"));
        assertTrue(facts.size() <= 8);
        assertTrue(facts.values().stream().allMatch(value -> value.length() <= 256));
    }
}


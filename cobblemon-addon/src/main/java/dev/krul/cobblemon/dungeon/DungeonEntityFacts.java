package dev.krul.cobblemon.dungeon;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts a small, read-only diagnostic fact set from a live entity. */
public final class DungeonEntityFacts {
    private static final int MAX_FACTS = 8;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final int MAX_COMPONENTS = 16;
    private static final EquipmentSlot[] ARMOR_STAND_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET
    };

    private DungeonEntityFacts() {
    }

    public static Map<String, String> facts(Entity entity) {
        if (entity instanceof DisplayEntity.ItemDisplayEntity itemDisplay) {
            ItemStack stack = itemDisplay.getStackReference(0).get();
            if (!stack.isEmpty()) {
                Map<String, String> facts = new LinkedHashMap<>(safeFacts(entity.isOnFire(),
                    String.valueOf(Registries.ITEM.getId(stack.getItem())), stack.getName().getString(), components(stack)));
                facts.put("item_id", String.valueOf(Registries.ITEM.getId(stack.getItem())));
                CustomModelDataComponent model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
                if (model != null) facts.put("model_data", Integer.toString(model.value()));
                return Map.copyOf(facts);
            }
        }
        if (entity instanceof ArmorStandEntity armorStand) return armorStandFacts(armorStand);
        return safeFacts(entity.isOnFire(), "", "", "");
    }

    private static Map<String, String> armorStandFacts(ArmorStandEntity armorStand) {
        Map<String, String> equipped = new LinkedHashMap<>();
        for (EquipmentSlot slot : ARMOR_STAND_SLOTS) {
            ItemStack stack = armorStand.getEquippedStack(slot);
            if (!stack.isEmpty()) equipped.put(slot.getName(), equipment(stack));
        }
        Map<String, String> facts = new LinkedHashMap<>(equipmentFacts(armorStand.isOnFire(), equipped));
        ItemStack head = armorStand.getEquippedStack(EquipmentSlot.HEAD);
        if (!head.isEmpty()) {
            facts.put("head_item_id", String.valueOf(Registries.ITEM.getId(head.getItem())));
            CustomModelDataComponent model = head.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            if (model != null) facts.put("head_model_data", Integer.toString(model.value()));
        }
        return Map.copyOf(facts);
    }

    static Map<String,String> equipmentFacts(boolean onFire,Map<String,String> equipped) {
        Map<String,String> facts=new LinkedHashMap<>();
        put(facts,"onFire",Boolean.toString(onFire));
        for (EquipmentSlot slot:ARMOR_STAND_SLOTS) put(facts,"equipment:"+slot.getName(),equipped.get(slot.getName()));
        return Map.copyOf(facts);
    }

    private static String equipment(ItemStack stack) {
        return "itemId=" + Registries.ITEM.getId(stack.getItem())
            + ";components=" + components(stack);
    }

    static Map<String, String> safeFacts(boolean onFire, String itemId, String displayName, String components) {
        Map<String, String> facts = new LinkedHashMap<>();
        put(facts, "onFire", Boolean.toString(onFire));
        if (itemId != null && !itemId.isBlank()) put(facts, "itemId", itemId);
        if (displayName != null && !displayName.isBlank()) put(facts, "displayName", displayName);
        if (components != null && !components.isBlank()) put(facts, "components", components);

        return Map.copyOf(facts);
    }

    private static String components(ItemStack stack) {
        CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return componentSummary(modelData == null ? null : modelData.value(),
            stack.getComponents().getTypes().stream()
                .map(type -> String.valueOf(Registries.DATA_COMPONENT_TYPE.getId(type))).toList());
    }

    static String componentSummary(Integer modelData, Iterable<String> componentIds) {
        List<String> entries = new ArrayList<>();
        for (String id : componentIds) {
            if (entries.size() == MAX_COMPONENTS - (modelData == null ? 0 : 1)) break;
            if (id.equals("minecraft:custom_model_data") && modelData != null) continue;
            entries.add(id);
        }
        entries.sort(Comparator.naturalOrder());
        // Keep the only component value we extract ahead of count/text truncation.
        if (modelData != null) entries.addFirst("minecraft:custom_model_data=" + modelData);
        return entries.isEmpty() ? "none" : bounded(String.join(",", entries));
    }

    private static void put(Map<String, String> facts, String key, String value) {
        if (facts.size() >= MAX_FACTS || value == null) return;
        facts.put(key, bounded(value));
    }

    private static String bounded(String value) {
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }
}

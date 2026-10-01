package dev.krul.cobblemon.dungeon;

import dev.krul.cobblemon.rules.RuleProfile;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonRenderSelectorTest {
    @Test void categoryDistanceCapsGlobalVisualRange() {
        var profile = RuleProfile.parse("""
            {"version":2,"settings":{"visualRange":1024},"categories":[
              {"id":"CHEST","name":"Chest","enabled":true,"color":[255,190,55,220],"boxes":true,"tracers":false,"nametags":true,"arrows":true,"maxDistance":8}
            ],"rules":[
              {"id":"chest","enabled":true,"categoryId":"CHEST","priority":1,"label":"Chest","all":[{"field":"kind","equals":"BLOCK_ENTITY"}]}
            ]}
            """);
        var observation = new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, "test:world", "chest",
            "minecraft:chest", "", "", new net.minecraft.util.math.BlockPos(10, 0, 0), Map.of(), 0);

        assertTrue(DungeonRenderSelector.select(List.of(observation), "test:world", 0, Long.MAX_VALUE,
            0, 0, 0, 1024, DungeonTargetFilters.ALL, profile).isEmpty());
    }
    private static DungeonObservation observation(String id, String registry, int x, long time) {
        return new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, "test:room", id, registry,
            "", "Chest", new BlockPos(x, 0, 0), Map.of(), time);
    }
    @Test void noProfileMeansNoDungeonClassification() {
        assertTrue(DungeonRenderSelector.select(List.of(observation("chest", "cobblemon:chest", 1, 100)),
            "test:room", 100, 1000, 0, 0, 0, 64).isEmpty());
    }


    @Test void customCategoryIdIsRenderedWithoutDungeonEnum() {
        var profile = RuleProfile.parse("""
            {"version":2,"categories":[{"id":"rare_loot","name":"Rare loot","enabled":true,"color":[1,2,3,255],"boxes":true,"tracers":false,"nametags":true,"arrows":true,"maxDistance":128}],
             "rules":[{"id":"rare","enabled":true,"categoryId":"rare_loot","priority":10,"label":"Rare","all":[{"field":"registry_id","equals":"cobblemon:chest"}]}]}
            """);
        assertTrue(profile.diagnostics().isEmpty(), profile.diagnostics().toString());
        var target = DungeonRenderSelector.classify(observation("rare", "cobblemon:chest", 1, 100), 1, profile).orElseThrow();
        assertEquals("rare_loot", target.categoryId());
        assertEquals("Rare", target.label());
    }
    @Test void configuredClassificationPreservesDistanceOrderFreshnessAndDimensionChecks() {
        var observations = List.of(observation("far", "cobblemon:chest", 20, 100),
            observation("near", "cobblemon:chest", 1, 100), observation("stale", "cobblemon:chest", 0, 1),
            observation("future", "cobblemon:chest", 2, 101), observation("noise", "minecraft:sign", 0, 100),
            observation("distant", "cobblemon:chest", 100, 100));
        var selected = DungeonRenderSelector.select(observations, "test:room", 100, 50, 0, 0, 0, 64,
            DungeonTargetFilters.ALL, TestProfiles.PROFILE);
        assertEquals(List.of("near", "far"), selected.stream().map(DungeonRenderTarget::identity).toList());
        assertTrue(DungeonRenderSelector.select(observations, "other:room", 100, 1000, 0, 0, 0, 64,
            DungeonTargetFilters.ALL, TestProfiles.PROFILE).isEmpty());
    }
    @Test void targetVisibilityFiltersStillApplyAfterRuleMatching() {
        assertTrue(DungeonRenderSelector.select(List.of(observation("chest", "cobblemon:chest", 1, 100)),
            "test:room", 100, 1000, 0, 0, 0, 64,
            new DungeonTargetFilters(false, true, true, true, true, true), TestProfiles.PROFILE).isEmpty());
    }

    @Test void openedDungeonChestIsRemovedFromRenderTargetsButOtherTargetsRemain() {
        var cache = new OpenedChestCache();
        var opened = observation("opened", "cobblemon:chest", 1, 100);
        var unopened = observation("unopened", "cobblemon:chest", 2, 100);
        cache.markOpen("test:room", opened.position());
        var targets = DungeonRenderSelector.select(List.of(opened, unopened), "test:room", 100, 1000,
            0, 0, 0, 64, DungeonTargetFilters.ALL, TestProfiles.PROFILE, cache);

        assertEquals(List.of("unopened"), targets.stream().map(DungeonRenderTarget::identity).toList());
    }
    @Test void academyModelNumbersOnlyMatchExplicitPresetAndDimension() throws Exception {
        RuleProfile profile = TestProfiles.academy();
        for (var model : Map.of(1150, "Poké Ball", 1151, "Ultra Ball", 1152, "Master Ball", 1478, "Dream Ball").entrySet()) {
            var ball = new DungeonObservation(DungeonObservation.Kind.ENTITY, "dungeons:dungeon", "ball",
                "minecraft:armor_stand", "", "", new BlockPos(1, 0, 0),
                Map.of("head_item_id", "minecraft:paper", "head_model_data", model.getKey().toString()), 100);
            assertEquals(model.getValue(), DungeonRenderSelector.classify(ball, 1, profile).orElseThrow().label());
            assertTrue(DungeonRenderSelector.classify(ball, 1).isEmpty());
            var otherWorld = new DungeonObservation(ball.kind(), "minecraft:overworld", ball.identity(),
                ball.registryId(), "", "", ball.position(), ball.facts(), 100);
            assertTrue(DungeonRenderSelector.classify(otherWorld, 1, profile).isEmpty());
        }
    }
    @Test void wildPokemonRulesRequirePositiveOwnershipEvidence() throws Exception {
        RuleProfile profile = TestProfiles.academy();
        var missingOwner = new DungeonObservation(DungeonObservation.Kind.POKEMON, "dungeons:dungeon", "pokemon",
            "cobblemon:ralts", "", "Ralts", new BlockPos(1, 0, 0), Map.of("level", "90"), 100);
        assertTrue(DungeonRenderSelector.classify(missingOwner, 1, profile).isEmpty());
        var provenWild = new DungeonObservation(missingOwner.kind(), missingOwner.dimension(), "pokemon",
            missingOwner.registryId(), "", "Ralts", missingOwner.position(),
            Map.of("species", "cobblemon:ralts", "level", "90", "wild", "true"), 100);
        assertEquals("cobblemon:ralts Lv.90", DungeonRenderSelector.classify(provenWild, 1, profile).orElseThrow().label());
    }
}

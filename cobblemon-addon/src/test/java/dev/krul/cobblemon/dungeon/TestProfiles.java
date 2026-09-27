package dev.krul.cobblemon.dungeon;
import dev.krul.cobblemon.rules.RuleProfile;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
final class TestProfiles {
    static final RuleProfile PROFILE = RuleProfile.parse("""
        {"version":1,"rules":[
         {"id":"chest","target":"CHEST","label":"Chest","all":[{"field":"kind","equals":"BLOCK_ENTITY"},{"field":"registry_id","equals":"cobblemon:chest"}]},
         {"id":"ball","target":"POKE_BALL","label":"Ball","all":[{"field":"kind","equals":"ENTITY"},{"field":"head_item_id","equals":"minecraft:paper"},{"field":"head_model_data","equals":1150}]}
        ]}
        """);
    static RuleProfile academy() throws Exception {
        try(var input = TestProfiles.class.getResourceAsStream("/profiles/academy-example.json")) {
            assertNotNull(input);
            var profile = RuleProfile.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(profile.diagnostics().isEmpty(), profile.diagnostics().toString());
            return profile;
        }
    }
}

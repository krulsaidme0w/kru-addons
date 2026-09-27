package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.BattleMove;
import dev.krul.cobblemon.battle.MoveCategory;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.CobblemonMatchupReader;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Freshness;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleFactsReaderTest {
    public record Battle(UUID getBattleId, Side getSide1, Side getSide2) {}
    public record Side(List<Actor> getActors) {}
    public record Actor(UUID getUuid, List<?> getPokemon, List<Active> getActivePokemon) {}
    public record Active(Object getBattlePokemon) {}
    public record Properties(String getSpecies, Integer getLevel) {}
    public record Shown(UUID getUuid, double getHpValue, double getMaxHp, Boolean isFlatHp, String getStatus,
                        Map<?, ?> getStatChanges) {
        Shown(UUID uuid, double hp, double max, Boolean flat, String status) {
            this(uuid, hp, max, flat, status, Map.of());
        }
        public Properties getProperties() { return new Properties("charizard", 50); }
        public String getDisplayName() { return "Charizard"; }
    }
    public record Profile(UUID getUuid, int getCurrentHealth, int getMaxHealth) {}
    public record TypedShown(UUID getUuid, List<String> getTypes, String getTerastallized) {
        public Properties getProperties() { return new Properties("charizard", 50); }
    }
    // Runtime Stat is an interface: identifiers are getters, not enum names or toString().
    public record StatKey(String getShowdownId) {}
    public record Dto(UUID getUuid, Object getActiveBattlePokemonDTO, Map<StatKey, Double> getBuffs,
                      String getAbility, Object getHeldItem, List<MoveDto> getMoves) {}
    public record MoveDto(String getMove) {}
    public record Repository(Map<UUID, Object> getBattles, Map<UUID, List<Dto>> getActors) {}
    public record Info(int getTurn) {}

    private final UUID battle = UUID.randomUUID(), player = UUID.randomUUID(), foeActor = UUID.randomUUID();
    private final UUID ownId = UUID.randomUUID(), foeId = UUID.randomUUID();
    private final CobblemonMatchupReader reader = new CobblemonMatchupReader(new CobblemonMatchupReader.Catalog() {
        public BattleMove move(String id) { return new BattleMove(id, id, PokemonType.FIRE, MoveCategory.SPECIAL, 90); }
        public List<PokemonType> types(String species, Set<String> aspects) { return List.of(PokemonType.FIRE, PokemonType.FLYING); }
        public String moveId(Object text) { return text == null ? "" : text.toString(); }
        public String text(Object value) { return value == null ? "" : value.toString(); }
    });

    private MatchupSnapshot read(Object own, Object foe, Dto dto, boolean active) {
        Battle b = new Battle(battle, new Side(List.of(new Actor(player, List.of(), own == null ? List.of() : List.of(new Active(own))))),
            new Side(List.of(new Actor(foeActor, List.of(), active ? List.of(new Active(foe)) : List.of()))));
        return reader.read(b, player, new Repository(Map.of(battle, new Info(7)), Map.of(foeActor, List.of(dto))));
    }

    private Dto dto(Object shown) { return new Dto(foeId, shown, Map.of(), "", "", List.of()); }

    @Test void missingHpFormatDoesNotTurnRawHpIntoOneHundredPercent() {
        var shown = new Shown(foeId, 75, 100, null, "");
        var f = read(null, shown, dto(shown), true).enemies().getFirst();
        assertEquals(-1, f.hpPercent());
        assertFalse(f.facts().health().known());
        assertEquals("?", BattleFactsModel.health(f.facts().health()));
    }

    @Test void missingAndMalformedHpStayUnknownIncludingNanAndInfinity() {
        for (double hp : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 1.5}) {
            var shown = new Shown(foeId, hp, 100, false, "");
            var f = read(null, shown, dto(shown), true).enemies().getFirst();
            assertEquals(-1, f.hpPercent(), "hp=" + hp);
            assertFalse(f.facts().health().known());
        }
        var hidden = read(null, null, dto(null), false).enemies().getFirst();
        assertFalse(hidden.facts().health().known());
        assertFalse(hidden.facts().status().known());
    }

    @Test void ownActiveHpCanBeExactButEnemyNeverExposesAnInferredTotal() {
        var own = new Shown(ownId, 72, 183, true, "par");
        var foe = new Shown(foeId, 72, 183, true, "brn");
        var snapshot = read(own, foe, dto(foe), true);
        assertEquals(1, snapshot.own().size(), "Visible own slot must not disappear without profile/request");
        var ownHp = snapshot.own().getFirst().facts().health();
        assertTrue(ownHp.value().exact());
        assertEquals(72, ownHp.value().current());
        assertEquals(183, ownHp.value().maximum());
        var foeHp = snapshot.enemies().getFirst().facts().health();
        assertFalse(foeHp.value().exact());
        assertEquals(-1, foeHp.value().maximum());
        assertEquals(Freshness.CURRENT, foeHp.freshness());
        assertEquals(72 * 100.0 / 183, foeHp.value().percent());
    }

    @Test void benchFactsRemainLastObservedAndSpeciesTypesDoNotProveCurrentTypes() {
        var foe = new Shown(foeId, .35, 100, false, "brn", Map.of(new StatKey("atk"), 1,
            new StatKey("spa"), -2, new StatKey("def"), 99, new StatKey("spe"), Double.NaN));
        var dto = new Dto(foeId, foe, Map.of(new StatKey("atk"), 1.5, new StatKey("spa"), .5),
            "blaze", "Leftovers", List.of(new MoveDto("flamethrower")));
        var f = read(null, foe, dto, false).enemies().getFirst();
        assertEquals(Freshness.LAST_OBSERVED, f.facts().health().freshness());
        assertEquals(Freshness.LAST_OBSERVED, f.facts().status().freshness());
        assertEquals(Freshness.LAST_OBSERVED, f.facts().ability().freshness());
        assertFalse(f.facts().types().known());
        assertEquals(List.of(PokemonType.FIRE, PokemonType.FLYING), f.baseTypes());
        assertEquals(List.of("Atk +1", "SpA -2"), f.facts().changes().value());
        assertEquals("~35.0%", BattleFactsModel.health(f.facts().health()));
        String details = String.join("\n", BattleFactsModel.details(f, false));
        assertTrue(details.contains("Leftovers"));
        assertTrue(details.contains("flamethrower"));
        assertTrue(details.contains("PP ?"));
        assertTrue(details.contains("Revealed ability"));
    }

    @Test void emptyStatusAndEmptyStageMapAreUnavailableRatherThanHealthyOrZero() {
        var foe = new Shown(foeId, 1, 100, false, "");
        var facts = read(null, foe, dto(foe), true).enemies().getFirst().facts();
        assertFalse(facts.status().known());
        assertFalse(facts.changes().known());
        assertFalse(facts.ability().known());
        assertFalse(facts.item().known());
    }

    @Test void nonexistentNativeProjectionGettersCannotAuthorizeTypesOrTera() {
        var foe = new TypedShown(foeId, List.of("water"), "water");
        var facts = read(null, foe, dto(foe), true).enemies().getFirst().facts();
        assertFalse(facts.types().known());
        assertFalse(facts.changes().known());
        assertFalse(facts.health().known());
    }

    @Test void emptyTeraValueDoesNotClaimActivation() {
        var foe = new TypedShown(foeId, List.of(), "");
        var facts = read(null, foe, dto(foe), true).enemies().getFirst().facts();
        assertFalse(facts.changes().known());
        assertFalse(facts.types().known());
    }

    @Test void nullHeldItemRemainsUnknownInBothSnapshotAndDetails() {
        var foe = new Shown(foeId, 1, 100, false, "");
        var dto = new Dto(foeId, foe, Map.of(), "", null, List.of());
        var fighter = read(null, foe, dto, true).enemies().getFirst();
        assertTrue(fighter.item().isBlank());
        assertFalse(fighter.facts().item().known());
        assertEquals("?", BattleFactsModel.value(fighter.facts().item()));
        assertFalse(String.join("\n", BattleFactsModel.details(fighter, false)).contains("None (revealed)"));
    }

    @Test void ownProfileFallbackIsExplicitlyNotCurrent() {
        Battle b = new Battle(battle,
            new Side(List.of(new Actor(player, List.of(new Profile(ownId, 20, 100)), List.of()))), new Side(List.of()));
        var f = reader.read(b, player, null).own().getFirst();
        assertEquals(Freshness.PROFILE, f.facts().health().freshness());
        assertEquals("~20/100", BattleFactsModel.health(f.facts().health()));
    }

    @Test void replacementBattleCannotBorrowOldRepositoryFacts() {
        var foe = new Shown(foeId, .35, 100, false, "brn");
        Battle b = new Battle(UUID.randomUUID(), new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(foeActor, List.of(), List.of(new Active(foe))))));
        var f = reader.read(b, player, new Repository(Map.of(battle, new Info(7)), Map.of(foeActor,
            List.of(new Dto(foeId, foe, Map.of(new StatKey("atk"), 1.5), "blaze", "Leftovers", List.of()))))).enemies().getFirst();
        assertFalse(f.facts().ability().known());
        assertFalse(f.facts().item().known());
        assertFalse(f.facts().changes().known());
    }

    @Test void ownAndEnemyActiveStagesComeFromVisiblePokemonWithStatKeys() {
        var own = new Shown(ownId, 80, 100, true, "", Map.of(new StatKey("atk"), 1, new StatKey("def"), -1));
        var foe = new Shown(foeId, .5, 100, false, "", Map.of(new StatKey("accuracy"), 2, new StatKey("evasion"), -3));
        var snapshot = read(own, foe, dto(foe), true);
        assertEquals(List.of("Atk +1", "Def -1"), snapshot.own().getFirst().facts().changes().value());
        assertEquals(Freshness.CURRENT, snapshot.own().getFirst().facts().changes().freshness());
        assertEquals(List.of("Accuracy +2", "Evasion -3"), snapshot.enemies().getFirst().facts().changes().value());
        assertEquals(Freshness.CURRENT, snapshot.enemies().getFirst().facts().changes().freshness());
    }

    @Test void multiplierBuffsNeverBecomeStagesEvenWhenNumericallyIntegral() {
        var foe = new Shown(foeId, 1, 100, false, "");
        var dto = new Dto(foeId, foe, Map.of(new StatKey("atk"), 2.0, new StatKey("def"), .5), "", "", List.of());
        assertFalse(read(null, foe, dto, true).enemies().getFirst().facts().changes().known());
        assertFalse(read(null, foe, dto, false).enemies().getFirst().facts().changes().known());
    }

    @Test void liveStagesWinOverOldRepositoryProjectionAndDoNotBorrowMissingStages() {
        var live = new Shown(foeId, .5, 100, false, "", Map.of(new StatKey("atk"), -1));
        var old = new Shown(foeId, 1, 100, false, "", Map.of(new StatKey("atk"), 3, new StatKey("spe"), 2));
        var snapshot = read(null, live, dto(old), true);
        assertEquals(List.of("Atk -1"), snapshot.enemies().getFirst().facts().changes().value());
        var noStages = new Shown(foeId, .5, 100, false, "");
        assertFalse(read(null, noStages, dto(old), true).enemies().getFirst().facts().changes().known());
    }

    @Test void stagesRequireIntegerValuesAndSupportedStatIdentifiers() {
        var foe = new Shown(foeId, .5, 100, false, "", Map.of(new StatKey("atk"), 1.0,
            new StatKey("def"), -7, new StatKey("hp"), 1, new StatKey("unknown"), 1,
            new StatKey("spa"), 0, new StatKey("spd"), -6, new StatKey("spe"), 6));
        assertEquals(List.of("SpA 0", "SpD -6", "Spe +6"),
            read(null, foe, dto(foe), true).enemies().getFirst().facts().changes().value());
    }
}

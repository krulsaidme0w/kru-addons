package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.*;
import dev.krul.cobblemon.battle.matrix.BattleMatrixModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.PokemonType.*;

class CobblemonMatchupReaderTest {
    // Getter shapes mirror javap on the installed 1.7.3 fork; these are boundary fixtures, not mocks of the calculator.
    public record Battle(UUID getBattleId, Side getSide1, Side getSide2, List<Request> getPendingActionRequests, boolean getSpectating, boolean getMustChoose) {
        Battle(UUID id, Side first, Side second, List<Request> requests, boolean spectating) { this(id, first, second, requests, spectating, true); }
    }
    public record Side(List<Actor> getActors) {}
    public record Actor(UUID getUuid, List<?> getPokemon, List<Active> getActivePokemon) {}
    public static final class Active {
        Object pokemon;
        Active(Object pokemon) { this.pokemon = pokemon; }
        public Object getBattlePokemon() { return pokemon; }
    }
    public record Shown(UUID getUuid, String getDisplayName, Properties getProperties, Set<String> getAspects,
                        float getHpValue, float getMaxHp, boolean isFlatHp, String getStatus, Object getTerastallized) {
        Shown(UUID id, String name, Properties properties, Set<String> aspects, float hp, float maxHp, boolean flat, String status) {
            this(id, name, properties, aspects, hp, maxHp, flat, status, null);
        }
    }
    public record Properties(String getSpecies, Integer getLevel) {}
    public record Dto(UUID getUuid, boolean getFainted, String getAbility, List<MoveDto> getMoves,
                      String getHeldItem, Object getTerastallized, Object getActiveBattlePokemonDTO) {}
    public record MoveDto(String getMove, int getTimesUsed) {}
    public record Repository(Map<UUID, List<Dto>> getActors, Map<UUID, Info> getBattles) {}
    public record Info(int getTurn) {}
    public record Showdown(UUID getUuid, List<String> getTypes, List<String> getBaseTypes, List<String> getMoves,
                           String getCondition, String getAbility, String getDetails, boolean getActive) {}
    public record Request(Active getActivePokemon, RequestSide getSide, Moveset getMoveSet, boolean getForceSwitch, Object getResponse) {
        Request(Active active, RequestSide side, Moveset moves, boolean force) { this(active, side, moves, force, null); }
    }
    public record RequestSide(List<Showdown> getPokemon, UUID getName) {
        RequestSide(List<Showdown> pokemon) { this(pokemon, null); }
    }
    public record Moveset(List<RequestMove> getMoves, boolean getTrapped) {}
    public record RequestMove(String getId, int getPp, boolean getDisabled) {}
    public record Tera(Element getType) {}
    public record Element(String getName) {}
    public record Profile(UUID getUuid, List<String> getTypes) {}
    public record TeraProfile(UUID getUuid, List<String> getTypes, Tera getTeraType) {}
    public record MoveProfile(UUID getUuid, ProfileMoves getMoveSet) {}
    public record ProfileMoves(List<ProfileMove> getMoves) {}
    public record ProfileMove(String getName, int getCurrentPp) {}
    public record ElementalType(String getName) {}
    public static final class SpeciesShown {
        private final UUID id;
        SpeciesShown(UUID id) { this.id = id; }
        public UUID getUuid() { return id; }
        public String getDisplayName() { return "Cherubi"; }
        public Properties getProperties() { return new Properties("cherubi", 17); }
        public Set<String> getAspects() { return Set.of(); }
        public float getHpValue() { return 100; }
        public float getMaxHp() { return 100; }
        public boolean isFlatHp() { return true; }
        public String getStatus() { return ""; }
        public Object getTerastallized() { return null; }
        public Object getSpecies() { return new SpeciesData(); }
    }
    public static final class SpeciesData {
        public List<ElementalType> getTypes() { return List.of(new ElementalType("grass")); }
    }

    final UUID player = UUID.randomUUID(), enemy = UUID.randomUUID(), mine = UUID.randomUUID(), foe = UUID.randomUUID(), battleId = UUID.randomUUID();
    final CobblemonMatchupReader reader = new CobblemonMatchupReader(new CobblemonMatchupReader.Catalog() {
        public BattleMove move(String id) {
            PokemonType type = switch (id) {
                case "surf" -> WATER;
                case "aurawheel" -> ELECTRIC;
                case "ragingbull", "terrainpulse", "struggle" -> NORMAL;
                default -> FIRE;
            };
            return new BattleMove(id, id, type, MoveCategory.SPECIAL, 90);
        }
        public List<PokemonType> types(String species, Set<String> aspects) { return species.equals("charizard") ? List.of(FIRE, FLYING) : List.of(WATER); }
        public String moveId(Object text) { String s = String.valueOf(text); return s.startsWith("cobblemon.move.") ? s.substring(15) : ""; }
        public String text(Object value) { return value == null ? "" : value.toString(); }
    });

    Shown shown(UUID id) { return new Shown(id, "Charizard", new Properties("charizard", 50), Set.of(), 75, 100, true, "brn"); }
    Battle battle(UUID id, List<Request> requests) {
        return new Battle(id, new Side(List.of(new Actor(player, List.of(), List.of(new Active(shown(mine)))))),
            new Side(List.of(new Actor(enemy, List.of(), List.of(new Active(shown(foe)))))), requests, false);
    }
    Dto dto(UUID id, Object details) { return new Dto(id, false, "blaze", List.of(new MoveDto("cobblemon.move.flamethrower", 1)), "", null, details); }
    Repository repository(List<Dto> foes) { return new Repository(Map.of(enemy, foes, UUID.randomUUID(), List.of(dto(UUID.randomUUID(), shown(UUID.randomUUID())))), Map.of(battleId, new Info(3))); }

    @Test void scopesRepositoryToBattleActorsAndKeepsHiddenSlotsUnknown() {
        var result = reader.read(battle(battleId, List.of()), player, repository(List.of(dto(foe, shown(foe)), dto(UUID.randomUUID(), null))));
        assertEquals(2, result.enemies().size());
        assertTrue(result.enemies().getFirst().types().isEmpty());
        assertEquals(75, result.enemies().getFirst().hpPercent());
        assertEquals("flamethrower", result.enemies().getFirst().moves().getFirst().data().id());
        assertTrue(result.enemies().get(1).types().isEmpty());
        assertTrue(result.enemies().get(1).moves().isEmpty());
        assertEquals(3, result.turn());
    }
    @Test void activeClientBattlePokemonTypesAreUsedForCurrentOpponentChart() {
        var visible = new SpeciesShown(foe);
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(enemy, List.of(), List.of(new Active(visible))))), List.of(), false);
        var result = reader.read(b, player, repository(List.of(dto(foe, visible)))).enemies().getFirst();
        assertEquals(List.of(GRASS), result.types());
        assertEquals(MatchupSnapshot.Freshness.CURRENT, result.facts().types().freshness());
    }
    @Test void portraitsUseVisibleSpeciesAndAspectsNotNicknamesOrUnrevealedSlots() {
        Shown visible = new Shown(foe, "My Ice Dragon", new Properties("charizard", 50), Set.of("shiny", "mega-x"), .5f, 100, false, "");
        var battle = new Battle(battleId, new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(enemy, List.of(), List.of()))), List.of(), false);
        var result = reader.read(battle, player, repository(List.of(dto(foe, visible), dto(UUID.randomUUID(), null))));
        Object portrait = BattleReflection.get(result.enemies().getFirst(), "portrait");
        assertNotNull(portrait);
        assertEquals("charizard", BattleReflection.get(portrait, "speciesId"));
        assertEquals(Set.of("shiny", "mega-x"), BattleReflection.get(portrait, "aspects"));
        Object hidden = BattleReflection.get(result.enemies().get(1), "portrait");
        assertTrue(hidden == null || "".equals(BattleReflection.get(hidden, "speciesId")));
    }
    @Test void activeRequestOverridesMoveAvailabilityAndLegalRoster() {
        var poke = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "12/100 par", "torrent", "Blastoise, L50", true);
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(poke)), new Moveset(List.of(new RequestMove("surf", 0, true)), true), false);
        var result = reader.read(battle(battleId, List.of(request)), player, repository(List.of(dto(foe, shown(foe)))));
        assertEquals(1, result.own().size());
        var own = result.own().getFirst();
        assertEquals(List.of(WATER), own.types());
        assertEquals(0, own.moves().getFirst().pp());
        assertTrue(own.moves().getFirst().disabled());
        assertTrue(own.moves().getFirst().availabilityKnown());
        assertTrue(own.notes().stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains("trapped")));
    }

    @Test void auraWheelDynamicTypeCannotBecomeFabricatedElectricImmunity() {
        var move = requestMoves("aurawheel").getFirst();

        assertEquals("?", BattleMatrixModel.evaluate(move, currentTarget("Golem", GROUND)).label());
    }

    @Test void ragingBullDynamicTypeCannotBecomeFabricatedNormalImmunity() {
        var move = requestMoves("ragingbull").getFirst();

        assertEquals("?", BattleMatrixModel.evaluate(move, currentTarget("Gengar", GHOST)).label());
    }

    @Test void terrainPulseDynamicTypeCannotBecomeFabricatedNormalImmunity() {
        var move = requestMoves("terrainpulse").getFirst();

        assertEquals("?", BattleMatrixModel.evaluate(move, currentTarget("Gengar", GHOST)).label());
    }

    @Test void struggleQuestionMarkTypeCannotBecomeFabricatedNormalImmunity() {
        var move = requestMoves("struggle").getFirst();

        assertEquals("?", BattleMatrixModel.evaluate(move, currentTarget("Gengar", GHOST)).label());
    }

    @Test void ordinaryKnownMoveFromReaderRemainsNumericInMatrix() {
        var move = requestMoves("flamethrower").getFirst();

        assertEquals("2", BattleMatrixModel.evaluate(move, currentTarget("Venusaur", GRASS)).label());
    }

    @Test void unsupportedShownTeraGetterCannotSupplyBattleTypes() {
        var own = new Shown(mine, "Kyurem-Black", new Properties("kyurem-black", 100), Set.of(), 100, 100, true, "",
            new Tera(new Element("fairy")));
        var ownActor = new Actor(player, List.of(new Profile(mine, List.of("dragon", "ice"))), List.of(new Active(own)));
        var battle = new Battle(battleId, new Side(List.of(ownActor)), new Side(List.of()), List.of(), false);

        var result = reader.read(battle, player, null).own().getFirst();

        assertFalse(result.teraActive());
        assertTrue(result.types().isEmpty());
        assertFalse(result.facts().types().known());
    }
    @Test void persistentTeraTypeDoesNotPretendToBeActive() {
        var own = new Shown(mine, "Kyurem-Black", new Properties("kyurem-black", 100), Set.of(), 100, 100, true, "");
        var ownActor = new Actor(player, List.of(new TeraProfile(mine, List.of("dragon", "ice"), new Tera(new Element("fairy")))),
            List.of(new Active(own)));
        var requestPokemon = new Showdown(mine, List.of("fairy"), List.of("dragon", "ice"), List.of(), "100/100", "", "Kyurem-Black, L100", true);
        var request = new Request(new Active(own), new RequestSide(List.of(requestPokemon)), null, false);
        var battle = new Battle(battleId, new Side(List.of(ownActor)), new Side(List.of()), List.of(request), false);

        var result = reader.read(battle, player, null).own().getFirst();

        assertFalse(result.teraActive());
        assertEquals(FAIRY, result.teraType());
        assertEquals(List.of(FAIRY), result.types(), "Current request types remain authoritative without proving Tera activation");
        assertEquals(List.of(DRAGON, ICE), result.baseTypes());
    }

    @Test void storedTeraMetadataDoesNotOverwriteDifferentAuthoritativeCurrentTypes() {
        var profile = new TeraProfile(mine, List.of("fire", "flying"), new Tera(new Element("fairy")));
        var current = new Showdown(mine, List.of("water"), List.of("fire", "flying"), List.of(), "100/100", "", "Charizard, L50", true);
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(current)), null, false);
        var actor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(request), false);

        var result = reader.read(b, player, null).own().getFirst();

        assertEquals(List.of(WATER), result.types());
        assertEquals(List.of(FIRE, FLYING), result.baseTypes());
        assertEquals(FAIRY, result.teraType());
        assertFalse(result.teraActive());
    }

    @Test void storedTeraMetadataDoesNotFillMissingCurrentTypesFromProfileBaseline() {
        var profile = new TeraProfile(mine, List.of("fire", "flying"), new Tera(new Element("fairy")));
        var actor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(), false);

        var result = reader.read(b, player, null).own().getFirst();

        assertTrue(result.types().isEmpty());
        assertEquals(List.of(FIRE, FLYING), result.baseTypes());
        assertEquals(FAIRY, result.teraType());
        assertFalse(result.teraActive());
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("Current defensive types unknown")));
    }

    @Test void profileBaseTypesSurviveWithoutCurrentRequestButStayUnproven() {
        var profile = new Profile(mine, List.of("fire", "flying"));
        var ownActor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var b = new Battle(
            battleId,
            new Side(List.of(ownActor)),
            new Side(List.of()),
            List.of(),
            false);

        var own = reader.read(b, player, null).own().getFirst();

        assertTrue(own.types().isEmpty(), "a profile cannot prove current defensive types");
        assertEquals(List.of(FIRE, FLYING), own.baseTypes());
        assertFalse(own.currentEffects());
        assertTrue(own.notes().stream().anyMatch(note -> note.contains("Current defensive types unknown")));
    }

    @Test void requestCurrentTypesRemainDistinctFromProfileBaseline() {
        var profile = new Profile(mine, List.of("fire", "flying"));
        var requestPokemon = new Showdown(mine, List.of("water"), List.of("fire", "flying"), List.of(), "100/100", "", "Charizard, L50", true);
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(requestPokemon)), null, false);
        var ownActor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var b = new Battle(
            battleId,
            new Side(List.of(ownActor)),
            new Side(List.of()),
            List.of(request),
            false);

        var own = reader.read(b, player, null).own().getFirst();

        assertEquals(List.of(WATER), own.types());
        assertEquals(List.of(FIRE, FLYING), own.baseTypes());
        assertFalse(own.teraActive(), "a request's Pokemon object is not evidence of Terastallization");
        assertFalse(own.currentEffects(), "the fixture has no current ability fact");
    }

    @Test void hiddenEnemyKeepsBothTypeSourcesEmpty() {
        var ownActor = new Actor(player, List.of(), List.of(new Active(shown(mine))));
        var enemyActor = new Actor(enemy, List.of(), List.of());
        var b = new Battle(battleId,
            new Side(List.of(ownActor)),
            new Side(List.of(enemyActor)),
            List.of(),
            false);
        var result = reader.read(b, player,
            repository(List.of(dto(foe, null)))).enemies().getFirst();

        assertTrue(result.types().isEmpty());
        assertTrue(result.baseTypes().isEmpty());
        assertTrue(result.portrait().speciesId().isBlank());
    }
    @Test void noRepositoryDoesNotInventEnemyBenchAndNewBattleDoesNotRetainIt() {
        var first = reader.read(battle(battleId, List.of()), player, repository(List.of(dto(foe, shown(foe)), dto(UUID.randomUUID(), shown(UUID.randomUUID())))));
        assertEquals(2, first.enemies().size());
        var second = reader.read(battle(UUID.randomUUID(), List.of()), player, null);
        assertEquals(1, second.enemies().size());
        assertTrue(second.enemies().getFirst().moves().isEmpty());
        assertNotEquals(first.battleId(), second.battleId());
    }
    @Test void spectatorOrNoBattleCannotBorrowAnotherPlayersTeam() {
        assertTrue(reader.read(battle(battleId, List.of()), UUID.randomUUID(), repository(List.of())).own().isEmpty());
        assertNull(reader.read(null, player, null).battleId());
    }
    @Test void enemyTeraRemainsLastObservedAndPercentageHpIsNotDividedByMaxHp() {
        var shown = new Shown(foe, "Charizard", new Properties("charizard", 50), Set.of(), .5f, 100, false, "");
        var dto = new Dto(foe, false, "blaze", List.of(), "", new Tera(new Element("water")), shown);
        // Benched DTO uses percentage HP; no active client entry overrides the fixture.
        var battle = new Battle(battleId, new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(enemy, List.of(), List.of()))), List.of(), false);
        var result = reader.read(battle, player, repository(List.of(dto))).enemies().getFirst();
        assertTrue(result.types().isEmpty());
        assertEquals(List.of(FIRE, FLYING), result.baseTypes());
        assertEquals(50, result.hpPercent());
        assertFalse(result.currentEffects());
        assertTrue(result.teraActive());
        assertEquals(WATER, result.teraType());
        assertEquals(List.of(WATER), result.facts().types().value());
        assertEquals(MatchupSnapshot.Freshness.LAST_OBSERVED, result.facts().types().freshness());
    }
    @Test void repositoryFromAnotherBattleDoesNotRevealOldBench() {
        var oldRepository = repository(List.of(dto(foe, shown(foe)), dto(UUID.randomUUID(), shown(UUID.randomUUID()))));
        var result = reader.read(battle(UUID.randomUUID(), List.of()), player, oldRepository);
        assertEquals(1, result.enemies().size());
        assertTrue(result.enemies().getFirst().moves().isEmpty());
    }
    @Test void answeredRequestCannotSupplyCurrentAvailabilityOrAbility() {
        var p = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "12/100 par", "torrent", "Blastoise, L50", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), new Moveset(List.of(new RequestMove("surf", 5, false)), false), false, "answered");
        var result = reader.read(battle(battleId, List.of(r)), player, null);
        assertTrue(result.own().stream().noneMatch(f -> f.currentEffects() || f.moves().stream().anyMatch(MatchupSnapshot.Move::availabilityKnown)));
    }
    @Test void retainedUnansweredRequestOutsideChoicePhaseDoesNotOverrideProfiles() {
        var p = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "12/100 par", "torrent", "Blastoise, L50", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), new Moveset(List.of(new RequestMove("surf", 5, false)), false), false);
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(shown(mine)), List.of(new Active(shown(mine)))))), new Side(List.of()), List.of(r), false, false);
        var own = reader.read(b, player, null).own().getFirst();
        assertFalse(own.currentEffects());
        assertTrue(own.moves().isEmpty());
        assertEquals(75, own.hpPercent());
    }
    @Test void unavailableCurrentTypesMustNotRevertTeraToProfileTypes() {
        var p = new Showdown(mine, List.of("water"), List.of("fire", "flying"), List.of("surf"), "75/100", "blaze", "Charizard, L50", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), null, false, "answered");
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(new Profile(mine, List.of("fire", "flying"))), List.of(new Active(shown(mine)))))),
            new Side(List.of()), List.of(r), false, false);
        var own = reader.read(b, player, null).own().getFirst();
        assertEquals(List.of(FIRE, FLYING), own.baseTypes());
        assertTrue(own.types().isEmpty(), "No current proof: must not claim Fire/Flying after Tera Water");
        assertTrue(own.notes().stream().anyMatch(s -> s.contains("Current defensive types unknown")));
    }
    @Test void mutableActiveSlotCannotTransferOldMovesToReplacement() {
        var replacement = UUID.randomUUID();
        var slot = new Active(shown(mine));
        var old = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "12/100", "torrent", "Blastoise, L50", true);
        var next = new Showdown(replacement, List.of("fire"), List.of("fire"), List.of("flamethrower"), "100/100", "blaze", "Charizard, L50", false);
        var r = new Request(slot, new RequestSide(List.of(old, next)), new Moveset(List.of(new RequestMove("surf", 5, false)), false), false);
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(shown(mine), shown(replacement)), List.of(slot)))), new Side(List.of()), List.of(r), false);
        reader.read(b, player, null);
        slot.pokemon = shown(replacement);
        var result = reader.read(b, player, null);
        var incoming = result.own().stream().filter(f -> f.id().endsWith(replacement.toString())).findFirst().orElseThrow();
        assertTrue(incoming.moves().stream().noneMatch(m -> m.data().id().equals("surf")));
        assertFalse(incoming.currentEffects());
    }
    @Test void liveActiveHpWinsOverActionRequestCondition() {
        var p = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "12/100 par", "torrent", "Blastoise, L50", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), null, false);
        var own = reader.read(battle(battleId, List.of(r)), player, null).own().getFirst();
        assertEquals(75, own.hpPercent());
        assertEquals("brn", own.status());
    }
    @Test void illusionProjectionCannotMergeRealAndDisguiseSlots() {
        UUID disguised = UUID.randomUUID();
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(enemy, List.of(), List.of(new Active(shown(disguised)))))), List.of(), false);
        var result = reader.read(b, player, repository(List.of(dto(foe, shown(disguised)), dto(disguised, shown(disguised)))));
        assertEquals(2, result.enemies().size());
        for (var f : result.enemies()) {
            assertTrue(f.types().isEmpty());
            assertTrue(f.moves().isEmpty());
            assertFalse(f.active());
            assertTrue(f.notes().stream().anyMatch(s -> s.contains("Ambiguous")));
            assertTrue(f.portrait().speciesId().isBlank(), "Illusion must not leak either identity through its portrait");
        }
    }

    @Test void currentRequestTypesAreCurrentOnlyForItsConfirmedActiveUuid() {
        UUID bench = UUID.randomUUID();
        var p = new Showdown(mine, List.of("water"), List.of("fire"), List.of(), "100/100", "", "Same name", true);
        var other = new Showdown(bench, List.of("grass"), List.of("grass"), List.of(), "100/100", "", "Same name", false);
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(p, other)), null, false);
        var result = reader.read(battle(battleId, List.of(request)), player, null);
        assertEquals(MatchupSnapshot.Freshness.CURRENT, result.own().getFirst().facts().types().freshness());
        assertEquals(List.of(WATER), result.own().getFirst().facts().types().value());
        assertNotEquals(MatchupSnapshot.Freshness.CURRENT, result.own().get(1).facts().types().freshness());
    }

    @Test void activeWithoutRequestMovesCannotBorrowProfileMovesAfterTransformOrMimic() {
        var profile = new MoveProfile(mine, new ProfileMoves(List.of(new ProfileMove("surf", 15))));
        var actor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(), false);
        var fighter = reader.read(b, player, null).own().getFirst();
        assertTrue(fighter.moves().isEmpty());
        assertTrue(fighter.notes().stream().anyMatch(n -> n.contains("Transform/Mimic")));
    }

    @Test void requestSideMoveNamesAreNotAnActiveMovesetWhenMoveRequestIsMissing() {
        var p = new Showdown(mine, List.of("water"), List.of("water"), List.of("surf"), "100/100", "", "", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), null, false);
        assertTrue(reader.read(battle(battleId, List.of(r)), player, null).own().getFirst().moves().isEmpty());
    }

    @Test void exactRequestMovesReplaceProfileWithoutChangingRosterIdentity() {
        var profile = new MoveProfile(mine, new ProfileMoves(List.of(new ProfileMove("surf", 15))));
        var actor = new Actor(player, List.of(profile), List.of(new Active(shown(mine))));
        var p = new Showdown(mine, List.of("fire"), List.of("water"), List.of("surf"), "100/100", "", "", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p)),
            new Moveset(List.of(new RequestMove("flamethrower", 5, false)), false), false);
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(r), false);
        var fighter = reader.read(b, player, null).own().getFirst();
        assertEquals(player + "/" + mine, fighter.id());
        assertEquals(List.of("flamethrower"), fighter.moves().stream().map(m -> m.data().id()).toList());
        assertTrue(fighter.moves().getFirst().availabilityKnown());
    }

    @Test void ownedRequestForDetachedActiveSlotCannotAuthorizeOldMoves() {
        var p = new Showdown(mine, List.of("water"), List.of(), List.of("surf"), "100/100", "torrent", "", true);
        var r = new Request(new Active(shown(mine)), new RequestSide(List.of(p), player),
            new Moveset(List.of(new RequestMove("surf", 5, false)), false), false);
        var actor = new Actor(player, List.of(new Profile(mine, List.of("water"))), List.of(new Active(shown(UUID.randomUUID()))));
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(r), false);
        var f = reader.read(b, player, null).own().getFirst();
        assertTrue(f.moves().isEmpty());
        assertFalse(f.currentEffects());
        assertFalse(f.facts().types().known());
    }

    @Test void ownUnboundProjectionCannotExpandConfirmedSixSlotRoster() {
        var profiles = java.util.stream.IntStream.range(0, 6).mapToObj(i -> new Profile(UUID.randomUUID(), List.of("water"))).toList();
        var actor = new Actor(player, profiles, List.of(new Active(shown(mine))));
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(), false);
        var result = reader.read(b, player, null);
        assertEquals(profiles.stream().map(p -> player + "/" + p.getUuid()).toList(),
            result.own().stream().map(MatchupSnapshot.Fighter::id).toList());
    }

    @Test void benchProfileMovesRemainKnownWithoutClaimingCurrentAvailability() {
        var profile = new MoveProfile(mine, new ProfileMoves(List.of(new ProfileMove("surf", 15))));
        var actor = new Actor(player, List.of(profile), List.of());
        var b = new Battle(battleId, new Side(List.of(actor)), new Side(List.of()), List.of(), false);
        var fighter = reader.read(b, player, null).own().getFirst();
        assertEquals(List.of("surf"), fighter.moves().stream().map(m -> m.data().id()).toList());
        assertFalse(fighter.moves().getFirst().availabilityKnown());
        assertFalse(fighter.facts().types().known());
    }

    @Test void partialUnknownRequestTypesCannotAuthorizeSingleTypeMultiplier() {
        var p = new Showdown(mine, List.of("water", "unrecognized"), List.of(), List.of(), "100/100", "", "", true);
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(p)), null, false);
        assertFalse(reader.read(battle(battleId, List.of(request)), player, null).own().getFirst().facts().types().known());
    }

    @Test void activeEnemyCannotPromoteRepositoryTeraToCurrentTypes() {
        var dto = new Dto(foe, false, "", List.of(), "", new Tera(new Element("water")), shown(foe));
        var result = reader.read(battle(battleId, List.of()), player, repository(List.of(dto))).enemies().getFirst();
        assertNotEquals(MatchupSnapshot.Freshness.CURRENT, result.facts().types().freshness());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("Current defensive types unknown")));
    }

    @Test void revealUsesConfirmedRosterUuidEvenWhileOldProjectionStillDisagrees() {
        UUID disguise = UUID.randomUUID();
        var slot = new Active(shown(disguise));
        var b = new Battle(battleId, new Side(List.of(new Actor(player, List.of(), List.of()))),
            new Side(List.of(new Actor(enemy, List.of(), List.of(slot)))), List.of(), false);
        var repo = repository(List.of(dto(foe, shown(disguise)), dto(disguise, shown(disguise))));
        var before = reader.read(b, player, repo);
        slot.pokemon = new Shown(foe, "Zoroark", new Properties("zoroark", 50), Set.of(), .5f, 100, false, "");
        var after = reader.read(b, player, repo);
        assertEquals(before.enemies().stream().map(MatchupSnapshot.Fighter::id).toList(),
            after.enemies().stream().map(MatchupSnapshot.Fighter::id).toList());
        var revealed = after.enemies().getFirst();
        assertTrue(revealed.active());
        assertEquals("zoroark", revealed.portrait().speciesId());
        assertTrue(revealed.moves().isEmpty(), "Old disguised repository facts cannot bind to the reveal");
        assertFalse(revealed.facts().types().known());
        assertEquals(1, after.enemies().stream().filter(MatchupSnapshot.Fighter::active).count());
    }

    @Test void unmatchedProjectionDoesNotAppendSeventhFighterOrMergeByName() {
        var roster = java.util.stream.IntStream.range(0, 6).mapToObj(i -> dto(UUID.randomUUID(), null)).toList();
        var result = reader.read(battle(battleId, List.of()), player, repository(roster));
        assertEquals(6, result.enemies().size());
        assertTrue(result.enemies().stream().allMatch(f -> f.name().equals("Unknown")));
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("binding unknown")));
    }

    private List<MatchupSnapshot.Move> requestMoves(String... ids) {
        var pokemon = new Showdown(mine, List.of("fire"), List.of("fire"), List.of(ids),
            "100/100", "blaze", "Charizard, L50", true);
        var moves = Arrays.stream(ids).map(id -> new RequestMove(id, 10, false)).toList();
        var request = new Request(new Active(shown(mine)), new RequestSide(List.of(pokemon)),
            new Moveset(moves, false), false);
        return reader.read(battle(battleId, List.of(request)), player, repository(List.of(dto(foe, shown(foe)))))
            .own().getFirst().moves();
    }

    private static MatchupSnapshot.Fighter currentTarget(String name, PokemonType type) {
        var facts = new MatchupSnapshot.Facts(MatchupSnapshot.Fact.unknown(),
            new MatchupSnapshot.Fact<>(List.of(type), MatchupSnapshot.Freshness.CURRENT, "Fixture current type"),
            MatchupSnapshot.Fact.unknown(), MatchupSnapshot.Fact.unknown(), MatchupSnapshot.Fact.unknown(),
            MatchupSnapshot.Fact.unknown());
        return new MatchupSnapshot.Fighter(name.toLowerCase(Locale.ROOT), name, List.of(type), List.of(type), List.of(),
            50, 100, false, false, "", "", false, "", List.of(), MatchupSnapshot.Portrait.UNKNOWN,
            null, false, facts);
    }
}

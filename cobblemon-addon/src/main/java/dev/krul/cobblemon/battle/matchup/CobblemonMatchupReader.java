package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.*;
import net.minecraft.item.ItemStack;
import java.util.*;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.*;
import static dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;

/** Read-only optional boundary; never reads PC or sends a battle action. */
public final class CobblemonMatchupReader {
    public interface Catalog {
        BattleMove move(String id);
        List<PokemonType> types(String species, Set<String> aspects);
        String moveId(Object text);
        String text(Object value);
        default int baseSpeed(String species, Set<String> aspects) { return -1; }
        default List<String> possibleAttacks(String species, Set<String> aspects, int level) { return List.of(); }
    }
    private final Catalog catalog;
    public CobblemonMatchupReader(Catalog catalog) { this.catalog = catalog; }
    public MatchupSnapshot read(Object battle, UUID player, Object repository) {
        if (!(get(battle, "getBattleId") instanceof UUID battleId)) return unavailable(null, "No active battle");
        if (player == null || yes(get(battle, "getSpectating"))) return unavailable(battleId, "Spectator: own team unavailable");
        Object side1 = get(battle, "getSide1"), side2 = get(battle, "getSide2");
        Object ownSide = actor(side1, player) != null ? side1 : actor(side2, player) != null ? side2 : null;
        if (ownSide == null) return unavailable(battleId, "No participating player actor");
        Object ownActor = actor(ownSide, player);
        Object enemySide = ownSide == side1 ? side2 : side1;
        List<String> notes = new ArrayList<>();
        Object info = map(get(repository, "getBattles")).get(battleId);
        // The repository is optional. Without a current-battle entry do not reuse its global actor maps.
        Map<?, ?> roster = info == null ? Map.of() : map(get(repository, "getActors"));
        if (info == null) notes.add("Waiting for battle information; only currently visible enemies shown.");
        if (list(get(ownSide, "getActors")).size() > 1 || list(get(ownActor, "getActivePokemon")).size() > 1) {
            notes.add("Multi battle: per-target type chart only; adjacency and switch legality not evaluated.");
        }
        List<Object> requests = new ArrayList<>();
        for (Object request : list(get(battle, "getPendingActionRequests"))) {
            // Answered requests survive checkForFinishedChoosing, and their active slot is mutable.
            if (!yes(get(battle, "getMustChoose")) || get(request, "getResponse") != null) continue;
            Object side = get(request, "getSide");
            Object owner = get(side, "getName");
            Object activeId = get(get(request, "getActivePokemon"), "getBattlePokemon");
            List<?> originalActive = list(get(side, "getPokemon")).stream().filter(p -> yes(get(p, "getActive"))).toList();
            // An immutable request-side identity must agree with the live slot. Multiple active entries
            // cannot prove that binding here: retain profile moves, but do not guess PP/ability in doubles.
            if (originalActive.size() != 1 || !Objects.equals(get(originalActive.getFirst(), "getUuid"), get(activeId, "getUuid"))) continue;
            if (!active(ownActor).containsKey(get(activeId, "getUuid"))) continue;
            if (player.equals(owner) || (owner == null && active(ownActor).containsKey(get(activeId, "getUuid")))) requests.add(request);
        }
        List<Fighter> own = own(ownActor, requests);
        List<Fighter> enemies = new ArrayList<>();
        for (Object enemyActor : list(get(enemySide, "getActors"))) {
            Object actorId = get(enemyActor, "getUuid");
            String actorName = catalog.text(get(enemyActor, "getDisplayName"));
            if (actorName.isBlank()) actorName = string(get(enemyActor, "getShowdownId"));
            if (!actorName.isBlank()) notes.add("Enemy actor=" + actorName + " id=" + actorId
                + " type=" + string(get(get(enemyActor, "getType"), "name")));
            Map<Object, Object> active = active(enemyActor);
            Set<Object> seen = new HashSet<>();
            Set<Object> ambiguous = new HashSet<>();
            Set<Object> staleProjection = new HashSet<>(), projectionTargets = new HashSet<>();
            for (Object dto : list(roster.get(actorId))) {
                Object id = get(dto, "getUuid"), projection = get(dto, "getActiveBattlePokemonDTO");
                Object projectedId = get(projection, "getUuid");
                if (projection != null && !Objects.equals(id, projectedId)) {
                    ambiguous.add(id);
                    ambiguous.add(projectedId);
                    staleProjection.add(id);
                    projectionTargets.add(projectedId);
                }
            }
            // BattleReplacePokemonHandler installs realPokemon.uuid in the live slot. An exact
            // roster UUID can therefore recover while its repository projection is still stale.
            // Never use that stale DTO's moves/ability/Tera as facts about the revealed Pokemon.
            for (Object id : staleProjection) if (active.containsKey(id) && !projectionTargets.contains(id)) ambiguous.remove(id);
            for (Object dto : list(roster.get(actorId))) {
                Object id = get(dto, "getUuid");
                if (id == null || !seen.add(id)) continue;
                if (ambiguous.contains(id)) {
                    enemies.add(ambiguousEnemy(actorId, id));
                    continue;
                }
                Object shown = active.get(id);
                if (shown == null) shown = get(dto, "getActiveBattlePokemonDTO");
                enemies.add(enemy(actorId, id, shown, staleProjection.contains(id) ? null : dto, active.containsKey(id)));
            }
            for (var entry : active.entrySet()) if (seen.add(entry.getKey())) {
                if (ambiguous.contains(entry.getKey())) continue; // Disguise projection is not an extra roster slot.
                if (!list(roster.get(actorId)).isEmpty()) {
                    notes.add("Active projection / roster binding unknown; no extra fighter or name-based merge.");
                    continue;
                }
                enemies.add(enemy(actorId, entry.getKey(), entry.getValue(), null, true));
            }
        }
        if (own.isEmpty()) notes.add("Own battle roster not received yet.");
        notes.add("Current opponent types unavailable in pinned client projections; catalog types never prove battle types.");
        notes.add("Known moves only; Transform/Mimic require an active move request. Field/gimmick effects are not simulated.");
        return new MatchupSnapshot(battleId, (int) number(get(info, "getTurn"), 0), own, enemies, notes);
    }

    private List<Fighter> own(Object actor, List<Object> requests) {
        Map<Object, Object> profiles = new LinkedHashMap<>();
        for (Object pokemon : list(get(actor, "getPokemon"))) profiles.put(get(pokemon, "getUuid"), pokemon);
        Map<Object, Object> showdown = new LinkedHashMap<>(), requestById = new HashMap<>();
        for (Object request : requests) {
            for (Object p : list(get(get(request, "getSide"), "getPokemon"))) showdown.put(get(p, "getUuid"), p);
            Object id = get(get(get(request, "getActivePokemon"), "getBattlePokemon"), "getUuid");
            if (id != null) requestById.put(id, request);
        }
        Map<Object, Object> active = active(actor);
        Set<Object> ids = new LinkedHashSet<>(profiles.keySet());
        ids.addAll(showdown.keySet());
        ids.remove(null);
        // A projected UUID outside the confirmed roster is not a seventh Pokemon.
        if (ids.isEmpty()) ids.addAll(active.keySet());
        List<Fighter> result = new ArrayList<>();
        for (Object id : ids) {
            if (id == null) continue;
            Object profile = profiles.get(id), p = showdown.get(id), request = requestById.get(id);
            Object shown = active.get(id);
            List<PokemonType> base = types(get(p, "getBaseTypes"));
            if (base.isEmpty()) base = types(get(profile, "getTypes"));
            List<PokemonType> current = types(get(p, "getTypes"));
            List<Move> moves = new ArrayList<>();
            Object moveset = get(request, "getMoveSet");
            if (moveset != null) {
                for (Object move : list(get(moveset, "getMoves"))) moves.add(move(string(get(move, "getId")),
                    (int) number(get(move, "getPp"), -1), yes(get(move, "getDisabled")), true));
            } else if (active.containsKey(id)) {
                // Pokemon/ShowdownSide move names can predate Transform or Mimic. Only
                // the exact active moveset request proves the active attack set.
            } else if (p != null) {
                for (Object move : list(get(p, "getMoves"))) moves.add(move(string(move), -1, false, false));
            } else {
                for (Object move : list(get(get(profile, "getMoveSet"), "getMoves"))) {
                    if (move != null) moves.add(move(string(get(move, "getName")), (int) number(get(move, "getCurrentPp"), -1), false, false));
                }
            }
            String condition = string(get(p, "getCondition"));
            double hp = shown == null ? -1 : shownHp(shown);
            if (hp < 0) hp = conditionHp(condition);
            if (hp < 0) hp = ratio(get(profile, "getCurrentHealth"), get(profile, "getMaxHealth"));
            int level = (int) number(get(profile, "getLevel"), number(get(get(shown, "getProperties"), "getLevel"), 0));
            String details = string(get(p, "getDetails"));
            for (String token : details.split(",")) if (token.strip().matches("L[0-9]+")) level = Integer.parseInt(token.strip().substring(1));
            String name = catalog.text(get(profile, "getDisplayName"));
            if (name.isBlank()) name = details.isBlank() ? catalog.text(get(shown, "getDisplayName")) : details.split(",")[0];
            if (name.isBlank()) name = "Own Pokemon";
            List<String> notes = new ArrayList<>();
            if (current.isEmpty()) notes.add("Current defensive types unknown; profile base types do not prove Tera/transform state.");
            if (yes(get(moveset, "getTrapped"))) notes.add("Active request: trapped; switching may be blocked.");
            if (yes(get(request, "getForceSwitch"))) notes.add("Active request: forced switch.");
            if (request == null) notes.add("Bench / between requests: move availability not confirmed.");
            if (active.containsKey(id) && moveset == null) notes.add("Current moves unknown: Transform/Mimic cannot be resolved from profile moves.");
            String ability = string(get(p, "getAbility"));
            String status = shown == null ? (condition.contains(" ") ? condition.substring(condition.indexOf(' ') + 1) : "") : status(shown);
            // Pinned ClientBattlePokemon, ShowdownPokemon and SingleActionRequest have no
            // getTerastallized(). Pokemon.getTeraType() is profile metadata only.
            PokemonType storedTera = parseTeraType(get(profile, "getTeraType"));
            // Only the current action request proves a current ability. Profile ability may predate Trace/transform.
            result.add(new Fighter(get(actor, "getUuid") + "/" + id, name, current, base, moves, level, hp,
                active.containsKey(id), hp == 0, ability, "", request != null && !ability.isBlank(), status, notes,
                portrait(shown, profile), storedTera, false,
                ownFacts(shown, profile, p, condition, current, request != null && shown != null)));
        }
        return result;
    }

    private Fighter enemy(Object actor, Object id, Object shown, Object dto, boolean active) {
        if (shown == null) return new Fighter(actor + "/" + id, "Unknown", List.of(), List.of(), List.of(),
            0, -1, false, yes(get(dto, "getFainted")), "", "", false, "", List.of("Unrevealed slot; no inferred species or moves."));
        Object properties = get(shown, "getProperties");
        String species = string(get(properties, "getSpecies"));
        Object aspects = get(shown, "getAspects");
        if (aspects == null) aspects = get(get(shown, "getState"), "getCurrentAspects");
        Set<String> aspectNames = new HashSet<>();
        for (Object aspect : list(aspects)) aspectNames.add(string(aspect));
        List<PokemonType> base = catalog.types(species, aspectNames);
        TeraInfo reportedTera = activeTera(get(dto, "getTerastallized"));
        // ClientBattlePokemon is the authoritative live projection for an active slot.
        // Its Species object contains the resolved form types, including battle-time form data.
        // Do not replace this with catalog/base species data: that would hide transforms and
        // make every ordinary active opponent appear as "?" in the matrix.
        List<PokemonType> current = types(get(get(shown, "getSpecies"), "getTypes"));
        List<String> notes = new ArrayList<>();
        List<Move> moves = new ArrayList<>();
        for (Object move : list(get(dto, "getMoves"))) {
            String moveId = catalog.moveId(get(move, "getMove"));
            if (!moveId.isBlank()) moves.add(move(moveId, -1, false, false));
        }
        String name = catalog.text(get(shown, "getDisplayName"));
        if (name.isBlank()) name = species.isBlank() ? "Unknown" : species;
        double hp = shownHp(shown);
        String status = status(shown);
        int level = (int) number(get(properties, "getLevel"), 0);
        int baseSpeed = catalog.baseSpeed(species, aspectNames);
        if (baseSpeed >= 0) notes.add("Base Speed: " + baseSpeed + " (species baseline; not battle Speed).");
        List<String> possible = catalog.possibleAttacks(species, aspectNames, level);
        if (!possible.isEmpty()) notes.add("Possible attacks (learnset examples, not this Pokemon's known set): "
            + String.join(", ", possible));
        notes.add(active ? "Visible active opponent; moves/ability are server-revealed facts." : "Benched opponent; HP/types are last reported.");
        if (current.isEmpty()) notes.add("Current defensive types unknown: client projection carries no resolved battle types; repository Tera is last observed.");
        Object buffs = get(dto, "getBuffs");
        if (buffs instanceof Map<?, ?> m && !m.isEmpty()) notes.add("Reported buffs: " + m);
        return new Fighter(actor + "/" + id, name, current, base, moves, (int) number(get(properties, "getLevel"), 0), hp,
            active, yes(get(dto, "getFainted")) || hp == 0, string(get(dto, "getAbility")), revealedItem(get(dto, "getHeldItem")),
            false, status, notes, new Portrait(species, aspectNames), reportedTera.type(), reportedTera.active(),
            enemyFacts(shown, dto, active, reportedTera, current));
    }

    private Facts ownFacts(Object shown, Object profile, Object requestPokemon, String condition,
                           List<PokemonType> current, boolean currentRequest) {
        Fact<Health> health = health(shown, true, Freshness.CURRENT, "Visible battle HP");
        if (!health.known()) {
            String[] parts = condition.split(" ")[0].split("/");
            if (parts.length == 2) try {
                health = flatHealth(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), true,
                    Freshness.LAST_OBSERVED, "Action request HP");
            } catch (NumberFormatException ignored) { /* unknown */ }
        }
        if (!health.known()) health = flatHealth(number(get(profile, "getCurrentHealth"), -1),
            number(get(profile, "getMaxHealth"), -1), true, Freshness.PROFILE, "Own Pokemon profile");
        Fact<List<PokemonType>> typeFact = current.isEmpty() ? Fact.unknown()
            : new Fact<>(current, currentRequest ? Freshness.CURRENT : Freshness.LAST_OBSERVED,
                "ShowdownPokemon.types from UUID-bound unanswered action request");
        List<String> changes = new ArrayList<>();
        changes.addAll(stages(get(shown, "getStatChanges")));
        Fact<String> status = statusFact(shown, Freshness.CURRENT);
        if (!status.known() && !condition.isBlank()) {
            String reported = condition.contains(" ") ? condition.substring(condition.indexOf(' ') + 1) : "?";
            status = textFact(reported, Freshness.LAST_OBSERVED, "Action request condition");
        }
        return new Facts(health, typeFact, status, changes.isEmpty() ? Fact.unknown()
            : new Fact<>(changes, Freshness.CURRENT, "Visible battle stages; other changes unknown"),
            textFact(string(get(requestPokemon, "getAbility")), Freshness.LAST_OBSERVED, "Action request ability"),
            textFact(string(get(requestPokemon, "getItem")), Freshness.LAST_OBSERVED, "Action request item"));
    }

    private Facts enemyFacts(Object shown, Object dto, boolean active, TeraInfo reportedTera,
                             List<PokemonType> currentTypes) {
        Freshness freshness = active ? Freshness.CURRENT : Freshness.LAST_OBSERVED;
        Fact<List<PokemonType>> current = currentTypes.isEmpty()
            ? reportedTera.type() == null ? Fact.unknown()
                : new Fact<>(List.of(reportedTera.type()), Freshness.LAST_OBSERVED,
                    "Reported Terastallization type; current battle typing unavailable")
            : new Fact<>(currentTypes, Freshness.CURRENT, "ClientBattlePokemon.getSpecies().getTypes()");
        // 'shown' is the live ClientBattlePokemon, or the last ActiveBattlePokemonDTO on the bench.
        // BattlePokemonDTO.getBuffs() contains multipliers, not stages; never invert or cast it.
        List<String> changes = new ArrayList<>(stages(get(shown, "getStatChanges")));
        if (reportedTera.active()) changes.add("Terastallized" + (reportedTera.type() == null ? " (type ?)"
            : " as " + reportedTera.type().label()));
        return new Facts(health(shown, false, freshness, active ? "Visible opponent HP" : "Last opponent projection"),
            current, statusFact(shown, freshness), changes.isEmpty() ? Fact.unknown()
                : new Fact<>(changes, reportedTera.active() ? Freshness.LAST_OBSERVED : freshness,
                    "Reported battle stages / Terastallization; other changes unknown"),
            textFact(string(get(dto, "getAbility")), Freshness.LAST_OBSERVED, "Revealed ability"),
            textFact(revealedItem(get(dto, "getHeldItem")), Freshness.LAST_OBSERVED, "Revealed item"));
    }

    private String revealedItem(Object heldItem) {
        // Empty may be the default/unrevealed stack; it does not prove an item was removed.
        // Link the Minecraft call so Loom remaps isEmpty to method_7960 in production.
        if (heldItem == null || heldItem instanceof ItemStack stack && stack.isEmpty()) return "";
        return catalog.text(heldItem);
    }

    private Fact<String> statusFact(Object shown, Freshness freshness) {
        // Null is also returned for a missing getter: it cannot establish a healthy status.
        return textFact(status(shown), freshness, "Reported battle status");
    }

    private static Fact<String> textFact(String value, Freshness freshness, String source) {
        return value == null || value.isBlank() || value.equals("?") ? Fact.unknown() : new Fact<>(value, freshness, source);
    }

    private static List<String> stages(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return List.of();
        Map<String, String> names = Map.of("atk", "Atk", "def", "Def", "spa", "SpA", "spd", "SpD",
            "spe", "Spe", "accuracy", "Accuracy", "evasion", "Evasion");
        Map<String, Integer> reported = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (var entry : map.entrySet()) {
            // Stat.getShowdownId() is the public Cobblemon contract, including battle-only stats.
            String id = string(get(entry.getKey(), "getShowdownId"));
            if (!names.containsKey(id) || !(entry.getValue() instanceof Integer stage) || stage < -6 || stage > 6) continue;
            Integer previous = reported.putIfAbsent(id, stage);
            if (previous != null && previous.intValue() != stage) ambiguous.add(id);
        }
        List<String> result = new ArrayList<>();
        for (String id : List.of("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")) {
            Integer value = reported.get(id);
            if (value == null || ambiguous.contains(id)) continue;
            result.add(names.get(id) + " " + (value > 0 ? "+" : "") + value);
        }
        return List.copyOf(result);
    }

    private static Fact<Health> health(Object shown, boolean own, Freshness freshness, String source) {
        Object flat = get(shown, "isFlatHp");
        if (!(flat instanceof Boolean)) flat = get(shown, "isHpFlat");
        double hp = number(get(shown, "getHpValue"), -1);
        if (!(flat instanceof Boolean) || !Double.isFinite(hp) || hp < 0) return Fact.unknown();
        if (Boolean.TRUE.equals(flat)) return flatHealth(hp, number(get(shown, "getMaxHp"), -1), own, freshness, source);
        return hp > 1 ? Fact.unknown() : new Fact<>(new Health(-1, -1, hp * 100), freshness, source);
    }

    private static Fact<Health> flatHealth(double hp, double max, boolean own, Freshness freshness, String source) {
        if (!Double.isFinite(hp) || !Double.isFinite(max) || hp < 0 || max <= 0 || hp > max) return Fact.unknown();
        boolean exact = own && hp == Math.rint(hp) && max == Math.rint(max) && max <= Integer.MAX_VALUE;
        return new Fact<>(new Health(exact ? (int) hp : -1, exact ? (int) max : -1, hp * 100 / max), freshness, source);
    }

    private record TeraInfo(PokemonType type, boolean active) {}

    private static TeraInfo activeTera(Object... values) {
        for (Object raw : values) {
            TeraInfo info = tera(raw);
            if (info.active()) return info;
        }
        return new TeraInfo(null, false);
    }

    private static PokemonType parseTeraType(Object raw) {
        if (raw == null || raw instanceof Boolean) return null;
        Object type = get(raw, "getTeraType");
        if (type == null) type = get(raw, "getType");
        if (type == null) type = raw;
        String id = string(get(type, "getShowdownId"));
        if (id.isBlank()) id = string(get(type, "getId"));
        if (id.isBlank()) id = string(get(type, "getName"));
        if (id.isBlank()) id = string(type);
        return PokemonType.fromId(id).orElse(null);
    }

    private static TeraInfo tera(Object raw) {
        if (raw == null || Boolean.FALSE.equals(raw)) return new TeraInfo(null, false);
        if (raw instanceof Boolean) return new TeraInfo(null, true);
        PokemonType type = parseTeraType(raw);
        return new TeraInfo(type, type != null);
    }

    private Portrait portrait(Object shown, Object profile) {
        String species = string(get(get(shown, "getProperties"), "getSpecies"));
        Object aspects = get(shown, "getAspects");
        if (aspects == null) aspects = get(get(shown, "getState"), "getCurrentAspects");
        if (species.isBlank()) {
            species = string(get(get(profile, "getSpecies"), "getResourceIdentifier"));
            aspects = get(profile, "getAspects");
        }
        if (species.isBlank()) return Portrait.UNKNOWN;
        Set<String> names = new HashSet<>();
        for (Object aspect : list(aspects)) names.add(string(aspect));
        return new Portrait(species, names);
    }

    private Fighter ambiguousEnemy(Object actor, Object id) {
        return new Fighter(actor + "/" + id, "Unknown", List.of(), List.of(), List.of(), 0, -1,
            false, false, "", "", false, "", List.of("Ambiguous identity / Illusion: projection does not match roster UUID; facts withheld."));
    }
    private String status(Object shown) {
        String name = string(get(get(shown, "getStatus"), "getName"));
        return name.isBlank() ? catalog.text(get(shown, "getStatus")) : name;
    }

    private Move move(String id, int pp, boolean disabled, boolean known) {
        BattleMove data = catalog.move(id);
        if (data == null) data = new BattleMove(id, id, null, MoveCategory.UNKNOWN, 0);
        return new Move(data, pp, disabled, known, SPECIAL_MOVES.contains(id) || (data.category() != MoveCategory.STATUS && data.power() <= 1));
    }
    private static final Set<String> SPECIAL_MOVES = Set.of("seismictoss", "nightshade", "dragonrage", "sonicboom", "superfang", "naturesmadness", "ruination",
        "endeavor", "counter", "mirrorcoat", "metalburst", "bide", "fissure", "guillotine", "horndrill", "sheercold", "finalgambit",
        "terablast", "terastarstorm", "weatherball", "judgment", "technoblast", "multiattack", "hiddenpower", "revelationdance", "ivycudgel", "flyingpress", "freezedry", "thousandarrows",
        "aurawheel", "ragingbull", "terrainpulse", "struggle");
    private static Object actor(Object side, UUID id) { return list(get(side, "getActors")).stream().filter(a -> id.equals(get(a, "getUuid"))).findFirst().orElse(null); }
    private static Map<Object, Object> active(Object actor) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Object slot : list(get(actor, "getActivePokemon"))) {
            Object pokemon = get(slot, "getBattlePokemon"), id = get(pokemon, "getUuid");
            if (id != null) result.put(id, pokemon);
        }
        return result;
    }
    private List<PokemonType> types(Object raw) {
        List<PokemonType> result = new ArrayList<>();
        Iterable<?> values = raw instanceof Iterable<?> iterable ? iterable : List.of();
        for (Object value : values) {
            var type = PokemonType.fromId(value instanceof String s ? s : string(get(value, "getName")));
            if (type.isEmpty()) return List.of(); // A partial type list is not an exact defensive type set.
            if (!result.contains(type.get())) result.add(type.get());
        }
        return result;
    }
    private static double shownHp(Object shown) {
        Fact<Health> fact = health(shown, false, Freshness.CURRENT, "Visible HP");
        return fact.known() ? fact.value().percent() : -1;
    }
    private static double ratio(Object hp, Object max) {
        Fact<Health> fact = flatHealth(number(hp, -1), number(max, -1), false, Freshness.PROFILE, "HP");
        return fact.known() ? fact.value().percent() : -1;
    }
    private static double conditionHp(String condition) {
        if (condition.contains("fnt")) return 0;
        try { String[] values = condition.split(" ")[0].split("/"); return values.length == 2 ? ratio(Double.parseDouble(values[0]), Double.parseDouble(values[1])) : -1; }
        catch (NumberFormatException ignored) { return -1; }
    }
    private static MatchupSnapshot unavailable(UUID id, String reason) { return new MatchupSnapshot(id, 0, List.of(), List.of(), List.of(reason)); }
}

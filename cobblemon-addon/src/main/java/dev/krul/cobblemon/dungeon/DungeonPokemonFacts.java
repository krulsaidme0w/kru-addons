package dev.krul.cobblemon.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.entity.Entity;
import dev.krul.cobblemon.discovery.PokemonObservation;
import dev.krul.cobblemon.discovery.DiscoveryRarityIndex;
import dev.krul.cobblemon.discovery.DiscoveryRarityRuntime;
import dev.krul.cobblemon.discovery.SpawnRarity;
import dev.krul.cobblemon.species.SpeciesFacts;

/** Builds the deliberately small, machine-readable Pokémon fact set for the probe. */
public final class DungeonPokemonFacts {
    public static final Set<String> ALLOWED_KEYS = Set.of(
        "species", "form", "level", "shiny", "radiant", "aspects", "x", "y", "z", "rarity", "speciesClass", "ownerUuid", "wild");

    private DungeonPokemonFacts() {
    }

    public static Map<String, String> facts(DungeonWorldProbe.PokemonData data) {
        Map<String, String> facts = new LinkedHashMap<>();
        put(facts, "species", data.species());
        put(facts, "form", data.form());
        if (data.level() > 0) facts.put("level", Integer.toString(data.level()));
        if (data.shiny() != null) facts.put("shiny", data.shiny().toString());
        if (data.radiant() != null) facts.put("radiant", data.radiant().toString());
        put(facts, "aspects", DungeonWorldProbe.bounded(String.join(",", data.aspects())));
        data.extraFacts().forEach((key, value) -> {
            if (ALLOWED_KEYS.contains(key) && value != null) facts.put(key, DungeonWorldProbe.bounded(value));
        });
        facts.put("x", Double.toString(data.x()));
        facts.put("y", Double.toString(data.y()));
        facts.put("z", Double.toString(data.z()));
        return Map.copyOf(facts);
    }

    /** Best-effort compatibility boundary; unavailable fields stay absent, never guessed. */
    public static Optional<DungeonWorldProbe.PokemonData> fromEntity(Entity entity, PokemonObservation known,
                                                                       DiscoveryRarityIndex rarityIndex) {
        try {
            Object pokemon = entity.getClass().getMethod("getPokemon").invoke(entity);
            Object species = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            String form = "";
            Object formObject = optional(pokemon, "getForm");
            if (formObject != null) {
                String namedForm = formName(formObject);
                if (!namedForm.isBlank()) form = namedForm;
            }
            int level = number(optional(pokemon, "getLevel"));
            List<String> aspects = strings(optional(pokemon, "getAspects"));
            Map<String, String> extra = new LinkedHashMap<>();
            List<String> labels = strings(optional(species, "getLabels"));
            SpeciesFacts facts = SpeciesFacts.from(known.species(), -1, List.of(), java.util.Set.of(), java.util.Set.copyOf(labels));
            if (!facts.classes().isEmpty()) extra.put("speciesClass", facts.classes().toString());
            // Missing ownership API is unknown; original trainer is not current ownership.
            try {
                Method ownerGetter = pokemon.getClass().getMethod("getOwnerUUID");
                Object owner = ownerGetter.invoke(pokemon);
                extra.put("wild", Boolean.toString(owner == null));
                if (owner != null) extra.put("ownerUuid", owner.toString());
            } catch (NoSuchMethodException ignored) {}
            return Optional.of(new DungeonWorldProbe.PokemonData(known.species(), form, level, known.shiny(), known.radiant(), aspects,
                entity.getId(), entity.getUuidAsString(), entity.getClass().getName(), entity.getX(), entity.getY(), entity.getZ(), extra));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Object optional(Object receiver, String name) throws ReflectiveOperationException {
        if (receiver == null) return null;
        try { return receiver.getClass().getMethod(name).invoke(receiver); }
        catch (NoSuchMethodException ignored) { return null; }
    }
    static String formName(Object form) {
        try {
            Object value = optional(form, "getName");
            return value == null ? "" : text(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : 0; }
    private static List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false).filter(v -> v != null)
            .map(Object::toString).limit(16).toList();
    }

    private static void put(Map<String, String> facts, String key, String value) {
        if (value != null && !value.isBlank()) facts.put(key, DungeonWorldProbe.bounded(value));
    }
}

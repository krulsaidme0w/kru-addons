package dev.krul.cobblemon.observation;

import dev.krul.cobblemon.species.SpeciesClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.*;
import static dev.krul.cobblemon.observation.FactValue.*;

/** Read-only optional Cobblemon boundary. Each getter failure affects only its own fact. */
public final class PokemonFactReader {
    private static final String POKEMON_ENTITY = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final int MAX_TOKENS = 64;
    private PokemonFactReader() {}

    public static final class RegistryCache {
        private final Map<Object, Map<String, FactValue>> entries = new IdentityHashMap<>();
        public void clear() { entries.clear(); }
        private Map<String, FactValue> read(Object species) {
            Map<String, FactValue> existing = entries.get(species);
            if (existing != null) return existing;
            Map<String, FactValue> facts = new LinkedHashMap<>();
            PokemonFactReader.read(facts, "referenceTypes", Source.REGISTRY_REFERENCE, () -> tokens(call(species, "getTypes"), true, false));
            PokemonFactReader.read(facts, "speciesClass", Source.REGISTRY_REFERENCE, () -> tokens(call(species, "getLabels"), false, true));
            Map<String, FactValue> result = Map.copyOf(facts);
            if (entries.size() >= 2048) entries.clear();
            entries.put(species, result);
            return result;
        }
    }
    static Map<String, FactValue> readEntityProjection(Object entity, RegistryCache cache) {
        Object pokemon;
        try { pokemon = call(entity, "getPokemon"); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            Map<String, FactValue> failed = new LinkedHashMap<>(readPokemon(null, cache));
            for (String key : List.of("species", "pokemonUuid", "form", "gender", "shiny", "aspects", "referenceTypes", "speciesClass")) {
                FactValue fact = failed.get(key);
                failed.put(key, unavailable(fact.type(), fact.source(), Availability.READER_UNSUPPORTED));
            }
            return Map.copyOf(failed);
        }
        return readPokemon(pokemon, cache);
    }

    /** Call on the client thread for a live entity; scope tokens are managed by the caller. */
    public static Optional<ObservationSnapshot> read(Entity entity, ObservationSnapshot.Scope scope, long seenAt) {
        return read(entity, scope, seenAt, new RegistryCache());
    }
    public static Optional<ObservationSnapshot> read(Entity entity, ObservationSnapshot.Scope scope, long seenAt, RegistryCache cache) {
        if (entity == null || !isPokemonEntity(entity.getClass())) return Optional.empty();
        Map<String, FactValue> facts = new LinkedHashMap<>(readEntityProjection(entity, cache));
        read(facts, "kind", Source.LIVE, () -> ObservationSnapshot.Kind.POKEMON.name());
        read(facts, "uuid", Source.LIVE, () -> entity.getUuid().toString());
        read(facts, "dimension", Source.LIVE, () -> entity.getWorld().getRegistryKey().getValue().toString());
        read(facts, "registry_id", Source.LIVE, () -> Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        read(facts, "name", Source.LIVE, () -> entity.getName().getString());
        read(facts, "x", Source.LIVE, entity::getX);
        read(facts, "y", Source.LIVE, entity::getY);
        read(facts, "z", Source.LIVE, entity::getZ);
        // SpawnPokemonPacket applies ownerId to TameableEntity, not Pokemon.storeCoordinates.
        if (entity instanceof TameableEntity tameable) {
            try {
                UUID owner = tameable.getOwnerUuid();
                facts.put("wild", known(Type.BOOLEAN, Source.LIVE, owner == null));
                facts.put("ownerUuid", owner == null ? unavailable(Type.STRING, Source.LIVE, Availability.NOT_SENT)
                    : known(Type.STRING, Source.LIVE, owner.toString()));
            } catch (RuntimeException | LinkageError unavailable) {
                // Retain explicit READER_UNSUPPORTED from readPokemon; never infer wild on failure.
            }
        }
        read(facts, "level", Source.LIVE, () -> trackedLevel(entity));
        return Optional.of(new ObservationSnapshot(ObservationSnapshot.Identity.entity(scope, entity.getUuid()), seenAt, facts));
    }

    /**
     * Reads the synchronized appearance projection, plus explicitly marked registry reference facts.
     * A bare Pokemon carries no reliable entity ownership or label level. Private defaults are not read.
     */
    public static Map<String, FactValue> readPokemon(Object pokemon) {
        return readPokemon(pokemon, new RegistryCache());
    }
    public static Map<String, FactValue> readPokemon(Object pokemon, RegistryCache cache) {
        Map<String, FactValue> facts = new LinkedHashMap<>();
        read(facts, "species", Source.LIVE, () -> identifier(call(call(pokemon, "getSpecies"), "getResourceIdentifier")));
        read(facts, "pokemonUuid", Source.LIVE, () -> uuid(call(pokemon, "getUuid")));
        read(facts, "form", Source.LIVE, () -> nonempty(call(call(pokemon, "getForm"), "getName")));
        read(facts, "gender", Source.LIVE, () -> gender(call(pokemon, "getGender")));
        read(facts, "shiny", Source.LIVE, () -> call(pokemon, "getShiny"));
        read(facts, "aspects", Source.LIVE, () -> tokens(call(pokemon, "getAspects"), false, false));
        try { facts.putAll(cache.read(call(pokemon, "getSpecies"))); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            for (String key : List.of("referenceTypes", "speciesClass"))
                facts.put(key, unavailable(type(key), Source.REGISTRY_REFERENCE, Availability.READER_UNSUPPORTED));
        }
        for (String key : List.of("level", "ivs", "evs", "nature", "ability", "moves", "heldItem", "hp"))
            facts.put(key, unavailable(type(key), Source.LIVE, Availability.NOT_SENT));
        for (String key : List.of("wild", "ownerUuid"))
            facts.put(key, unavailable(type(key), Source.LIVE, Availability.READER_UNSUPPORTED));
        return Collections.unmodifiableMap(facts);
    }

    private static boolean isPokemonEntity(Class<?> type) {
        for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass())
            if (candidate.getName().equals(POKEMON_ENTITY)) return true;
        return false;
    }

    private static Object trackedLevel(Entity entity) throws ReflectiveOperationException {
        Object key = entity.getClass().getMethod("getLABEL_LEVEL").invoke(null);
        if (!(key instanceof TrackedData<?> tracked)) throw new IllegalArgumentException("Invalid LABEL_LEVEL key");
        Object value = entity.getDataTracker().get(tracked);
        // -1 is Cobblemon's hidden/unsent label level, not a numerical fact.
        if (value instanceof Integer level && level > 0) return level;
        if (value instanceof Integer) return null;
        throw new IllegalArgumentException("Invalid LABEL_LEVEL value");
    }

    @FunctionalInterface
    private interface Getter { Object get() throws ReflectiveOperationException; }

    private static void read(Map<String, FactValue> facts, String key, Source source, Getter getter) {
        FactValue value;
        try {
            Object raw = getter.get();
            value = raw == null ? unavailable(type(key), source, Availability.NOT_SENT) : known(type(key), source, raw);
        } catch (IllegalArgumentException invalid) {
            value = unavailable(type(key), source, Availability.INVALID);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            value = unavailable(type(key), source, Availability.READER_UNSUPPORTED);
        }
        facts.put(key, value);
    }

    private static Type type(String key) { return FactSchema.field(key).orElseThrow().type(); }

    private static Object call(Object receiver, String getter) throws ReflectiveOperationException {
        if (receiver == null) return null;
        // Only public optional-mod methods. Minecraft methods use typed calls so Loom remaps them.
        return receiver.getClass().getMethod(getter).invoke(receiver);
    }

    private static String identifier(Object value) {
        if (value == null) return null;
        if (value instanceof Identifier id) return id.toString();
        return nonempty(value);
    }

    private static String nonempty(Object value) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected text");
        return text.isBlank() ? null : text;
    }

    private static String uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid.toString();
        throw new IllegalArgumentException("Expected UUID");
    }

    private static String gender(Object value) {
        if (value == null) return null;
        String name = value instanceof Enum<?> gender ? gender.name() : nonempty(value);
        if (name == null) return null;
        return name.toLowerCase(Locale.ROOT);
    }

    private static String tokens(Object raw, boolean types, boolean classes) throws ReflectiveOperationException {
        if (raw == null) return null;
        if (!(raw instanceof Iterable<?> values)) throw new IllegalArgumentException("Expected tokens");
        // Preserve the registry's primary/secondary type order; unordered labels/aspects are canonical.
        Set<String> result = types ? new LinkedHashSet<>() : new TreeSet<>();
        int examined = 0;
        int length = 0;
        for (Object value : values) {
            if (++examined > MAX_TOKENS) throw new IllegalArgumentException("Too many tokens");
            String token = nonempty(types ? call(value, "getName") : value);
            if (token == null || token.length() > MAX_STRING_LENGTH
                || token.chars().anyMatch(c -> Character.isWhitespace(c) || c == ','))
                throw new IllegalArgumentException("Invalid token");
            if (classes) {
                var classification = SpeciesClass.fromLabel(token);
                if (classification.isEmpty()) continue;
                token = classification.get().name().toLowerCase(Locale.ROOT);
            }
            if (result.add(token)) length += token.length() + (result.size() == 1 ? 0 : 1);
            if (length > MAX_STRING_LENGTH) throw new IllegalArgumentException("Token set too long");
        }
        return String.join(",", result);
    }
}

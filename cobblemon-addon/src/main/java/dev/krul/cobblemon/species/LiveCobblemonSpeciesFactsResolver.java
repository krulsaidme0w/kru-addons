package dev.krul.cobblemon.species;

import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Reflection boundary for Cobblemon's client-synchronized species registry. */
public final class LiveCobblemonSpeciesFactsResolver implements SpeciesFactsResolver {
    private final Function<String, Object> lookup;
    private final Map<String, SpeciesFacts> cache = new ConcurrentHashMap<>();

    public LiveCobblemonSpeciesFactsResolver() {
        this(LiveCobblemonSpeciesFactsResolver::lookupLive);
    }

    LiveCobblemonSpeciesFactsResolver(Function<String, Object> lookup) {
        this.lookup = lookup;
    }

    @Override
    public SpeciesFacts resolve(String species) {
        String normalized = normalizeSpecies(species);
        if (normalized.isBlank()) return SpeciesFacts.unknown(normalized);
        return cache.computeIfAbsent(normalized, this::read);
    }

    private SpeciesFacts read(String species) {
        try {
            Object definition = lookup.apply(species);
            if (definition == null) return SpeciesFacts.unknown(species);
            Object dexValue = invoke(definition, "getNationalPokedexNumber");
            if (!(dexValue instanceof Number dex)) return SpeciesFacts.unknown(species);
            List<String> types = orderedNames(invoke(definition, "getTypes"), "getName");
            Set<String> groups = names(invoke(definition, "getEggGroups"), "getShowdownID");
            Set<String> labels = strings(invoke(definition, "getLabels"));
            return SpeciesFacts.from(species, dex.intValue(), types, groups, labels);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return SpeciesFacts.unknown(species);
        }
    }

    private static Object lookupLive(String species) {
        try {
            Class<?> registry = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Method lookup = registry.getMethod("getByIdentifier", Identifier.class);
            return lookup.invoke(null, Identifier.of(species));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Set<String> names(Object values, String accessor) throws ReflectiveOperationException {
        if (!(values instanceof Iterable<?> iterable)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : iterable) {
            if (value == null) continue;
            Object name = invoke(value, accessor);
            if (name != null && !name.toString().isBlank()) result.add(name.toString());
        }
        return Set.copyOf(result);
    }

    private static List<String> orderedNames(Object values, String accessor) throws ReflectiveOperationException {
        if (!(values instanceof Iterable<?> iterable)) return List.of();
        ArrayList<String> result = new ArrayList<>();
        for (Object value : iterable) {
            if (value == null) continue;
            Object name = invoke(value, accessor);
            if (name != null && !name.toString().isBlank()) result.add(name.toString());
        }
        return List.copyOf(result);
    }

    private static Set<String> strings(Object values) {
        if (!(values instanceof Iterable<?> iterable)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : iterable) {
            if (value != null && !value.toString().isBlank()) result.add(value.toString());
        }
        return Set.copyOf(result);
    }

    private static Object invoke(Object receiver, String method) throws ReflectiveOperationException {
        return receiver.getClass().getMethod(method).invoke(receiver);
    }

    private static String normalizeSpecies(String species) {
        return species == null ? "" : species.trim().toLowerCase(Locale.ROOT);
    }
}


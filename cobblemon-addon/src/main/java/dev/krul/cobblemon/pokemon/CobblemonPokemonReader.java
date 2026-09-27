package dev.krul.cobblemon.pokemon;

import net.minecraft.entity.Entity;
import java.lang.reflect.Method;
import java.util.*;

/** Read-only Cobblemon ABI boundary; server-specific aspect meanings belong to rules. */
public final class CobblemonPokemonReader {
    public Optional<CobblemonPokemonData> read(Entity entity) {
        if (entity == null || !entity.getClass().getName().contains("PokemonEntity")) return Optional.empty();
        return readPokemon(optional(entity, "getPokemon"));
    }

    public static Optional<CobblemonPokemonData> readPokemon(Object pokemon) {
        Object species = optional(optional(pokemon, "getSpecies"), "getResourceIdentifier");
        if (species == null || species.toString().isBlank()) return Optional.empty();
        Object rawShiny = optional(pokemon, "getShiny");
        Object rawAspects = optional(pokemon, "getAspects");
        Set<String> aspects = new LinkedHashSet<>();
        if (rawAspects instanceof Iterable<?> values) for (Object value : values) {
            if (aspects.size() >= 64) break;
            if (value instanceof String s && s.length() <= 256) aspects.add(s);
        }
        Boolean shiny = rawShiny instanceof Boolean b ? b : null;
        Map<String, String> details = new LinkedHashMap<>();
        put(details, "form", text(optional(optional(pokemon, "getForm"), "getName")));
        put(details, "level", number(optional(pokemon, "getLevel")));
        put(details, "gender", text(optional(pokemon, "getGender")));
        put(details, "nature", named(optional(pokemon, "getNature")));
        put(details, "ability", named(optional(pokemon, "getAbility")));
        put(details, "egg_groups", tokens(optional(species, "getEggGroups")));
        put(details, "ivs", ivs(optional(pokemon, "getIvs")));
        put(details, "evs", ivs(optional(pokemon, "getEvs")));
        put(details, "held_item", item(optional(pokemon, "heldItem")));
        if (!details.containsKey("held_item")) put(details, "held_item", item(optional(pokemon, "getHeldItem")));
        return Optional.of(new CobblemonPokemonData(species.toString(), shiny, null, aspects, details));
    }

    private static void put(Map<String, String> out, String key, String value) {
        if (value != null && !value.isBlank() && value.length() <= 256) out.put(key, value);
    }
    private static String text(Object value) { return value == null ? null : String.valueOf(value); }
    private static String number(Object value) { return value instanceof Number n ? Integer.toString(n.intValue()) : null; }
    private static String named(Object value) {
        if (value == null) return null;
        Object name = optional(value, "getName");
        if (name == null) name = optional(value, "getDisplayName");
        return text(name == null ? value : name);
    }
    private static String tokens(Object value) {
        if (!(value instanceof Iterable<?> values)) return null;
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).limit(16)
            .map(CobblemonPokemonReader::named).filter(Objects::nonNull).reduce((a, b) -> a + "," + b).orElse(null);
    }
    private static String ivs(Object value) {
        if (value == null) return null;
        try {
            Class<?> stats = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
            String[] names = {"HP", "ATTACK", "DEFENCE", "SPECIAL_ATTACK", "SPECIAL_DEFENCE", "SPEED"};
            List<String> result = new ArrayList<>();
            for (String name : names) {
                Object stat = Enum.valueOf((Class) stats, name);
                Object number = null;
                for (Method method : value.getClass().getMethods()) {
                    if (method.getName().equals("getOrDefault") && method.getParameterCount() == 1) {
                        number = method.invoke(value, stat);
                        break;
                    }
                }
                if (number instanceof Number n) result.add(name + "=" + n.intValue());
            }
            return result.isEmpty() ? null : String.join(",", result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
    private static String item(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof net.minecraft.item.ItemStack stack) {
                return stack.isEmpty() ? "none" : net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static Object optional(Object receiver, String name) {
        if (receiver == null) return null;
        try {
            Method method = receiver.getClass().getMethod(name);
            return method.invoke(receiver);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}

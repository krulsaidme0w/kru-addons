package dev.krul.cobblemon.hover;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import java.lang.reflect.Method;
import java.util.*;

/** Bounded reflection boundary for a Pokémon already resolved from the hovered PC slot. */
public final class PokemonHoverReader {
    private static final String[] STATS = {"HP", "ATTACK", "DEFENCE", "SPECIAL_ATTACK", "SPECIAL_DEFENCE", "SPEED"};

    public PokemonHoverDetails read(Object pokemon) {
        if (pokemon == null) return null;
        try {
            Object species = invoke(pokemon, "getSpecies");
            String speciesId = text(invoke(species, "getResourceIdentifier"));
            if (speciesId.isBlank()) return null;
            List<String> types = new ArrayList<>(); addType(types, optional(species, "getPrimaryType")); addType(types, optional(species, "getSecondaryType"));
            List<Integer> ivs = stats(optional(pokemon, "getIvs"), 31), evs = stats(optional(pokemon, "getEvs"), 252);
            List<String> moves = moves(optional(pokemon, "getMoveSet"));
            Set<String> aspects = aspects(optional(pokemon, "getAspects"));
            String tera = named(optional(pokemon, "getTeraType"));
            String skin = aspects.stream().filter(v -> v.toLowerCase(Locale.ROOT).contains("skin")).findFirst().orElse("");
            String display = text(first(pokemon, "getNickname", "getDisplayName"));
            if (display.isBlank() || display.equalsIgnoreCase("null")) display = title(speciesId);
            Object scale = first(pokemon, "getScaleModifier", "getScale");
            return new PokemonHoverDetails(display, title(speciesId), named(optional(pokemon, "getForm")),
                number(optional(pokemon, "getLevel")), text(optional(pokemon, "getGender")),
                Boolean.TRUE.equals(optional(pokemon, "getShiny")), types, emptyUnknown(tera),
                emptyUnknown(named(optional(pokemon, "getAbility"))), emptyUnknown(named(optional(pokemon, "getNature"))),
                itemName(first(pokemon, "heldItem", "getHeldItem$common", "getHeldItem")),
                itemName(first(pokemon, "getCaughtBall", "getCaughtBall$common")),
                scale instanceof Number n ? String.format(Locale.ROOT, "%.2fx", n.doubleValue()) : "", skin,
                ivs, evs, moves);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return null; }
    }

    private static List<Integer> stats(Object source, int max) {
        if (source == null) return List.of();
        try {
            Class<?> stats = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
            List<Integer> result = new ArrayList<>(6);
            for (String name : STATS) {
                Object stat = Enum.valueOf((Class) stats, name), raw = invoke(source, "getOrDefault", stat);
                if (!(raw instanceof Number n) || n.intValue() < 0 || n.intValue() > max) return List.of();
                result.add(n.intValue());
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return List.of(); }
    }
    private static List<String> moves(Object moveSet) {
        Object raw = first(moveSet, "getMoves", "getMoveset");
        if (!(raw instanceof Iterable<?> values)) return List.of();
        List<String> result = new ArrayList<>(4);
        for (Object move : values) { if (result.size() == 4) break; String name = text(first(move, "getDisplayName", "getName", "getMove")); if (!name.isBlank() && !name.equalsIgnoreCase("null")) result.add(name); }
        return List.copyOf(result);
    }
    private static Set<String> aspects(Object raw) {
        if (!(raw instanceof Iterable<?> values)) return Set.of();
        Set<String> result = new LinkedHashSet<>(); for (Object value : values) if (value instanceof String s && result.size() < 32) result.add(s); return result;
    }
    private static void addType(List<String> out, Object raw) { String value = named(raw); if (!value.isBlank() && !value.equalsIgnoreCase("null") && !out.contains(value)) out.add(value); }
    private static String named(Object value) { if (value == null) return ""; Object name = first(value, "getName", "getDisplayName"); return text(name == null ? value : name); }
    private static String itemName(Object value) {
        if (value instanceof ItemStack stack) return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
        return named(value);
    }
    private static Object first(Object receiver, String... names) { for (String name : names) { Object v = optional(receiver, name); if (v != null) return v; } return null; }
    private static Object optional(Object receiver, String name) { try { return invoke(receiver, name); } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; } }
    private static Object invoke(Object receiver, String name, Object... args) throws ReflectiveOperationException {
        if (receiver == null) throw new NoSuchMethodException(name);
        for (Method method : receiver.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { return method.invoke(receiver, args); } catch (IllegalArgumentException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
    private static String text(Object value) { if (value == null) return ""; Object plain = optional(value, "getString"); return String.valueOf(plain == null ? value : plain); }
    private static int number(Object v) { return v instanceof Number n ? n.intValue() : 0; }
    private static String emptyUnknown(String v) { return v == null || v.equalsIgnoreCase("unknown") ? "" : v; }
    private static String title(String id) { String name = id.substring(id.lastIndexOf(':') + 1).replace('-', ' ').replace('_', ' '); StringJoiner out = new StringJoiner(" "); for (String p : name.split("\\s+")) if (!p.isBlank()) out.add(Character.toUpperCase(p.charAt(0)) + p.substring(1)); return out.toString(); }
}

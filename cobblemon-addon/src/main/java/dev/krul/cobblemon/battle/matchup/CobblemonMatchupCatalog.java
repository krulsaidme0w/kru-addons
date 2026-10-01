package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.*;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import java.util.*;
import static dev.krul.cobblemon.battle.matchup.BattleReflection.*;

/** Optional live registry metadata, not a source of enemy moves or abilities. */
public final class CobblemonMatchupCatalog implements CobblemonMatchupReader.Catalog {
    public BattleMove move(String id) {
        Object template = callStatic("com.cobblemon.mod.common.api.moves.Moves", "getByName", id);
        if (template == null) return null;
        PokemonType type = PokemonType.fromId(string(get(get(template, "getElementalType"), "getName"))).orElse(null);
        MoveCategory category = MoveCategory.fromId(string(get(get(template, "getDamageCategory"), "getName")));
        return new BattleMove(id, text(get(template, "getDisplayName")), type, category, (int) number(get(template, "getPower"), 0));
    }
    public List<PokemonType> types(String species, Set<String> aspects) {
        Object data = species(species);
        Object form = get(data, "getForm", aspects);
        List<PokemonType> types = new ArrayList<>();
        Object raw = get(form, "getTypes");
        if (raw instanceof Iterable<?> iterable) for (Object type : iterable) {
            PokemonType.fromId(string(get(type, "getName"))).ifPresent(t -> { if (!types.contains(t)) types.add(t); });
        }
        return List.copyOf(types);
    }
    @Override public int baseSpeed(String species, Set<String> aspects) {
        Object form = form(species, aspects);
        Object stats = get(form, "getBaseStats");
        if (!(stats instanceof Map<?, ?> values)) return -1;
        try {
            Object speed = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats", false,
                CobblemonMatchupCatalog.class.getClassLoader()).getField("SPEED").get(null);
            Object value = values.get(speed);
            return value instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return -1; }
    }
    @Override public List<String> possibleAttacks(String species, Set<String> aspects, int level) {
        Object learnset = get(form(species, aspects), "getMoves");
        Object all = get(learnset, "getAllLegalMoves");
        if (!(all instanceof Iterable<?> moves)) return List.of();
        return java.util.stream.StreamSupport.stream(moves.spliterator(), false)
            .filter(move -> number(get(move, "getPower"), 0) > 0)
            .map(move -> {
                String name = text(get(move, "getDisplayName"));
                String type = string(get(get(move, "getElementalType"), "getName"));
                int power = (int) number(get(move, "getPower"), 0);
                return name.isBlank() ? "" : name + (type.isBlank() ? "" : " (" + type + ", " + power + ")");
            })
            .filter(name -> !name.isBlank()).distinct().sorted().limit(4).toList();
    }
    private static Object form(String species, Set<String> aspects) {
        Object data = species(species);
        Object form = get(data, "getForm", aspects);
        return form == null ? data : form;
    }
    private static Object species(String species) {
        Object data = callStatic("com.cobblemon.mod.common.api.pokemon.PokemonSpecies", "getByName", species);
        if (data == null && species != null && species.contains(":")) {
            Identifier identifier = Identifier.tryParse(species);
            if (identifier != null) data = callStatic("com.cobblemon.mod.common.api.pokemon.PokemonSpecies", "getByIdentifier", identifier);
        }
        return data;
    }
    public String moveId(Object value) {
        // Same translation-key contract used by the native BattlePokemonInfoPanel.
        if (value instanceof Text text && text.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if (key.startsWith("cobblemon.move.")) return key.substring("cobblemon.move.".length());
        }
        return "";
    }
    public String text(Object value) {
        if (value instanceof Text text) return text.getString();
        if (value instanceof ItemStack stack) return stack.isEmpty() ? "None (revealed)" : stack.getName().getString();
        return string(value);
    }
}

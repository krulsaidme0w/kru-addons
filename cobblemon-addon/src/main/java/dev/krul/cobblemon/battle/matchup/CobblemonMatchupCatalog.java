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
        Object data = callStatic("com.cobblemon.mod.common.api.pokemon.PokemonSpecies", "getByName", species);
        if (data == null && species.contains(":")) {
            Identifier identifier = Identifier.tryParse(species);
            if (identifier != null) data = callStatic("com.cobblemon.mod.common.api.pokemon.PokemonSpecies", "getByIdentifier", identifier);
        }
        Object form = get(data, "getForm", aspects);
        List<PokemonType> types = new ArrayList<>();
        Object raw = get(form, "getTypes");
        if (raw instanceof Iterable<?> iterable) for (Object type : iterable) {
            PokemonType.fromId(string(get(type, "getName"))).ifPresent(t -> { if (!types.contains(t)) types.add(t); });
        }
        return List.copyOf(types);
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


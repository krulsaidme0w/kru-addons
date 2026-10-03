package dev.krul.cobblemon.hover;

import java.util.List;

/** Immutable, read-only facts for one Pokémon under the pointer; empty values mean unknown. */
public record PokemonHoverDetails(String displayName, String species, String form, int level, String gender, boolean shiny,
                                  List<String> types, String teraType, String ability, String nature,
                                  String heldItem, String caughtBall, String size, String skin,
                                  List<Integer> ivs, List<Integer> evs, List<String> moves) {
    public PokemonHoverDetails {
        displayName = clean(displayName);
        species = clean(species);
        if (displayName.isEmpty() || species.isEmpty()) throw new IllegalArgumentException("Pokémon identity is required");
        form = clean(form); gender = clean(gender); teraType = clean(teraType); ability = clean(ability);
        nature = clean(nature); heldItem = clean(heldItem); caughtBall = clean(caughtBall); size = clean(size); skin = clean(skin);
        types = cleanStrings(types); moves = cleanStrings(moves);
        ivs = cleanInts(ivs); evs = cleanInts(evs);
    }

    public IvTintTier ivTier() {
        if (ivs.size() != 6) return IvTintTier.NONE;
        return IvTintTier.from(ivs.stream().mapToInt(Integer::intValue).toArray());
    }

    public PokemonHoverDetails withIvs(List<Integer> values) {
        return new PokemonHoverDetails(displayName, species, form, level, gender, shiny, types, teraType, ability, nature,
            heldItem, caughtBall, size, skin, values, evs, moves);
    }

    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static List<String> cleanStrings(List<String> values) {
        return values == null ? List.of() : values.stream().map(PokemonHoverDetails::clean).filter(s -> !s.isEmpty()).toList();
    }
    private static List<Integer> cleanInts(List<Integer> values) { return values == null ? List.of() : List.copyOf(values); }
}

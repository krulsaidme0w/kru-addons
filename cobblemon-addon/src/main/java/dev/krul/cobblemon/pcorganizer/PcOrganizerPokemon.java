package dev.krul.cobblemon.pcorganizer;

import java.util.Locale;
import java.util.UUID;

public record PcOrganizerPokemon(UUID uuid, int box, int slot, String species, String type, String form,
                                 boolean shiny, boolean radiant, String rarity, String ability, String heldItem,
                                 int level, int ivTotal, int perfectIvs) {
    public PcOrganizerPokemon {
        if (uuid == null) throw new IllegalArgumentException("pokemon uuid is required");
        if (box < 0 || slot < 0) throw new IllegalArgumentException("pokemon position must be non-negative");
        species = text(species); type = text(type); form = text(form); rarity = text(rarity);
        ability = text(ability); heldItem = text(heldItem);
        if (ivTotal < 0 || perfectIvs < 0) throw new IllegalArgumentException("IV values must be non-negative");
    }
    public String normalizedSpecies() { return species.toLowerCase(Locale.ROOT); }
    private static String text(String value) { return value == null ? "" : value.strip(); }
}

package dev.krul.cobblemon.pcorganizer;

import java.util.Locale;

/** AND-combined filters. Blank/false fields are intentionally inactive. */
public record PcOrganizerFilter(String species, String type, String form, boolean shiny, boolean radiant,
                                String rarity, String ivPreset, String ability, String heldItem) {
    public PcOrganizerFilter {
        species = normalize(species);
        type = normalize(type);
        form = normalize(form);
        rarity = normalize(rarity);
        ivPreset = normalize(ivPreset);
        ability = normalize(ability);
        heldItem = normalize(heldItem);
    }

    public static PcOrganizerFilter empty() { return new PcOrganizerFilter("", "", "", false, false, "", "", "", ""); }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }
}

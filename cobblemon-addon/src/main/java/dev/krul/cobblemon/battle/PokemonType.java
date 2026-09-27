package dev.krul.cobblemon.battle;

import java.util.Locale;
import java.util.Optional;

public enum PokemonType {
    NORMAL("Normal", "NOR", 0xFF9FA19F),
    FIRE("Fire", "FIR", 0xFFE62829),
    WATER("Water", "WAT", 0xFF2980EF),
    ELECTRIC("Electric", "ELE", 0xFFFAC000),
    GRASS("Grass", "GRA", 0xFF3FA129),
    ICE("Ice", "ICE", 0xFF3DCEF3),
    FIGHTING("Fighting", "FIG", 0xFFFF8000),
    POISON("Poison", "POI", 0xFF9141CB),
    GROUND("Ground", "GRO", 0xFF915121),
    FLYING("Flying", "FLY", 0xFF81B9EF),
    PSYCHIC("Psychic", "PSY", 0xFFEF4179),
    BUG("Bug", "BUG", 0xFF91A119),
    ROCK("Rock", "ROC", 0xFFAFA981),
    GHOST("Ghost", "GHO", 0xFF704170),
    DRAGON("Dragon", "DRA", 0xFF5060E1),
    DARK("Dark", "DAR", 0xFF624D4E),
    STEEL("Steel", "STE", 0xFF60A1B8),
    FAIRY("Fairy", "FAI", 0xFFEF70EF);

    private final String label;
    private final String abbreviation;
    private final int argb;

    PokemonType(String label, String abbreviation, int argb) {
        this.label = label;
        this.abbreviation = abbreviation;
        this.argb = argb;
    }

    public String label() {
        return label;
    }

    public String abbreviation() {
        return abbreviation;
    }

    public int argb() {
        return argb;
    }

    public static Optional<PokemonType> fromId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.trim().toUpperCase(Locale.ROOT);
        int namespace = normalized.lastIndexOf(':');
        if (namespace >= 0) normalized = normalized.substring(namespace + 1);
        normalized = normalized.replace('-', '_').replace(' ', '_');
        try {
            return Optional.of(valueOf(normalized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}


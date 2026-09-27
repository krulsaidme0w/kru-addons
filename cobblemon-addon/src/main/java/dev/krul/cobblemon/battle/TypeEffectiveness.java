package dev.krul.cobblemon.battle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.krul.cobblemon.battle.PokemonType.*;

public final class TypeEffectiveness {
    private static final Map<Cell, Double> CELLS = build();

    private TypeEffectiveness() {
    }

    public static Map<Cell, Double> cells() {
        return CELLS;
    }

    public static double multiplier(PokemonType attack, PokemonType... defenders) {
        if (attack == null || defenders == null || defenders.length == 0) return 1.0;
        double result = 1.0;
        for (PokemonType defender : defenders) {
            if (defender != null) result *= CELLS.get(new Cell(attack, defender));
        }
        return result;
    }

    public static Summary offensive(PokemonType attack) {
        return summary(attack, true);
    }

    public static Summary defensive(PokemonType defend) {
        return summary(defend, false);
    }

    private static Summary summary(PokemonType selected, boolean attacking) {
        List<PokemonType> doubled = java.util.Arrays.stream(PokemonType.values())
            .filter(other -> value(selected, other, attacking) == 2.0).toList();
        List<PokemonType> halved = java.util.Arrays.stream(PokemonType.values())
            .filter(other -> value(selected, other, attacking) == 0.5).toList();
        List<PokemonType> immune = java.util.Arrays.stream(PokemonType.values())
            .filter(other -> value(selected, other, attacking) == 0.0).toList();
        return new Summary(doubled, halved, immune);
    }

    private static double value(PokemonType selected, PokemonType other, boolean attacking) {
        return attacking ? multiplier(selected, other) : multiplier(other, selected);
    }

    private static Map<Cell, Double> build() {
        Map<Cell, Double> values = new LinkedHashMap<>();
        for (PokemonType attack : PokemonType.values()) {
            for (PokemonType defend : PokemonType.values()) values.put(new Cell(attack, defend), 1.0);
        }
        set(values, NORMAL, 0.5, ROCK, STEEL); set(values, NORMAL, 0.0, GHOST);
        set(values, FIRE, 2.0, GRASS, ICE, BUG, STEEL); set(values, FIRE, 0.5, FIRE, WATER, ROCK, DRAGON);
        set(values, WATER, 2.0, FIRE, GROUND, ROCK); set(values, WATER, 0.5, WATER, GRASS, DRAGON);
        set(values, ELECTRIC, 2.0, WATER, FLYING); set(values, ELECTRIC, 0.5, ELECTRIC, GRASS, DRAGON); set(values, ELECTRIC, 0.0, GROUND);
        set(values, GRASS, 2.0, WATER, GROUND, ROCK); set(values, GRASS, 0.5, FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL);
        set(values, ICE, 2.0, GRASS, GROUND, FLYING, DRAGON); set(values, ICE, 0.5, FIRE, WATER, ICE, STEEL);
        set(values, FIGHTING, 2.0, NORMAL, ICE, ROCK, DARK, STEEL); set(values, FIGHTING, 0.5, POISON, FLYING, PSYCHIC, BUG, FAIRY); set(values, FIGHTING, 0.0, GHOST);
        set(values, POISON, 2.0, GRASS, FAIRY); set(values, POISON, 0.5, POISON, GROUND, ROCK, GHOST); set(values, POISON, 0.0, STEEL);
        set(values, GROUND, 2.0, FIRE, ELECTRIC, POISON, ROCK, STEEL); set(values, GROUND, 0.5, GRASS, BUG); set(values, GROUND, 0.0, FLYING);
        set(values, FLYING, 2.0, GRASS, FIGHTING, BUG); set(values, FLYING, 0.5, ELECTRIC, ROCK, STEEL);
        set(values, PSYCHIC, 2.0, FIGHTING, POISON); set(values, PSYCHIC, 0.5, PSYCHIC, STEEL); set(values, PSYCHIC, 0.0, DARK);
        set(values, BUG, 2.0, GRASS, PSYCHIC, DARK); set(values, BUG, 0.5, FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL, FAIRY);
        set(values, ROCK, 2.0, FIRE, ICE, FLYING, BUG); set(values, ROCK, 0.5, FIGHTING, GROUND, STEEL);
        set(values, GHOST, 2.0, PSYCHIC, GHOST); set(values, GHOST, 0.5, DARK); set(values, GHOST, 0.0, NORMAL);
        set(values, DRAGON, 2.0, DRAGON); set(values, DRAGON, 0.5, STEEL); set(values, DRAGON, 0.0, FAIRY);
        set(values, DARK, 2.0, PSYCHIC, GHOST); set(values, DARK, 0.5, FIGHTING, DARK, FAIRY);
        set(values, STEEL, 2.0, ICE, ROCK, FAIRY); set(values, STEEL, 0.5, FIRE, WATER, ELECTRIC, STEEL);
        set(values, FAIRY, 2.0, FIGHTING, DRAGON, DARK); set(values, FAIRY, 0.5, FIRE, POISON, STEEL);
        return Collections.unmodifiableMap(values);
    }

    private static void set(Map<Cell, Double> values, PokemonType attack, double multiplier,
                            PokemonType... defenders) {
        for (PokemonType defender : defenders) values.put(new Cell(attack, defender), multiplier);
    }

    public record Cell(PokemonType attack, PokemonType defend) {
    }

    public record Summary(List<PokemonType> doubled, List<PokemonType> halved,
                          List<PokemonType> immune) {
        public Summary {
            doubled = List.copyOf(doubled);
            halved = List.copyOf(halved);
            immune = List.copyOf(immune);
        }
    }
}


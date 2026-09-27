package dev.krul.cobblemon.battle;

/** Compact, width-bounded labels for the interactive type matrix. */
public final class TypeMatrixPresentation {
    private TypeMatrixPresentation() {}

    public static String describe(PokemonType attack, PokemonType defend) {
        if (attack != null && defend != null) {
            return attack.label() + " → " + defend.label() + " · "
                + format(TypeEffectiveness.multiplier(attack, defend));
        }
        if (attack != null) return attack.label() + " attack";
        if (defend != null) return defend.label() + " defence";
        return "Hover · click to lock · Esc";
    }

    public static String headerTooltip(PokemonType type, boolean attack) {
        if (type == null) return "";
        return type.label() + (attack ? " attack" : " defence");
    }

    private static String format(double multiplier) {
        if (multiplier == 0.0) return "0x";
        if (multiplier == 0.5) return "1/2x";
        if (multiplier == 2.0) return "2x";
        return multiplier + "x";
    }
}


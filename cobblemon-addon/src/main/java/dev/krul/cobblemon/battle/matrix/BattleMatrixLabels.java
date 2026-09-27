package dev.krul.cobblemon.battle.matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Short, roster-local move labels; full names remain in each chip's tooltip. */
final class BattleMatrixLabels {
    private BattleMatrixLabels() {}

    static List<String> abbreviations(List<String> names) {
        List<String> bases = names.stream().limit(4).map(BattleMatrixLabels::initials).toList();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < bases.size(); i++) {
            String base = bases.get(i);
            labels.add(bases.stream().filter(base::equals).count() > 1 ? base + (i + 1) : base);
        }
        return List.copyOf(labels);
    }

    private static String initials(String name) {
        List<String> words = java.util.Arrays.stream(name.toUpperCase(Locale.ROOT).split("[^\\p{L}]+"))
            .filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) return "?";
        if (words.size() == 1) {
            String word = words.getFirst();
            return word.substring(0, word.offsetByCodePoints(0, Math.min(2, word.codePointCount(0, word.length()))));
        }
        return words.getFirst().substring(0, words.getFirst().offsetByCodePoints(0, 1))
            + words.get(1).substring(0, words.get(1).offsetByCodePoints(0, 1));
    }
}

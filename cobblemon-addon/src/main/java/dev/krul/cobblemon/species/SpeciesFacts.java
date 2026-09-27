package dev.krul.cobblemon.species;

import java.util.EnumSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable registry facts for one normalized species id. */
public record SpeciesFacts(
    String species,
    int nationalDex,
    Set<String> types,
    String primaryType,
    Set<String> eggGroups,
    Set<String> labels,
    Set<SpeciesClass> classes,
    boolean authoritative
) {
    public SpeciesFacts {
        species = normalizeSpecies(species);
        types = normalizedCopy(types);
        primaryType = normalizeLabel(primaryType);
        if (!primaryType.isBlank() && !types.contains(primaryType)) primaryType = "";
        eggGroups = normalizedCopy(eggGroups);
        labels = normalizedCopy(labels);
        classes = classes == null || classes.isEmpty()
            ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(classes));
    }

    public static SpeciesFacts from(String species, int nationalDex, Set<String> types,
                                    Set<String> eggGroups, Set<String> labels) {
        Set<String> normalizedLabels = normalizedCopy(labels);
        EnumSet<SpeciesClass> classes = EnumSet.noneOf(SpeciesClass.class);
        normalizedLabels.forEach(label -> SpeciesClass.fromLabel(label).ifPresent(classes::add));
        String primaryType = normalizedCopy(types).stream()
            .min(Comparator.comparingInt(PokemonTypeOrder::rank).thenComparing(String::compareTo))
            .orElse("");
        return new SpeciesFacts(species, nationalDex, types, primaryType, eggGroups,
            normalizedLabels, classes, true);
    }

    public static SpeciesFacts from(String species, int nationalDex, List<String> orderedTypes,
                                    Set<String> eggGroups, Set<String> labels) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (orderedTypes != null) {
            orderedTypes.stream().map(SpeciesFacts::normalizeLabel)
                .filter(value -> !value.isBlank()).forEach(types::add);
        }
        String primaryType = types.stream().findFirst().orElse("");
        Set<String> normalizedLabels = normalizedCopy(labels);
        EnumSet<SpeciesClass> classes = EnumSet.noneOf(SpeciesClass.class);
        normalizedLabels.forEach(label -> SpeciesClass.fromLabel(label).ifPresent(classes::add));
        return new SpeciesFacts(species, nationalDex, types, primaryType, eggGroups,
            normalizedLabels, classes, true);
    }

    public static SpeciesFacts unknown(String species) {
        return new SpeciesFacts(species, -1, Set.of(), "", Set.of(), Set.of(), Set.of(), false);
    }

    public boolean hasClass(SpeciesClass value) {
        return value != null && classes.contains(value);
    }

    public boolean special() {
        return classes.stream().anyMatch(SpeciesClass::special);
    }

    public static String normalizeLabel(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
    }

    private static String normalizeSpecies(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> normalizedCopy(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.stream().map(SpeciesFacts::normalizeLabel).filter(value -> !value.isBlank())
            .sorted().forEach(result::add);
        return Collections.unmodifiableSet(result);
    }
}


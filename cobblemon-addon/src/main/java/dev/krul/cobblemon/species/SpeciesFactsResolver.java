package dev.krul.cobblemon.species;

@FunctionalInterface
public interface SpeciesFactsResolver {
    SpeciesFacts resolve(String species);
}


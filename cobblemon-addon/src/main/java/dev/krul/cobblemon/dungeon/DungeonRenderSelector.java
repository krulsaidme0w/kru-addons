package dev.krul.cobblemon.dungeon;

import dev.krul.cobblemon.rules.RuleProfile;
import java.util.*;

/** Pure distance/freshness ordering; recognition and labels come from explicit data. */
public final class DungeonRenderSelector {
    private DungeonRenderSelector() {}
    public static List<DungeonRenderTarget> select(List<DungeonObservation> observations, String dimension,
        long now, long freshnessMillis, double x, double y, double z, double maximumDistance) {
        return select(observations, dimension, now, freshnessMillis, x, y, z, maximumDistance, DungeonTargetFilters.ALL);
    }
    public static List<DungeonRenderTarget> select(List<DungeonObservation> observations, String dimension,
        long now, long freshnessMillis, double x, double y, double z, double maximumDistance, boolean alphaPokemon) {
        return select(observations, dimension, now, freshnessMillis, x, y, z, maximumDistance, DungeonTargetFilters.ALL);
    }
    public static List<DungeonRenderTarget> select(List<DungeonObservation> observations, String dimension,
        long now, long freshnessMillis, double x, double y, double z, double maximumDistance, DungeonTargetFilters filters) {
        return select(observations, dimension, now, freshnessMillis, x, y, z, maximumDistance, filters, RuleProfile.disabled());
    }
    public static List<DungeonRenderTarget> select(List<DungeonObservation> observations, String dimension,
        long now, long freshnessMillis, double x, double y, double z, double maximumDistance,
        DungeonTargetFilters filters, RuleProfile profile) {
        return select(observations, dimension, now, freshnessMillis, x, y, z, maximumDistance, filters, profile, null);
    }
    public static List<DungeonRenderTarget> select(List<DungeonObservation> observations, String dimension,
        long now, long freshnessMillis, double x, double y, double z, double maximumDistance,
        DungeonTargetFilters filters, RuleProfile profile, OpenedChestCache openedChests) {
        if (observations == null || dimension == null) return List.of();
        List<DungeonRenderTarget> targets = new ArrayList<>();
        for (DungeonObservation observation : observations) {
            if (!dimension.equals(observation.dimension()) || observation.position() == null) continue;
            if (observation.seenAt() > now || now - observation.seenAt() > freshnessMillis) continue;
            double distanceSquared = observation.position().getSquaredDistance(x, y, z);
            if (distanceSquared > maximumDistance * maximumDistance) continue;
            classify(observation, Math.sqrt(distanceSquared), profile)
                .filter(target -> openedChests == null || !isChest(target.categoryId())
                    || !openedChests.isOpen(dimension, observation.position()))
                .filter(target -> profile.categories().stream()
                    .filter(category -> category.id().equals(target.categoryId()))
                    .findFirst().map(category -> target.distance() <= category.maxDistance()).orElse(false))
                .filter(target -> visible(target.categoryId(), filters, profile))
                .ifPresent(targets::add);
        }
        targets.sort(Comparator.comparingDouble(DungeonRenderTarget::distance).thenComparing(DungeonRenderTarget::identity));
        return List.copyOf(targets);
    }
    private static boolean isChest(String categoryId) { return categoryId.equals("chest") || categoryId.equals("CHEST"); }
    public static Optional<DungeonRenderTarget> classify(DungeonObservation observation, double distance) {
        return classify(observation, distance, RuleProfile.disabled());
    }
    public static Optional<DungeonRenderTarget> classify(DungeonObservation observation, double distance, RuleProfile profile) {
        if (observation.position() == null) return Optional.empty();
        return profile.match(facts(observation)).flatMap(match -> profile.categories().stream()
            .filter(category -> category.id().equals(match.target()) && category.enabled())
            .map(category -> new DungeonRenderTarget(category.id(), category.id().equals("guardian") || category.id().equals("strong_pokemon"),
                observation.identity(), match.label(), observation.position(), distance))
            .findFirst());
    }

    private static boolean visible(String categoryId, DungeonTargetFilters filters, RuleProfile profile) {
        return switch (categoryId) {
            case "chest", "CHEST" -> filters.chests();
            case "poke_ball", "POKE_BALL" -> filters.pokeBalls();
            case "weapon", "WEAPON" -> filters.stakes();
            case "guardian", "GUARDIAN" -> filters.guardians();
            case "strong_pokemon", "STRONG_POKEMON" -> filters.strongPokemon();
            default -> true;
        } && profile.categories().stream().filter(c -> c.id().equals(categoryId)).findFirst().map(c -> c.enabled()).orElse(false);
    }
    public static Map<String, Object> facts(DungeonObservation observation) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("kind", observation.kind().name());
        values.put("dimension", observation.dimension());
        values.put("registry_id", observation.registryId());
        if (!observation.name().isBlank()) values.put("name", observation.name());
        if (observation.position() != null) {
            values.put("x", observation.position().getX());
            values.put("y", observation.position().getY());
            values.put("z", observation.position().getZ());
        }
        observation.facts().forEach((key, value) -> {
            String field = key.replace(':', '_');
            if (Set.of("level", "model_data", "head_model_data").contains(field)) {
                try { values.put(field, Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
            } else if (Set.of("shiny", "wild", "opened").contains(field)) {
                if ("true".equals(value) || "false".equals(value)) values.put(field, Boolean.parseBoolean(value));
            } else if (!values.containsKey(field)) values.put(field, value);
        });
        return Map.copyOf(values);
    }
}

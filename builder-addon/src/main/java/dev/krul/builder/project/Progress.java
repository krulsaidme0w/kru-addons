package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;

/** Air targets contribute to replace (manual removal); this model never performs world actions. */
public record Progress(int matching, int place, int replace, int unknown) {
    public boolean complete() { return place == 0 && replace == 0 && unknown == 0; }

    /** For a validated plan in the caller's verified world session/selection. */
    public static Progress measure(BuildPlan plan, WorldSnapshot snapshot) {
        int matching = 0, place = 0, replace = 0, unknown = 0;
        for (var entry : targets(plan).entrySet()) {
            var relative = entry.getKey();
            if (!PlanValidator.containsRelative(snapshot.bounds(), relative)) { unknown++; continue; }
            var origin = snapshot.bounds().min();
            var absolute = new Cell(Math.addExact(origin.x(), relative.x()), Math.addExact(origin.y(), relative.y()),
                Math.addExact(origin.z(), relative.z()));
            var actual = snapshot.known().get(absolute);
            if (actual == null) unknown++;
            else if (actual.equals(entry.getValue())) matching++;
            else if (isAir(actual) && !isAir(entry.getValue())) place++;
            else replace++;
        }
        return new Progress(matching, place, replace, unknown);
    }

    static Map<Cell, StateKey> targets(BuildPlan plan) {
        var targets = new HashMap<Cell, StateKey>();
        for (var stage : plan.stages()) targets.putAll(stage.targets());
        return targets;
    }

    public static boolean isAir(StateKey state) {
        return state.id().equals("minecraft:air") || state.id().equals("minecraft:cave_air")
            || state.id().equals("minecraft:void_air");
    }
}

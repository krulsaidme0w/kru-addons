package dev.krul.cobblemon.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Render evidence is independent of the much larger, lossy diagnostic index.
 * Presence is revalidated against the loaded world by the rendering owner, not a scan-age timer. */
public final class DungeonTargetIndex {
    private final Map<String, DungeonObservation> targets = new LinkedHashMap<>();
    private final dev.krul.cobblemon.rules.RuleProfile profile;
    public DungeonTargetIndex() { this(dev.krul.cobblemon.rules.RuleProfile.disabled()); }
    public DungeonTargetIndex(dev.krul.cobblemon.rules.RuleProfile profile) { this.profile = profile; }

    public void observe(DungeonObservation observation) {
        if (DungeonRenderSelector.classify(observation, 0, profile).isEmpty()) {
            targets.remove(observation.identity());
            return;
        }
        targets.put(observation.identity(), observation);
    }

    public List<DungeonObservation> observations() { return List.copyOf(targets.values()); }
    public void clear() { targets.clear(); }
    public void retainLoaded(java.util.Set<String> identities) { targets.keySet().retainAll(identities); }
}

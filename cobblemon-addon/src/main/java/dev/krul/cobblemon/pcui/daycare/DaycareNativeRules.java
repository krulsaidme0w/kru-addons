package dev.krul.cobblemon.pcui.daycare;

import java.util.List;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.*;

final class DaycareNativeRules {
    record Choice(Projection projection, int level, boolean enabled) {}
    static int pickIndex(Pokemon target, List<Pokemon> party, List<Choice> choices) {
        if (target == null || party.stream().noneMatch(p -> p.uuid().equals(target.uuid()))
            || party.stream().filter(p -> p.projection().equals(target.projection()) && p.level() == target.level()).count() != 1)
            return -1;
        int match = -1;
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            if (c.projection().equals(target.projection()) && c.level() == target.level()) {
                if (match >= 0 || !c.enabled()) return -1;
                match = i;
            }
        }
        return match;
    }
}

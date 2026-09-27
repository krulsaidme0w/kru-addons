package dev.krul.cobblemon.rules;

import java.util.List;

/** Results in evaluation order. A disabled rule/category is ineligible even if predicates match. */
public record RuleExplanation(String ruleId, String categoryId, boolean eligible, RuleProfile.Truth truth,
                              List<Condition> conditions) {
    public RuleExplanation { conditions = List.copyOf(conditions); }
    public record Condition(String field, String operator, Object expected, Object actual, RuleProfile.Truth truth) {}
}

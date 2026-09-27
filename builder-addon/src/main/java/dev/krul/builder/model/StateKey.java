package dev.krul.builder.model;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Full state identity; syntax validation does not prove that a registry entry exists. */
public record StateKey(String id, Map<String, String> properties) {
    private static final Pattern QUALIFIED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public StateKey {
        Objects.requireNonNull(id, "id");
        if (!QUALIFIED_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("State ID must be a qualified registry identifier");
        }
        properties = Map.copyOf(properties);
    }
}

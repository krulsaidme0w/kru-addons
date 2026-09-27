package dev.krul.cobblemon.observation;

import java.util.*;
import static dev.krul.cobblemon.observation.FactValue.*;

/** Shared typed field catalog for providers and rule validation. Names are case-sensitive. */
public final class FactSchema {
    public static final int MAX_FACTS = 64;
    private static final Map<String, Field> FIELDS = createFields();
    private FactSchema() {}
    public record Field(Type type, Set<Source> sources) {
        public Field {
            Objects.requireNonNull(type, "type");
            sources = Set.copyOf(sources);
            if (sources.isEmpty()) throw new IllegalArgumentException("Field needs a source");
        }
    }
    public static Map<String, Field> fields() { return FIELDS; }
    public static Optional<Field> field(String name) { return Optional.ofNullable(FIELDS.get(name)); }

    public static void validate(String name, FactValue value) {
        Field field = field(name).orElseThrow(() -> new IllegalArgumentException("Unknown fact: " + name));
        Objects.requireNonNull(value, "fact value");
        if (field.type() != value.type() || !field.sources().contains(value.source()))
            throw new IllegalArgumentException("Wrong type or source for fact: " + name);
    }

    private static Map<String, Field> createFields() {
        Map<String, Field> result = new LinkedHashMap<>();
        add(result, Type.STRING, Source.LIVE, "kind", "dimension", "registry_id", "name", "uuid", "pokemonUuid",
            "item_id", "head_item_id", "species", "form", "gender", "aspects", "ownerUuid",
            "ivs", "evs", "nature", "ability", "moves", "heldItem");
        add(result, Type.NUMBER, Source.LIVE, "x", "y", "z", "distance", "model_data", "head_model_data", "level", "hp");
        add(result, Type.BOOLEAN, Source.LIVE, "opened", "shiny", "wild");
        add(result, Type.STRING, Source.REGISTRY_REFERENCE, "speciesClass", "referenceTypes", "referenceBucket");
        add(result, Type.STRING, Source.SERVER_MAP, "serverBucket");
        add(result, Type.STRING, Source.PROFILE, "profileLabel");
        add(result, Type.BOOLEAN, Source.PROFILE, "radiant", "alpha");
        return Collections.unmodifiableMap(result);
    }

    private static void add(Map<String, Field> fields, Type type, Source source, String... names) {
        for (String name : names) fields.put(name, new Field(type, Set.of(source)));
    }
}

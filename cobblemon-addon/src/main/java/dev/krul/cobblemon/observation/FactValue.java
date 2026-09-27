package dev.krul.cobblemon.observation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** A bounded scalar plus its source and availability; never retains a live game object. */
public record FactValue(Type type, Source source, Availability availability, Object value) {
    public static final int MAX_STRING_LENGTH = 256;
    public static final long MAX_SAFE_NUMBER = 9_007_199_254_740_991L;
    public enum Type { STRING, NUMBER, BOOLEAN }
    public enum Source { LIVE, REGISTRY_REFERENCE, SERVER_MAP, PROFILE }
    public enum Availability { AVAILABLE, NOT_SENT, READER_UNSUPPORTED, INVALID }

    public FactValue {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(availability, "availability");
        if (availability != Availability.AVAILABLE) {
            if (value != null) throw new IllegalArgumentException("Unavailable facts cannot have a value");
        } else {
            value = switch (type) {
                case STRING -> {
                    if (!(value instanceof String text) || text.length() > MAX_STRING_LENGTH)
                        throw new IllegalArgumentException("Expected string of at most 256 characters");
                    yield text;
                }
                case BOOLEAN -> {
                    if (!(value instanceof Boolean)) throw new IllegalArgumentException("Expected boolean");
                    yield value;
                }
                case NUMBER -> safeNumber(value);
            };
        }
    }

    private static double safeNumber(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof Float || value instanceof Double || value instanceof BigInteger || value instanceof BigDecimal))
            throw new IllegalArgumentException("Expected immutable number");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || new BigDecimal(value.toString()).abs().compareTo(BigDecimal.valueOf(MAX_SAFE_NUMBER)) > 0)
            throw new IllegalArgumentException("Number exceeds finite safe scalar range");
        return number;
    }

    public boolean available() { return availability == Availability.AVAILABLE; }
    public static FactValue known(Type type, Source source, Object value) {
        return new FactValue(type, source, Availability.AVAILABLE, value);
    }
    public static FactValue unavailable(Type type, Source source, Availability availability) {
        if (availability == Availability.AVAILABLE) throw new IllegalArgumentException("Expected unavailable status");
        return new FactValue(type, source, availability, null);
    }
}

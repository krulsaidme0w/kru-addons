package dev.krul.cobblemon.pcui;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Optional public Cobblemon getters; a successful null differs from an unavailable getter. */
public final class PcReflection {
    public record Read(boolean available, Object value) {}
    private static final ClassValue<Map<String, Optional<Method>>> GETTERS = new ClassValue<>() {
        @Override protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private PcReflection() {}

    public static Read read(Object receiver, String name) {
        if (receiver == null) return new Read(false, null);
        try {
            var method = GETTERS.get(receiver.getClass()).computeIfAbsent(name, ignored -> {
                try { return Optional.of(receiver.getClass().getMethod(name)); }
                catch (NoSuchMethodException | SecurityException unavailable) { return Optional.empty(); }
            });
            return method.isEmpty() ? new Read(false, null) : new Read(true, method.get().invoke(receiver));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return new Read(false, null);
        }
    }

    public static Object get(Object receiver, String name) { return read(receiver, name).value(); }

    public static String value(Object value) {
        return value == null ? "unknown" : value.toString().replace('\n', ' ').replace('\r', ' ');
    }
}

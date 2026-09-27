package dev.krul.cobblemon.battle.matchup;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Cached optional public getters only. No setters, world access or packet calls. */
public final class BattleReflection {
    private record Key(String name, List<Class<?>> args) {}
    private static final ClassValue<Map<Key, Optional<Method>>> METHODS = new ClassValue<>() {
        protected Map<Key, Optional<Method>> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private BattleReflection() {}

    public static Object get(Object receiver, String name, Object... args) {
        if (receiver == null) return null;
        Class<?> type = receiver instanceof Class<?> clazz ? clazz : receiver.getClass();
        Key key = new Key(name, Arrays.stream(args).map(a -> a == null ? Void.class : a.getClass()).toList());
        Optional<Method> method = METHODS.get(type).computeIfAbsent(key, ignored -> Arrays.stream(type.getMethods())
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length)
            .filter(m -> compatible(m.getParameterTypes(), args)).findFirst());
        try {
            return method.isEmpty() ? null : method.get().invoke(receiver instanceof Class<?> ? null : receiver, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) if (args[i] != null && !types[i].isInstance(args[i])) return false;
        return true;
    }
    public static Object singleton(String name) {
        try { return Class.forName(name).getField("INSTANCE").get(null); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }
    public static Object callStatic(String type, String method, Object... args) {
        try { return get(Class.forName(type), method, args); }
        catch (ClassNotFoundException | LinkageError ignored) { return null; }
    }
    static List<?> list(Object value) { return value instanceof List<?> list ? list : value instanceof Collection<?> c ? List.copyOf(c) : List.of(); }
    static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    static double number(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    static boolean yes(Object value) { return Boolean.TRUE.equals(value); }
}


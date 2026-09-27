package dev.krul.builder.ui;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Removes map entries only after key iteration has finished. */
final class MapCleanup {
    private MapCleanup() {}

    static <K, V> void removeWhere(Map<K, V> values, Predicate<K> remove, Consumer<V> dispose) {
        ArrayList<K> stale = new ArrayList<>();
        for (K key : values.keySet()) if (remove.test(key)) stale.add(key);
        for (K key : stale) {
            V value = values.remove(key);
            if (value != null) dispose.accept(value);
        }
    }
}

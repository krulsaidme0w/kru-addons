package dev.krul.builder.catalog;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ItemCatalogTest {
    @Test void namespacePagesAreSortedAndBoundedToTwenty() throws Exception {
        Object catalog = catalog(entries(25, "mod_b", "widget "));
        Object first = call(catalog, "listMods", new Class<?>[]{int.class}, 0);
        assertEquals(List.of("mod_b"), namespaces(call(first, "mods")));
        Object page0 = call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "widget", Optional.empty(), 0);
        Object page1 = call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "widget", Optional.empty(), 1);
        assertEquals(20, list(call(page0, "items")).size());
        assertEquals(5, list(call(page1, "items")).size());
        assertEquals(25, call(page0, "total"));
        assertEquals(1, call(page0, "nextPage"));

        List<Object> namespaces = new ArrayList<>();
        for (int i = 24; i >= 0; i--) namespaces.add(entry("mod_" + String.format(Locale.ROOT, "%02d", i), "item", "Item " + i));
        Object namespaceCatalog = catalog(namespaces);
        Object mods0 = call(namespaceCatalog, "listMods", new Class<?>[]{int.class}, 0);
        Object mods1 = call(namespaceCatalog, "listMods", new Class<?>[]{int.class}, 1);
        assertEquals(20, list(call(mods0, "mods")).size());
        assertEquals(5, list(call(mods1, "mods")).size());
        assertEquals("mod_00", namespaces(call(mods0, "mods")).get(0));
    }

    @Test void namespaceSearchIsExactAndTextSearchIgnoresCase() throws Exception {
        Object catalog = catalog(List.of(entry("mod_a", "oak", "Oak Plank"), entry("mod_b", "oak", "Oak Plank")));
        Object byNamespace = call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "", Optional.of("mod_a"), 0);
        assertEquals(List.of("mod_a:oak"), ids(call(byNamespace, "items")));
        Object byText = call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "OAK plank", Optional.empty(), 0);
        assertEquals(List.of("mod_a:oak", "mod_b:oak"), ids(call(byText, "items")));
    }

    @Test void describeResolvesOnlyTheExactItemId() throws Exception {
        Object catalog = catalog(List.of(entry("example", "oak drawer", "Oak Drawer")));
        Object details = ((Optional<?>) call(catalog, "describe", new Class<?>[]{String.class}, "example:oak_drawer")).orElseThrow();
        assertEquals("example:oak_drawer", call(details, "id"));
        assertEquals("Oak Drawer", call(details, "label"));
        assertTrue((boolean) call(catalog, "describe", new Class<?>[]{String.class}, "example:oak-drawr").equals(Optional.empty()));
    }

    @Test void invalidPagesQueriesAndBlankUnscopedSearchAreRejected() throws Exception {
        Object catalog = catalog(List.of(entry("example", "stone", "Stone")));
        assertThrows(IllegalArgumentException.class, () -> call(catalog, "listMods", new Class<?>[]{int.class}, -1));
        assertThrows(IllegalArgumentException.class, () -> call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "", Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "x".repeat(129), Optional.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> call(catalog, "search", new Class<?>[]{String.class, Optional.class, int.class}, "stone", Optional.empty(), 10_001));
    }

    private static Object catalog(List<?> entries) throws Exception {
        Class<?> catalogType = assertDoesNotThrow(() -> Class.forName("dev.krul.builder.catalog.ItemCatalog"));
        return catalogType.getConstructor(List.class, Function.class).newInstance(entries, (Function<String, String>) String::toUpperCase);
    }

    private static Object entry(String namespace, String path, String label) throws Exception {
        Class<?> entryType = assertDoesNotThrow(() -> Class.forName("dev.krul.builder.catalog.ItemCatalog$ItemEntry"));
        return entryType.getConstructor(String.class, String.class, String.class, String.class)
            .newInstance(namespace + ":" + path.replace(' ', '_'), namespace, label, "example.Item");
    }

    private static List<Object> entries(int count, String namespace, String prefix) throws Exception {
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(entry(namespace, String.format(Locale.ROOT, "item_%02d", i), prefix + i));
        return values;
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = Arrays.stream(args).map(value -> value instanceof Integer ? int.class : value.getClass()).toArray(Class<?>[]::new);
        return call(target, name, types, args);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getMethod(name, types);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(cause);
        }
    }

    @SuppressWarnings("unchecked") private static List<Object> list(Object value) { return (List<Object>) value; }
    private static List<String> ids(Object values) throws Exception { return list(values).stream().map(value -> (String) uncheckedCall(value, "id")).toList(); }
    private static List<String> namespaces(Object values) throws Exception { return list(values).stream().map(value -> (String) uncheckedCall(value, "namespace")).toList(); }
    private static Object uncheckedCall(Object target, String method) {
        try { return call(target, method); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
}

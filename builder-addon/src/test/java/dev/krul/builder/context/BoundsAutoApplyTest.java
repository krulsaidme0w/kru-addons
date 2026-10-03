package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BoundsAutoApplyTest {
    private static final Bounds AREA = new Bounds(new Cell(-2, 60, 8), new Cell(1, 64, 11));

    @Test void appliesCompleteValidCoordinatesOnlyAfterTheyStayUnchangedFor350Ms() throws Exception {
        Object input = newInput();
        assertEquals(Optional.empty(), update(input, new String[]{"-2", "60", "8", "1", "64", "11"}, 100, null));
        assertEquals(Optional.empty(), update(input, new String[]{"-2", "60", "8", "1", "64", "11"}, 449, null));
        assertEquals(Optional.of(AREA), update(input, new String[]{"-2", "60", "8", "1", "64", "11"}, 450, null));
    }

    @Test void incompleteInvalidAndOversizedValuesNeverBecomeBounds() throws Exception {
        Object input = newInput();
        assertEquals(Optional.empty(), update(input, new String[]{"1", "2", "", "4", "5", "6"}, 1_000, null));
        assertEquals(Optional.empty(), update(input, new String[]{"x", "2", "3", "4", "5", "6"}, 2_000, null));
        assertEquals(Optional.empty(), update(input, new String[]{"0", "0", "0", "32768", "0", "0"}, 3_000, null));
    }

    @Test void changingCoordinatesRestartsDebounceAndCurrentBoundsAreNotReapplied() throws Exception {
        Object input = newInput();
        String[] original = {"-2", "60", "8", "1", "64", "11"};
        update(input, original, 100, null);
        assertEquals(Optional.empty(), update(input, new String[]{"-2", "60", "8", "2", "64", "11"}, 449, null));
        assertEquals(Optional.empty(), update(input, new String[]{"-2", "60", "8", "2", "64", "11"}, 798, null));
        assertEquals(Optional.empty(), update(input, original, 1_000, AREA));
        assertEquals(Optional.empty(), update(input, original, 1_350, AREA));
    }

    private static Object newInput() throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("dev.krul.builder.context.BoundsAutoApply"));
        return type.getConstructor().newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Bounds> update(Object instance, String[] values, long now, Bounds current) throws Exception {
        Method method = instance.getClass().getMethod("update", String[].class, long.class, Bounds.class);
        try { return (Optional<Bounds>) method.invoke(instance, values, now, current); }
        catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
    }
}

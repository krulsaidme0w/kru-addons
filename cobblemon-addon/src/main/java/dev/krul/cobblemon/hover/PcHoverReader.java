package dev.krul.cobblemon.hover;

import net.minecraft.client.gui.screen.Screen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Read-only exact-slot resolver for Cobblemon's custom PC screen. */
public final class PcHoverReader {
    private static final String PC_SCREEN = "com.cobblemon.mod.common.client.gui.pc.PCGUI";

    public Optional<Hovered> read(Screen screen, int mouseX, int mouseY) {
        if (screen == null || !PC_SCREEN.equals(screen.getClass().getName())) return Optional.empty();
        try {
            Object storage = invoke(screen, "getStorage");
            List<Object> slots = new ArrayList<>();
            slots.addAll(fieldList(storage, "boxSlots"));
            slots.addAll(fieldList(storage, "partySlots"));
            for (Object slot : slots) {
                Object pokemon = optional(slot, "getPokemon");
                Object hovered = invoke(slot, "isHovered", new Class<?>[]{int.class, int.class}, mouseX, mouseY);
                if (pokemon != null && Boolean.TRUE.equals(hovered)) {
                    Number x = number(slot, "getX"), y = number(slot, "getY");
                    double sx = x == null ? mouseX : x.doubleValue(), sy = y == null ? mouseY : y.doubleValue();
                    return Optional.of(new Hovered(pokemon, new HoverCardLayout.Rect(sx, sy, 24, 24)));
                }
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return Optional.empty(); }
    }

    public static Optional<Hovered> findHovered(Iterable<?> slots, double mouseX, double mouseY) {
        if (slots == null) return Optional.empty();
        for (Object slot : slots) {
            Object pokemon = optional(slot, "getPokemon");
            Number x = number(slot, "getX"), y = number(slot, "getY");
            Number width = number(slot, "getWidth"), height = number(slot, "getHeight");
            if (pokemon == null || x == null || y == null || width == null || height == null) continue;
            var bounds = new HoverCardLayout.Rect(x.doubleValue(), y.doubleValue(), width.doubleValue(), height.doubleValue());
            if (mouseX >= bounds.x() && mouseY >= bounds.y() && mouseX < bounds.x() + bounds.width()
                && mouseY < bounds.y() + bounds.height()) return Optional.of(new Hovered(pokemon, bounds));
        }
        return Optional.empty();
    }
    private static List<Object> fieldList(Object receiver, String name) throws ReflectiveOperationException {
        Field f = field(receiver.getClass(), name); f.setAccessible(true); Object value = f.get(receiver);
        if (!(value instanceof Iterable<?> items)) return List.of();
        List<Object> result = new ArrayList<>(); items.forEach(result::add); return result;
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object receiver, String method) throws ReflectiveOperationException { return invoke(receiver, method, new Class<?>[0]); }
    private static Object invoke(Object receiver, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        for (Class<?> c = receiver.getClass(); c != null; c = c.getSuperclass()) try {
            Method m = c.getDeclaredMethod(method, types); m.setAccessible(true); return m.invoke(receiver, args);
        } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(method);
    }
    private static Number number(Object receiver, String method) { Object value = optional(receiver, method); return value instanceof Number n ? n : null; }
    private static Object optional(Object receiver, String method) {
        if (receiver == null) return null;
        try { return receiver.getClass().getMethod(method).invoke(receiver); } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { return null; }
    }
    public record Hovered(Object pokemon, HoverCardLayout.Rect bounds) {}
}

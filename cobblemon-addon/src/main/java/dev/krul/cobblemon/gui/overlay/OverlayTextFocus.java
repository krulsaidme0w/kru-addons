package dev.krul.cobblemon.gui.overlay;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Avoid stealing letters while editing vanilla, Meteor or Cobblemon text fields. */
final class OverlayTextFocus {
    static boolean blocks(Screen screen) {
        if (screen == null || screen instanceof OverlayInteractionScreen) return false;
        if (screen instanceof ChatScreen || screen instanceof WidgetScreen
            || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen) return true;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        if (focused(screen, seen, 0)) return true;
        // Cobblemon sometimes renders text inputs without registering them as Screen children.
        for (Class<?> type = screen.getClass(); type != null && type != Screen.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Element.class.isAssignableFrom(field.getType())) continue;
                try {
                    if (field.trySetAccessible() && focused(field.get(screen), seen, 0)) return true;
                } catch (ReflectiveOperationException | RuntimeException ignored) { return true; }
            }
        }
        return false;
    }
    private static boolean focused(Object value, Set<Object> seen, int depth) {
        if (value == null || depth > 12 || !seen.add(value)) return false;
        if (value instanceof TextFieldWidget field) return field.isFocused();
        if (value instanceof ParentElement parent) {
            for (Element child : parent.children()) if (focused(child, seen, depth + 1)) return true;
        }
        return false;
    }
}

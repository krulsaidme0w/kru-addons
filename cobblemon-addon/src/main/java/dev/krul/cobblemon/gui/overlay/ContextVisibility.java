package dev.krul.cobblemon.gui.overlay;

import java.util.Objects;

/** A dismissal belongs to one menu/battle, never the next one. Null is ineligible. */
public final class ContextVisibility {
    private Object context;
    private boolean visible;

    public boolean update(Object next) {
        if (Objects.equals(context, next)) return false;
        context = next;
        visible = next != null;
        return true;
    }
    public boolean visible() { return visible; }
    public void hide() { visible = false; }
    public void show() { visible = context != null; }
    public void reset() { context = null; visible = false; }
}

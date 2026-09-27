package dev.krul.cobblemon.gui.overlay;

import java.util.HashSet;
import java.util.Set;

/** Retain ownership through panel close/movement until the corresponding physical release. */
public final class PointerCapture {
    private final Set<Integer> buttons = new HashSet<>();
    public void capture(int button) { buttons.add(button); }
    public boolean release(int button) { return buttons.remove(button); }
    public boolean captured(int button) { return buttons.contains(button); }
}

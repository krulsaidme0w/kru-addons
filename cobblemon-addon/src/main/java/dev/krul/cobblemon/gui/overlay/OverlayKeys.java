package dev.krul.cobblemon.gui.overlay;

import java.util.HashSet;
import java.util.Set;
import static org.lwjgl.glfw.GLFW.*;

/** Keeps press/release together, including after an editor loses focus. */
public final class OverlayKeys {
    private final Set<Integer> held = new HashSet<>();
    public void capture(int key) { held.add(key); }
    public boolean held(int key) { return held.contains(key); }
    public boolean release(int key) { return held.remove(key); }
    public boolean text(OverlayContent content, int key, int action, int mods) {
        if (!content.textFocused() || (action != GLFW_PRESS && action != GLFW_REPEAT)) return false;
        // A repeat can arrive after focus moved here from the native screen. Its
        // original press still belongs there, so its release must pass through.
        if (action == GLFW_PRESS) capture(key);
        if (key == GLFW_KEY_ESCAPE) {
            if (action == GLFW_PRESS) content.blur();
        } else if (action != GLFW_REPEAT || (key != GLFW_KEY_ENTER && key != GLFW_KEY_KP_ENTER)) {
            content.key(key, mods);
        }
        return true;
    }
}

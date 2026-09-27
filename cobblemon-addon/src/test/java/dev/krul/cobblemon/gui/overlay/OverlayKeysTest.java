package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class OverlayKeysTest {
    private static final class Field implements OverlayContent {
        int submissions, edits, modifiers;
        boolean focused = true;
        public String title() { return "test"; }
        public int width() { return 1; }
        public int height() { return 1; }
        public void render(OverlayCanvas c, double x, double y) {}
        public boolean textFocused() { return focused; }
        public void blur() { focused = false; }
        public boolean key(int key, int mods) {
            modifiers = mods;
            if (key == GLFW_KEY_ENTER) submissions++;
            if (key == GLFW_KEY_BACKSPACE) edits++;
            return true;
        }
    }
    @Test void keyRepeatEditsButCannotSubmitTwice() {
        var keys = new OverlayKeys();
        var field = new Field();
        assertTrue(keys.text(field, GLFW_KEY_ENTER, GLFW_PRESS, 0));
        assertTrue(keys.text(field, GLFW_KEY_ENTER, GLFW_REPEAT, 0));
        assertEquals(1, field.submissions);
        keys.text(field, GLFW_KEY_BACKSPACE, GLFW_PRESS, GLFW_MOD_CONTROL);
        keys.text(field, GLFW_KEY_BACKSPACE, GLFW_REPEAT, GLFW_MOD_CONTROL);
        assertEquals(2, field.edits);
        assertEquals(GLFW_MOD_CONTROL, field.modifiers);
    }
    @Test void allKeysAreOwnedWhileTypingAndReleaseSurvivesBlur() {
        var keys = new OverlayKeys();
        var field = new Field();
        assertTrue(keys.text(field, GLFW_KEY_W, GLFW_PRESS, 0));
        field.blur();
        assertTrue(keys.release(GLFW_KEY_W));
        assertFalse(keys.release(GLFW_KEY_W));
        assertFalse(keys.text(field, GLFW_KEY_W, GLFW_PRESS, 0));
    }
    @Test void escapeOnlyBlursTextNotMenuOrPanel() {
        var keys = new OverlayKeys();
        var field = new Field();
        assertTrue(keys.text(field, GLFW_KEY_ESCAPE, GLFW_PRESS, 0));
        assertFalse(field.textFocused());
        assertTrue(keys.release(GLFW_KEY_ESCAPE));
    }
    @Test void repeatOfKeyPressedBeforeFocusDoesNotStealItsRelease() {
        var keys = new OverlayKeys();
        var field = new Field();
        assertTrue(keys.text(field, GLFW_KEY_W, GLFW_REPEAT, 0));
        assertFalse(keys.release(GLFW_KEY_W), "Underlying owner must receive release of its original press");
    }
}

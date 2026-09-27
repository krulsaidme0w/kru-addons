package dev.krul.cobblemon.pcui;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class CommandLineTest {
    @Test void sendsOnlyExplicitNonemptyCommandWithOneSlashRemoved() {
        var sent = new ArrayList<String>();
        var line = new CommandLine(value -> { sent.add(value); return true; });
        line.focus();
        line.insert("  /wondertrade  ");
        assertTrue(sent.isEmpty());
        line.key(GLFW_KEY_ENTER, 0, new Clipboard());
        assertEquals(java.util.List.of("wondertrade"), sent);
        assertEquals("", line.text());
        line.insert(" / ");
        line.key(GLFW_KEY_ENTER, 0, new Clipboard());
        assertEquals(1, sent.size());
        line.clear();
        line.focus();
        line.insert("gts");
        line.key(GLFW_KEY_ENTER, 0, new Clipboard());
        assertEquals(java.util.List.of("wondertrade", "gts"), sent);
    }

    @Test void replacementSelectionPasteAndDeleteEditWithoutSending() {
        var line = new CommandLine(value -> { fail("editing cannot send"); return false; });
        var clipboard = new Clipboard();
        line.focus();
        line.insert("daycare");
        line.key(GLFW_KEY_HOME, 0, clipboard);
        line.key(GLFW_KEY_RIGHT, GLFW_MOD_SHIFT, clipboard);
        line.insert("D");
        assertEquals("Daycare", line.text());
        line.key(GLFW_KEY_A, GLFW_MOD_CONTROL, clipboard);
        line.key(GLFW_KEY_C, GLFW_MOD_CONTROL, clipboard);
        assertEquals("Daycare", clipboard.value);
        clipboard.value = "/gts\n\r\u00a7a";
        line.key(GLFW_KEY_V, GLFW_MOD_CONTROL, clipboard);
        assertEquals("/gtsa", line.text());
        line.key(GLFW_KEY_BACKSPACE, 0, clipboard);
        assertEquals("/gts", line.text());
        line.key(GLFW_KEY_HOME, 0, clipboard);
        line.key(GLFW_KEY_DELETE, 0, clipboard);
        assertEquals("gts", line.text());
    }

    @Test void failedSendKeepsDraftAndBlurDisablesTyping() {
        var line = new CommandLine(value -> false);
        line.focus();
        line.insert("daycare");
        line.submit();
        assertEquals("daycare", line.text());
        line.blur();
        line.insert("x");
        assertEquals("daycare", line.text());
        line.clear();
        assertEquals("", line.text());
        assertFalse(line.focused());
    }

    @Test void unicodeNavigationDeletesOneCodePointAndCapsPaste() {
        var line = new CommandLine(value -> false);
        line.focus();
        line.insert("a\ud83d\ude00b");
        line.key(GLFW_KEY_LEFT, 0, new Clipboard());
        line.key(GLFW_KEY_BACKSPACE, 0, new Clipboard());
        assertEquals("ab", line.text());
        line.key(GLFW_KEY_A, GLFW_MOD_CONTROL, new Clipboard());
        line.insert("x".repeat(400));
        assertEquals(256, line.text().length());
    }

    private static final class Clipboard implements CommandLine.Clipboard {
        String value = "";
        public String get() { return value; }
        public void set(String value) { this.value = value; }
    }
}

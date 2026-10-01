package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;

class ComposerInputTest {
    @Test void typingDoesNotSubmitAndEnterSubmitsOnlyWhenComposerIsFocused() {
        TestTextBox composer = new TestTextBox();
        composer.theme = new MeteorGuiTheme() {
            @Override public double textWidth(String text, int length, boolean title) { return length; }
        };
        composer.width = 200;
        composer.set("Keep this draft until I confirm it");
        composer.setFocused(true);
        AtomicInteger submissions = new AtomicInteger();
        composer.action = () -> submissions.addAndGet(100);
        ComposerInput.configure(composer);

        assertTrue(composer.onCharTyped('x'));
        assertEquals(0, submissions.get());
        assertTrue(ComposerInput.submitOnEnter(composer, GLFW_KEY_ENTER, ignored -> submissions.incrementAndGet()));
        assertEquals(1, submissions.get());
        assertEquals("xKeep this draft until I confirm it", composer.get());
        assertFalse(composer.isFocused());
        assertFalse(ComposerInput.submitOnEnter(composer, GLFW_KEY_ENTER, ignored -> submissions.incrementAndGet()));
        assertEquals(1, submissions.get());
    }

    private static final class TestTextBox extends WTextBox {
        TestTextBox() { super("", (text, c) -> true, null); }
        @Override protected WContainer createCompletionsRootWidget() { return null; }
        @Override protected <T extends WWidget & ICompletionItem> T createCompletionsValueWidth(String completion, boolean selected) { return null; }
    }
}

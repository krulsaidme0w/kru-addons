package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import java.util.function.Consumer;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;

/** Keeps Enter submission scoped to the focused Builder composer. */
final class ComposerInput {
    private ComposerInput() {}

    static void configure(WTextBox composer) {
        composer.action = null;
        composer.actionOnUnfocused = null;
    }

    static boolean submitOnEnter(WTextBox composer, int keyCode, Consumer<String> submit) {
        if (!composer.isFocused() || (keyCode != GLFW_KEY_ENTER && keyCode != GLFW_KEY_KP_ENTER)) return false;
        String text = composer.get();
        composer.setFocused(false);
        submit.accept(text);
        return true;
    }
}

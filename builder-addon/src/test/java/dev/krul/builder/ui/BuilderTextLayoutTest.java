package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderTextLayoutTest {
    @Test void largeViewportAllowsOneAndHalfTimesWiderChatContent() {
        MeteorGuiTheme theme = new MeteorGuiTheme() {
            @Override public double scale(double value) { return value; }
            @Override public double textWidth(String text, int length, boolean title) { return length * 6.0; }
            @Override public double textHeight(boolean title) { return 10; }
        };
        assertEquals(960, BuilderTextLayout.wrapWidth(theme, 1600));
    }

    @Test void longUnbrokenPathTokenIsBrokenBeforeMeteorMultilineMeasurement() {
        MeteorGuiTheme theme = new MeteorGuiTheme() {
            @Override public double scale(double value) { return value; }
            @Override public double textWidth(String text, int length, boolean title) { return length * 6.0; }
            @Override public double textHeight(boolean title) { return 10; }
        };
        String raw = "Could not start process C:\\Users\\builder\\" + "very-long-path-segment".repeat(40);
        double maxWidth = 72;

        WLabel label = BuilderTextLayout.label(theme, "initial", maxWidth);
        label.set(raw);
        label.calculateSize();

        assertTrue(label.height > 10, "the real Meteor multiline label should wrap the long error");
        assertTrue(label.width <= maxWidth, "no measured line may exceed the safe wrapping width");

        ((BuilderWrappedLabel) label).setMaxWidth(36);
        label.set(raw);
        label.calculateSize();
        assertTrue(label.width <= 36, "retained labels must rewrap after the viewport narrows");
    }
}

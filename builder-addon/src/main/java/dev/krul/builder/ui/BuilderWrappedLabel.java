package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorMultiLabel;

/** Meteor multiline label that makes every update safe for the theme's word wrapper. */
final class BuilderWrappedLabel extends WMeteorMultiLabel {
    private String sourceText = "";

    BuilderWrappedLabel(double maxWidth) {
        super("", false, maxWidth);
    }

    @Override public void set(String text) {
        sourceText = text;
        super.set(BuilderTextLayout.breakLongTokens(theme, sourceText, maxWidth));
    }

    void setMaxWidth(double width) {
        if (Double.compare(maxWidth, width) == 0) return;
        maxWidth = width;
        super.set(BuilderTextLayout.breakLongTokens(theme, sourceText, maxWidth));
    }
}

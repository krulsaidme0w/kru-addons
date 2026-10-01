package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.GuiTheme;

/** Makes display text safe for Meteor 0.5.8's word-only multiline label wrapping. */
final class BuilderTextLayout {
    private BuilderTextLayout() {}

    static String breakLongTokens(GuiTheme theme, String text, double maxWidth) {
        double limit = theme.scale(maxWidth);
        String[] words = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').split(" ");
        StringBuilder result = new StringBuilder(text.length());
        boolean first = true;
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!first) result.append(' ');
            first = false;

            StringBuilder chunk = new StringBuilder();
            for (int offset = 0; offset < word.length();) {
                int codePoint = word.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                offset += Character.charCount(codePoint);
                if (theme.textWidth(glyph, glyph.length(), false) > limit) glyph = "?";

                String candidate = chunk + glyph;
                if (!chunk.isEmpty() && theme.textWidth(candidate, candidate.length(), false) > limit) {
                    result.append(chunk).append(' ');
                    chunk.setLength(0);
                }
                chunk.append(glyph);
            }
            result.append(chunk);
        }
        return result.toString();
    }

    static double wrapWidth(GuiTheme theme, int viewportWidth) {
        double unit = theme.scale(1);
        double available = Math.max(unit, viewportWidth - theme.scale(48));
        double width = Math.max(1, Math.min(theme.scale(960), available) / unit);
        return Math.max(width, theme.textWidth("W", 1, false) / unit);
    }

    static BuilderWrappedLabel label(GuiTheme theme, String text, double maxWidth) {
        BuilderWrappedLabel label = new BuilderWrappedLabel(maxWidth);
        label.theme = theme;
        label.init();
        label.set(text);
        return label;
    }
}

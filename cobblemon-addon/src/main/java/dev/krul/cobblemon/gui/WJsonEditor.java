package dev.krul.cobblemon.gui;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

import static org.lwjgl.glfw.GLFW.*;

/** Small multiline editor for bounded JSON profiles. It deliberately has no clipboard or file side effects. */
public final class WJsonEditor extends WWidget {
    private static final Color BACKGROUND = new Color(0xE8141418);
    private static final Color LINE_NUMBER = new Color(0xFF77777F);
    private static final Color TEXT = new Color(0xFFF0F0F2);
    private static final Color CARET = new Color(0xFFFF9CA3);
    private static final int MAX_LINES = 16;

    private String text;
    private int cursor;
    private int selectionStart;
    private int selectionEnd;
    private int firstLine;
    private boolean focused;

    public Runnable action;

    public WJsonEditor(String text) {
        this.text = text == null ? "" : text;
        cursor = this.text.length();
        selectionStart = cursor;
        selectionEnd = cursor;
    }

    public String get() { return text; }

    @Override
    protected void onCalculateSize() {
        width = Math.max(width, theme.scale(620));
        height = theme.pad() * 2 + theme.textHeight() * MAX_LINES;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, BACKGROUND);
        double lineHeight = theme.textHeight();
        double left = x + theme.pad();
        double top = y + theme.pad();
        String[] lines = text.split("\\n", -1);
        int end = Math.min(lines.length, firstLine + MAX_LINES);
        for (int i = firstLine; i < end; i++) {
            double lineY = top + (i - firstLine) * lineHeight;
            renderer.text(Integer.toString(i + 1), left, lineY, LINE_NUMBER, false);
            renderer.text(lines[i], left + theme.scale(34), lineY, TEXT, false);
        }

        if (focused) {
            int line = lineOf(cursor);
            if (line >= firstLine && line < firstLine + MAX_LINES) {
                int column = cursor - lineStart(line);
                String prefix = lines[line].substring(0, Math.min(column, lines[line].length()));
                double caretX = left + theme.scale(34) + theme.textWidth(prefix);
                double caretY = top + (line - firstLine) * lineHeight;
                renderer.quad(caretX, caretY, theme.scale(1), lineHeight, CARET);
            }
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        if (!isOver(mouseX, mouseY) || button != GLFW_MOUSE_BUTTON_LEFT) {
            focused = false;
            return false;
        }
        focused = true;
        double lineHeight = theme.textHeight();
        int line = firstLine + (int) ((mouseY - y - theme.pad()) / lineHeight);
        line = Math.max(0, Math.min(line, lineCount() - 1));
        String[] lines = text.split("\\n", -1);
        double relativeX = mouseX - x - theme.pad() - theme.scale(34);
        int column = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= lines[line].length(); i++) {
            double difference = Math.abs(theme.textWidth(lines[line].substring(0, i)) - relativeX);
            if (difference < best) { best = difference; column = i; }
        }
        cursor = lineStart(line) + column;
        selectionStart = selectionEnd = cursor;
        return true;
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        if (!isOver(x + width / 2, y + height / 2)) return false;
        firstLine = Math.max(0, Math.min(firstLine - (int) Math.signum(amount), Math.max(0, lineCount() - MAX_LINES)));
        return true;
    }

    @Override
    public boolean onKeyPressed(int key, int mods) {
        if (!focused) return false;
        boolean control = (mods & (GLFW_MOD_CONTROL | GLFW_MOD_SUPER)) != 0;
        if (control && key == GLFW_KEY_A) { selectionStart = 0; selectionEnd = text.length(); cursor = text.length(); return true; }
        if (control && key == GLFW_KEY_C) { copySelection(); return true; }
        if (control && key == GLFW_KEY_X) { copySelection(); deleteSelection(); return true; }
        if (control && key == GLFW_KEY_V) { replaceSelection(MeteorClient.mc.keyboard.getClipboard()); return true; }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) { replaceSelection("\n"); return true; }
        if (key == GLFW_KEY_BACKSPACE) { backspace(); return true; }
        if (key == GLFW_KEY_DELETE) { delete(); return true; }
        if (key == GLFW_KEY_LEFT) { moveHorizontal(-1); return true; }
        if (key == GLFW_KEY_RIGHT) { moveHorizontal(1); return true; }
        if (key == GLFW_KEY_UP) { moveVertical(-1); return true; }
        if (key == GLFW_KEY_DOWN) { moveVertical(1); return true; }
        if (key == GLFW_KEY_HOME) { cursor = lineStart(lineOf(cursor)); selectionStart = selectionEnd = cursor; return true; }
        if (key == GLFW_KEY_END) { cursor = lineEnd(lineOf(cursor)); selectionStart = selectionEnd = cursor; return true; }
        return false;
    }

    @Override
    public boolean onKeyRepeated(int key, int mods) { return onKeyPressed(key, mods); }

    @Override
    public boolean onCharTyped(char c) {
        if (!focused || Character.isISOControl(c)) return false;
        replaceSelection(String.valueOf(c));
        return true;
    }

    private void replaceSelection(String value) {
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        text = text.substring(0, start) + value + text.substring(end);
        cursor = start + value.length();
        selectionStart = selectionEnd = cursor;
        ensureCursorVisible();
        if (action != null) action.run();
    }

    private void deleteSelection() { if (selectionStart != selectionEnd) replaceSelection(""); }
    private void backspace() {
        if (selectionStart != selectionEnd) { deleteSelection(); return; }
        if (cursor > 0) { selectionStart = cursor - 1; selectionEnd = cursor; deleteSelection(); }
    }
    private void delete() {
        if (selectionStart != selectionEnd) { deleteSelection(); return; }
        if (cursor < text.length()) { selectionStart = cursor; selectionEnd = cursor + 1; deleteSelection(); }
    }
    private void moveHorizontal(int delta) {
        if (selectionStart != selectionEnd) cursor = delta < 0 ? Math.min(selectionStart, selectionEnd) : Math.max(selectionStart, selectionEnd);
        else cursor = Math.max(0, Math.min(text.length(), cursor + delta));
        selectionStart = selectionEnd = cursor;
        ensureCursorVisible();
    }
    private void moveVertical(int delta) {
        int line = Math.max(0, Math.min(lineCount() - 1, lineOf(cursor) + delta));
        int column = cursor - lineStart(lineOf(cursor));
        cursor = Math.min(lineEnd(line), lineStart(line) + column);
        selectionStart = selectionEnd = cursor;
        ensureCursorVisible();
    }
    private void copySelection() {
        if (selectionStart != selectionEnd) {
            MeteorClient.mc.keyboard.setClipboard(text.substring(Math.min(selectionStart, selectionEnd), Math.max(selectionStart, selectionEnd)));
        }
    }
    private int lineCount() { return text.split("\\n", -1).length; }
    private int lineOf(int position) { return (int) text.substring(0, Math.max(0, Math.min(position, text.length()))).chars().filter(c -> c == '\n').count(); }
    private int lineStart(int line) { int current = 0; for (int i = 0; i < line; i++) { int next = text.indexOf('\n', current); if (next < 0) return text.length(); current = next + 1; } return current; }
    private int lineEnd(int line) { int end = text.indexOf('\n', lineStart(line)); return end < 0 ? text.length() : end; }
    private void ensureCursorVisible() { int line = lineOf(cursor); if (line < firstLine) firstLine = line; else if (line >= firstLine + MAX_LINES) firstLine = line - MAX_LINES + 1; }
}

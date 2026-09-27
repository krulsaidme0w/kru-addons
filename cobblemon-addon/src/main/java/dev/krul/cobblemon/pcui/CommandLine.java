package dev.krul.cobblemon.pcui;

import java.util.function.Predicate;
import static org.lwjgl.glfw.GLFW.*;

/** Small single-line editor. Transport and clipboard stay outside its state. */
final class CommandLine {
    interface Clipboard { String get(); void set(String value); }
    private static final int LIMIT = 256;
    private final Predicate<String> send;
    private final boolean stripSlash;
    private String text = "";
    private int cursor, anchor;
    private boolean focused;
    CommandLine(Predicate<String> send) { this(send, true); }
    CommandLine(Predicate<String> send, boolean stripSlash) { this.send = send; this.stripSlash = stripSlash; }
    String text() { return text; }
    int cursor() { return cursor; }
    int selectionStart() { return Math.min(anchor, cursor); }
    int selectionEnd() { return Math.max(anchor, cursor); }
    boolean focused() { return focused; }
    void focus() { focused = true; }
    void blur() { focused = false; }
    void clear() { text = ""; cursor = anchor = 0; focused = false; }

    void placeCursor(int index, boolean extend) {
        cursor = Math.clamp(index, 0, text.length());
        if (cursor > 0 && cursor < text.length() && Character.isLowSurrogate(text.charAt(cursor))) cursor--;
        if (!extend) anchor = cursor;
    }
    void insert(String value) {
        if (!focused || value == null) return;
        var filtered = new StringBuilder();
        int capacity = LIMIT - text.length() + selectionEnd() - selectionStart();
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint < 32 || codePoint == 127 || codePoint == 0xA7
                || Character.isISOControl(codePoint) || codePoint >= 0xD800 && codePoint <= 0xDFFF) continue;
            if (filtered.length() + Character.charCount(codePoint) > capacity) break;
            filtered.appendCodePoint(codePoint);
        }
        replaceSelection(filtered.toString());
    }
    private void replaceSelection(String value) {
        int start = selectionStart(), end = selectionEnd();
        text = text.substring(0, start) + value + text.substring(end);
        cursor = anchor = start + value.length();
    }
    void submit() {
        String command = text.trim();
        if (command.equals("/")) return;
        if (stripSlash && command.startsWith("/")) command = command.substring(1).trim();
        if (command.isEmpty()) return;
        if (send.test(command)) { text = ""; cursor = anchor = 0; }
    }
    boolean key(int key, int mods, Clipboard clipboard) {
        if (!focused) return false;
        boolean control = (mods & (GLFW_MOD_CONTROL | GLFW_MOD_SUPER)) != 0;
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
        if (control) {
            switch (key) {
                case GLFW_KEY_A -> { anchor = 0; cursor = text.length(); return true; }
                case GLFW_KEY_C -> { clipboard.set(text.substring(selectionStart(), selectionEnd())); return true; }
                case GLFW_KEY_X -> {
                    clipboard.set(text.substring(selectionStart(), selectionEnd()));
                    replaceSelection(""); return true;
                }
                case GLFW_KEY_V -> { insert(clipboard.get()); return true; }
            }
        }
        switch (key) {
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> submit();
            case GLFW_KEY_HOME -> placeCursor(0, shift);
            case GLFW_KEY_END -> placeCursor(text.length(), shift);
            case GLFW_KEY_LEFT, GLFW_KEY_RIGHT -> {
                int direction = key == GLFW_KEY_LEFT ? -1 : 1;
                if (!shift && selectionStart() != selectionEnd()) placeCursor(direction < 0 ? selectionStart() : selectionEnd(), false);
                else placeCursor(next(cursor, direction, control), shift);
            }
            case GLFW_KEY_BACKSPACE, GLFW_KEY_DELETE -> {
                if (selectionStart() == selectionEnd()) anchor = next(cursor, key == GLFW_KEY_BACKSPACE ? -1 : 1, control);
                replaceSelection("");
            }
            default -> { return false; }
        }
        return true;
    }
    private int next(int index, int direction, boolean word) {
        if (direction < 0) {
            if (!word) return index > 0 ? text.offsetByCodePoints(index, -1) : 0;
            while (index > 0 && Character.isWhitespace(text.codePointBefore(index))) index = text.offsetByCodePoints(index, -1);
            while (index > 0 && !Character.isWhitespace(text.codePointBefore(index))) index = text.offsetByCodePoints(index, -1);
        } else {
            if (!word) return index < text.length() ? text.offsetByCodePoints(index, 1) : index;
            while (index < text.length() && !Character.isWhitespace(text.codePointAt(index))) index = text.offsetByCodePoints(index, 1);
            while (index < text.length() && Character.isWhitespace(text.codePointAt(index))) index = text.offsetByCodePoints(index, 1);
        }
        return index;
    }
}

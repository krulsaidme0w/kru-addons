package dev.krul.cobblemon.pcui.daycare;

import java.util.*;
import java.io.*;
import java.util.regex.Pattern;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.Role;

final class AcademyDaycareProfile {
    private final Properties data;
    private AcademyDaycareProfile(Properties data) { this.data = data; }
    static AcademyDaycareProfile load() {
        try (InputStream in = AcademyDaycareProfile.class.getResourceAsStream("/profiles/academy-daycare-controls.properties")) {
            if (in == null) throw new IllegalStateException("Academy daycare profile missing");
            Properties data = new Properties(); data.load(in);
            return new AcademyDaycareProfile(data);
        } catch (IOException failure) { throw new IllegalStateException("Academy daycare profile unavailable", failure); }
    }
    int number(String key) { return Integer.parseInt(data.getProperty(key)); }
    String value(String key) { return data.getProperty(key); }
    int pen(String title, int containerSlots, int totalSlots, boolean backpackControls) {
        if (title == null || title.isEmpty() || backpackControls || containerSlots != number("container-slots")
            || totalSlots != number("total-slots")) return -1;
        int digit = title.codePointBefore(title.length()) - number("pen-codepoint-base");
        return digit >= 1 && digit <= number("pen-count") ? digit : -1;
    }
    Role role(String item, boolean empty) {
        if (empty && item.equals(value("empty-item"))) return Role.EMPTY;
        return item.equals(value("parent-item")) ? Role.OCCUPIED : Role.UNKNOWN;
    }
    boolean pickerTitle(String title) { return normalize(title).matches(value("picker-title-regex")); }
    int confirmation(String text, boolean insert, List<String> labels) {
        String content = normalize(text);
        if (!Pattern.compile(value("dialogue-context-regex")).matcher(content).find()
            || !Pattern.compile(value(insert ? "insert-context-regex" : "out-context-regex")).matcher(content).find()
            || labels.size() != 2) return -1;
        int positive = -1, negative = -1;
        for (int i = 0; i < labels.size(); i++) {
            String label = normalize(labels.get(i));
            if (label.matches(value("positive-option-regex"))) { if (positive >= 0) return -1; positive = i; }
            else if (label.matches(value("negative-option-regex"))) { if (negative >= 0) return -1; negative = i; }
            else return -1;
        }
        return positive >= 0 && negative >= 0 ? positive : -1;
    }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }
}

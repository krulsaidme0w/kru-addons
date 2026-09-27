package dev.krul.cobblemon.pcui;

import java.util.List;
import java.util.ArrayList;

/** Drawing and hit testing share these rectangles. */
final class UiToolsLayout {
    enum Action { SEND, COPY, CLOSE, SAVE, LOAD, PACKETS, DELAY, MORE, DESYNC, EXIT, FLUSH, CLEAR, FABRICATE, DISCONNECT, PC_IN, PC_OUT }
    record Control(Action action, int x, int y, int width) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + 20; }
    }
    static List<Control> controls(boolean advanced, boolean pc) {
        var controls = new ArrayList<Control>();
        controls.add(new Control(Action.SEND, 250, 8, 52));
        row(controls, 35, Action.COPY, Action.CLOSE);
        row(controls, 62, Action.SAVE, Action.LOAD);
        row(controls, 89, Action.PACKETS, Action.DELAY);
        controls.add(new Control(Action.MORE, 8, 134, 294));
        if (advanced) {
            row(controls, 161, Action.DESYNC, Action.EXIT);
            row(controls, 188, Action.FLUSH, Action.CLEAR);
            row(controls, 215, Action.FABRICATE, Action.DISCONNECT);
        }
        if (pc) row(controls, advanced ? 242 : 161, Action.PC_IN, Action.PC_OUT);
        return List.copyOf(controls);
    }
    private static void row(List<Control> controls, int y, Action left, Action right) {
        controls.add(new Control(left, 8, y, 144));
        controls.add(new Control(right, 158, y, 144));
    }
    static int height(boolean advanced, boolean pc) { return pc ? (advanced ? 303 : 222) : (advanced ? 276 : 195); }
}

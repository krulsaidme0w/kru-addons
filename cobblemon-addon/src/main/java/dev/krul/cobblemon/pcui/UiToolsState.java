package dev.krul.cobblemon.pcui;

import java.util.Objects;
import java.util.function.Predicate;

/** Draft belongs to a connection; focus and actions belong to the current menu. */
final class UiToolsState {
    final CommandLine line;
    private UiActionScope scope;
    private Object world, connection;
    UiToolsState(Predicate<String> send) { line = new CommandLine(send, false); }
    UiActionScope scope() { return scope; }
    void update(UiActionScope next) {
        if (next != null && (world != next.world() || connection != next.connection())) {
            line.clear();
            world = next.world(); connection = next.connection();
        }
        if (!Objects.equals(scope, next)) line.blur();
        scope = next;
    }
    void reset() { scope = null; world = connection = null; line.clear(); }
}

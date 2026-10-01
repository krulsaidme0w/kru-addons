package dev.krul.builder.minecraft;

import java.util.Objects;

/** Pure transition for reporting a continuous Builder failure only once. */
final class BuilderFailureDedupe {
    record State(String failure) {
        static State empty() { return new State(null); }
    }

    record Transition(State state, String report) {}

    private BuilderFailureDedupe() {}

    static Transition advance(State previous, boolean failed, String message) {
        Objects.requireNonNull(previous);
        if (!failed || message == null || message.isBlank()) return new Transition(State.empty(), null);
        if (message.equals(previous.failure())) return new Transition(previous, null);
        return new Transition(new State(message), message);
    }
}

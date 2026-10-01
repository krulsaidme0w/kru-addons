package dev.krul.builder.archive;

import java.util.Objects;

/** Safe, actionable operation failure that can be shown and stored in a conversation. */
public record BuilderDiagnostic(Category category, String operation, String cause, String detail,
                                String nextAction, long occurredAtEpochMillis, int occurrences) {
    public enum Category { SCAN_CONTEXT, AUTH_TRANSPORT, MODEL_TOOL, INVALID_PROPOSAL, LOCAL_STORAGE, CANCELLED }

    public BuilderDiagnostic {
        Objects.requireNonNull(category, "category");
        operation = checked(operation, "operation", 128, false);
        cause = checked(cause, "cause", 2048, false);
        detail = checked(detail, "detail", 8192, true);
        nextAction = checked(nextAction, "nextAction", 2048, true);
        if (occurredAtEpochMillis < 0) throw new IllegalArgumentException("Diagnostic time must be nonnegative");
        if (occurrences < 1) throw new IllegalArgumentException("Diagnostic occurrences must be positive");
    }

    private static String checked(String value, String name, int max, boolean allowEmpty) {
        Objects.requireNonNull(value, name);
        if (value.length() > max || (!allowEmpty && value.isBlank()))
            throw new IllegalArgumentException("Invalid diagnostic " + name);
        return value;
    }
}

package dev.krul.builder.archive;

import java.util.Locale;
import java.util.Objects;

/** Converts transport/validation strings to stable, sanitized chat diagnostics. */
public final class BuilderDiagnosticMapper {
    private BuilderDiagnosticMapper() {}

    public static BuilderDiagnostic fromAgentFailure(String message, long occurredAtEpochMillis) {
        String detail = concise(redact(Objects.requireNonNullElse(message, "Builder request failed")), 8192);
        String lower = detail.toLowerCase(Locale.ROOT);
        if (lower.contains("proposal") || lower.contains("validation") || lower.contains("out_of_bounds")
                || lower.contains("invalid plan")) {
            return new BuilderDiagnostic(BuilderDiagnostic.Category.INVALID_PROPOSAL, "Plan validation",
                concise(detail, 2048), detail, "Review the reported cells and keep every target inside the selected bounds",
                occurredAtEpochMillis, 1);
        }
        if (lower.contains("login") || lower.contains("sign in") || lower.contains("auth")
                || lower.contains("connection") || lower.contains("transport")) {
            return new BuilderDiagnostic(BuilderDiagnostic.Category.AUTH_TRANSPORT, "Codex connection",
                concise(detail, 2048), detail, "Check the Codex sign-in and connection, then retry the request",
                occurredAtEpochMillis, 1);
        }
        return new BuilderDiagnostic(BuilderDiagnostic.Category.MODEL_TOOL, "Agent request",
            concise(detail, 2048), detail, "Review the failure reason and retry; the world was not changed",
            occurredAtEpochMillis, 1);
    }

    public static BuilderDiagnostic fromFailure(BuilderDiagnostic.Category category, String operation,
                                                Throwable failure, String nextAction, long occurredAtEpochMillis) {
        Objects.requireNonNull(failure);
        String raw = failure.getMessage();
        String detail = concise(redact(raw == null || raw.isBlank() ? failure.getClass().getSimpleName() : raw), 8192);
        return new BuilderDiagnostic(category, operation, concise(detail, 2048), detail, nextAction,
            occurredAtEpochMillis, 1);
    }

    private static String redact(String value) {
        return value.replaceAll("(?i)(authorization\\s*[:=]\\s*)(bearer\\s+)[^\\s,;\\\"}]+", "$1$2[redacted]")
            .replaceAll("(?i)(bearer\\s+)[^\\s,;\\\"}]+", "$1[redacted]")
            .replaceAll("(?i)([\\\"]?(?:api[_-]?key|(?:access|refresh|id)?[_-]?token|client[_-]?secret|usercode)[\\\"]?\\s*[:=]\\s*[\\\"]?)[^\\s,;\\\"}]+", "$1[redacted]");
    }

    private static String concise(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}

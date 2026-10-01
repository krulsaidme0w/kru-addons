package dev.krul.builder.ui;

import dev.krul.builder.archive.BuilderDiagnostic;
import java.net.URI;

final class BuilderUiPresentation {
    enum PrimaryAction { CONNECT, SEND, CANCEL }
    record SignInActions(boolean openPage, boolean copyCode) {}

    private BuilderUiPresentation() {}

    static PrimaryAction primaryAction(BuilderUiState.Phase phase, boolean authenticated, boolean prepared) {
        if (phase == BuilderUiState.Phase.PREPARING || phase == BuilderUiState.Phase.WAITING)
            return PrimaryAction.CANCEL;
        if (prepared) return PrimaryAction.CANCEL;
        return authenticated ? PrimaryAction.SEND : PrimaryAction.CONNECT;
    }

    static String statusSummary(String status, boolean failed) {
        String visible = failed ? "Request failed · " + failureDetail(status) : status;
        return visible.length() <= 180 ? visible : visible.substring(0, 179) + "…";
    }

    static String failureDetail(String status) {
        String visible = redact(status);
        return visible.length() <= 180 ? visible : visible.substring(0, 179) + "…";
    }

    static String diagnosticText(BuilderDiagnostic diagnostic) {
        var text = new StringBuilder(diagnostic.operation()).append(" failed: ").append(diagnostic.cause());
        if (!diagnostic.detail().isBlank() && !diagnostic.detail().equals(diagnostic.cause()))
            text.append('\n').append(diagnostic.detail());
        if (!diagnostic.nextAction().isBlank()) text.append("\nNext: ").append(diagnostic.nextAction());
        return text.toString();
    }

    private static String redact(String value) {
        return value.replaceAll("(?i)(authorization\\s*[:=]\\s*)(bearer\\s+)[^\\s,;]+", "$1$2[redacted]")
            .replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[redacted]")
            .replaceAll("(?i)(authorization\\s*[:=]\\s*)(?!bearer\\s)[^\\s,;]+", "$1[redacted]")
            .replaceAll("(?i)(api[_-]?key|(?:access|refresh|id)?[_-]?token|client[_-]?secret|usercode)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[redacted]");
    }

    static SignInActions signInActions(URI authorizationUrl, String userCode) {
        return new SignInActions(authorizationUrl != null, userCode != null && !userCode.isBlank());
    }
}

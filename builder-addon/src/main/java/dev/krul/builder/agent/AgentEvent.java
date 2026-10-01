package dev.krul.builder.agent;

import dev.krul.builder.project.BuildPlan;

import java.net.URI;
import java.util.Objects;

public sealed interface AgentEvent permits AgentEvent.Status, AgentEvent.TextDelta,
        AgentEvent.Proposal, AgentEvent.Failed, AgentEvent.Completed {
    record Status(AgentClient.Connection connection, AgentClient.Login login, String message,
                  URI authorizationUrl, String userCode) implements AgentEvent {
        public Status { Objects.requireNonNull(connection); Objects.requireNonNull(login); }
    }
    record TextDelta(String text) implements AgentEvent { public TextDelta { Objects.requireNonNull(text); } }
    record Proposal(BuildPlan plan, boolean visualReviewed, String reviewFailure) implements AgentEvent {
        public Proposal(BuildPlan plan) { this(plan, false, "Plan preview was not inspected"); }
        public Proposal { Objects.requireNonNull(plan); reviewFailure = reviewFailure == null || reviewFailure.isBlank() ? null : reviewFailure; }
    }
    record Failed(String message) implements AgentEvent { public Failed { Objects.requireNonNull(message); } }
    record Completed(String message) implements AgentEvent { public Completed { Objects.requireNonNull(message); } }
}

package dev.krul.builder.agent;

import java.util.function.Consumer;

public interface AgentClient extends AutoCloseable {
    enum Connection { DISCONNECTED, STARTING, READY, INCOMPATIBLE }
    enum Login { UNKNOWN, REQUIRED, PENDING, AUTHENTICATED }

    record Status(Connection connection, Login login, String detail) {}

    void start(AgentRequest request, Consumer<AgentEvent> events);
    void cancel();
    Status status();
    /** Starts the official Codex login flow in the addon-owned profile. */
    void login(Consumer<AgentEvent> events);
    @Override void close();
}

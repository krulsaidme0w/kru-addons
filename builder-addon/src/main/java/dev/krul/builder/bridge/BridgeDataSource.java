package dev.krul.builder.bridge;

import java.util.concurrent.CompletionStage;

/** Implementations marshal all Minecraft access to the client thread. */
public interface BridgeDataSource {
    CompletionStage<BridgeProtocol.Response> status(BridgeProtocol.Request request);
    CompletionStage<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request);
    CompletionStage<BridgeProtocol.Response> captureView(BridgeProtocol.Request request);
}

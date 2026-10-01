package dev.krul.builder.bridge;

import java.util.concurrent.CompletionStage;

/** Implementations marshal all Minecraft access to the client thread. */
public interface BridgeDataSource {
    CompletionStage<BridgeProtocol.Response> status(BridgeProtocol.Request request);
    CompletionStage<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request);
    CompletionStage<BridgeProtocol.Response> captureView(BridgeProtocol.Request request);
    default CompletionStage<BridgeProtocol.Response> listBlockMods(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Block catalog unavailable")); }
    default CompletionStage<BridgeProtocol.Response> searchBlocks(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Block catalog unavailable")); }
    default CompletionStage<BridgeProtocol.Response> describeBlock(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Block catalog unavailable")); }
    default CompletionStage<BridgeProtocol.Response> inspectPlan(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Plan preview unavailable")); }
    default CompletionStage<BridgeProtocol.Response> publishPlan(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Plan preview unavailable")); }
    default CompletionStage<BridgeProtocol.Response> planStatus(BridgeProtocol.Request request) { return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Plan preview unavailable")); }
}

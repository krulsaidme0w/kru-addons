package dev.krul.builder.bridge;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class BuilderBridgeServerTest {
    @Test void bindsOnlyToIpv4LoopbackAndRequiresPairingForWorldRoutes() throws Exception {
        var pairing = new BridgePairing(Duration.ofSeconds(30), System::currentTimeMillis,
            () -> "123456", () -> "0123456789abcdef0123456789abcdef");
        var server = new BuilderBridgeServer(pairing);
        var source = new BridgeDataSource() {
            public CompletableFuture<BridgeProtocol.Response> status(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
            public CompletableFuture<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
            public CompletableFuture<BridgeProtocol.Response> captureView(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
            private BridgeProtocol.Response ok() { return new BridgeProtocol.Response(1, "id", true, 1, 1, "ok", null, null); }
        };
        try {
            InetSocketAddress address = server.start(source);
            assertTrue(address.getAddress().isLoopbackAddress());
            assertEquals("127.0.0.1", address.getAddress().getHostAddress());
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + address.getPort() + "/v1/status"))
                .POST(HttpRequest.BodyPublishers.ofString(BridgeProtocol.encodeRequest(new BridgeProtocol.Request(1,"id",BridgeProtocol.Method.STATUS,null,0,0,0,0,null))))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        } finally { server.close(); }
    }

    @Test void pairingTokenIsRetrievedOnceAndAllowsAuthenticatedStatus() throws Exception {
        var pairing = new BridgePairing(Duration.ofSeconds(30), System::currentTimeMillis,
            () -> "654321", () -> "0123456789abcdef0123456789abcdef");
        var server = new BuilderBridgeServer(pairing);
        var source = new BridgeDataSource() {
            public CompletableFuture<BridgeProtocol.Response> status(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(new BridgeProtocol.Response(1, request.id(), true, 1, 1, "ok", null, null)); }
            public CompletableFuture<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request) { return status(request); }
            public CompletableFuture<BridgeProtocol.Response> captureView(BridgeProtocol.Request request) { return status(request); }
        };
        HttpClient client = HttpClient.newHttpClient();
        try {
            int port = server.start(source).getPort();
            String base = "http://127.0.0.1:" + port;
            var pairResponse = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/pair")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(202, pairResponse.statusCode());
            assertTrue(pairing.approve("654321"));
            var status = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/pair/status?code=654321")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(status.body().contains("0123456789abcdef0123456789abcdef"));
            var authorized = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/status"))
                .header("Authorization", "Bearer 0123456789abcdef0123456789abcdef")
                .POST(HttpRequest.BodyPublishers.ofString(BridgeProtocol.encodeRequest(new BridgeProtocol.Request(1,"id",BridgeProtocol.Method.STATUS,null,0,0,0,0,null))))
                .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authorized.statusCode());
            assertTrue(authorized.body().contains("\"worldGeneration\":1"));
        } finally { server.close(); }
    }
}

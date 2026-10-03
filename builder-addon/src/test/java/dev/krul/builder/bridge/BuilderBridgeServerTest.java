package dev.krul.builder.bridge;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class BuilderBridgeServerTest {
    @Test void peerPolicyAcceptsOnlyLoopbackAddresses() throws Exception {
        Method policy = assertDoesNotThrow(() -> BuilderBridgeServer.class.getDeclaredMethod("isLoopback", InetAddress.class));
        policy.setAccessible(true);
        assertTrue(invokePolicy(policy, InetAddress.getByName("127.0.0.1")));
        assertFalse(invokePolicy(policy, InetAddress.getByName("192.0.2.10")));
    }

    @Test void pagedItemCatalogRoutesReachTheirExactReadOnlyHandlersWithoutBearer() throws Exception {
        var server = new BuilderBridgeServer(0);
        var source = new BridgeDataSource() {
            public CompletableFuture<BridgeProtocol.Response> status(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            public CompletableFuture<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            public CompletableFuture<BridgeProtocol.Response> captureView(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            public CompletableFuture<BridgeProtocol.Response> listItemMods(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            public CompletableFuture<BridgeProtocol.Response> searchItems(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            public CompletableFuture<BridgeProtocol.Response> describeItem(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok(request)); }
            private BridgeProtocol.Response ok(BridgeProtocol.Request request) {
                return new BridgeProtocol.Response(1, request.id(), true, 1, 1, "ok", request.itemId(), null);
            }
        };
        try {
            int port = server.start(source).getPort(); HttpClient client = HttpClient.newHttpClient();
            String[][] routes = {{"/v1/catalog/items/mods", "LIST_ITEM_MODS"},
                {"/v1/catalog/items/search", "SEARCH_ITEMS"}, {"/v1/catalog/items/describe", "DESCRIBE_ITEM"}};
            for (int i = 0; i < routes.length; i++) {
                String id = "mod:item_" + i;
                var request = new BridgeProtocol.Request(1, "items-" + i, BridgeProtocol.Method.valueOf(routes[i][1]),
                    null, 0, 20, 0, 0, "item", "mod", null, id, null, null);
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + routes[i][0]))
                    .POST(HttpRequest.BodyPublishers.ofString(BridgeProtocol.encodeRequest(request))).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), routes[i][0]);
                assertTrue(response.body().contains(id), routes[i][0]);
            }
        } finally { server.close(); }
    }

    @Test void bindsOnlyToIpv4LoopbackAndServesLocalWorldRoutesWithoutBearer() throws Exception {
        var server = new BuilderBridgeServer(0);
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
                .POST(HttpRequest.BodyPublishers.ofString(BridgeProtocol.encodeRequest(new BridgeProtocol.Request(1,"id",BridgeProtocol.Method.STATUS,null,0,0,0,0))))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"worldGeneration\":1"));
        } finally { server.close(); }
    }

    @Test void pairingEndpointsAndWorldMutationRoutesAreNotExposed() throws Exception {
        var server = new BuilderBridgeServer(0);
        try {
            var source = new BridgeDataSource() {
                public CompletableFuture<BridgeProtocol.Response> status(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
                public CompletableFuture<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
                public CompletableFuture<BridgeProtocol.Response> captureView(BridgeProtocol.Request request) { return CompletableFuture.completedFuture(ok()); }
                private BridgeProtocol.Response ok() { return new BridgeProtocol.Response(1, "id", true, 1, 1, "ok", null, null); }
            };
            int port = server.start(source).getPort();
            HttpClient client = HttpClient.newHttpClient();
            for (String path : new String[]{"/v1/pair", "/v1/pair/status", "/v1/world/place", "/v1/world/break"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode(), path);
            }
        } finally { server.close(); }
    }

    private static boolean invokePolicy(Method policy, InetAddress address) throws Exception {
        try { return (boolean) policy.invoke(null, address); }
        catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
    }
}

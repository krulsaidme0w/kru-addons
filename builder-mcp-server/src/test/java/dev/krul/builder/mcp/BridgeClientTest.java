package dev.krul.builder.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class BridgeClientTest {
    private static final String PNG = Base64.getEncoder().encodeToString(new byte[]{(byte)137,80,78,71,13,10,26,10});

    @Test void sendsBoundedReadAndCarriesSnapshotIdentity() throws Exception {
        var server = start(0);
        server.createContext("/v1/selection", exchange -> {
            assertEquals("Bearer secret-token", exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("expectedWorldGeneration\":7"));
            byte[] response = "{\"version\":1,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":7,\"snapshotRevision\":9,\"message\":\"ok\",\"payload\":\"{}\",\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1)); client.setToken("secret-token");
            var bounds = new BridgeClient.Bounds(new BridgeClient.Coordinate(0,0,0), new BridgeClient.Coordinate(1,0,0));
            var reply = client.readSelection(bounds, 0, 20, 7, 9);
            assertEquals(7, reply.worldGeneration()); assertEquals(9, reply.snapshotRevision());
        } finally { server.stop(0); }
    }

    @Test void rejectsProtocolVersionMismatchAndConnectionFailure() throws Exception {
        var server = start(0);
        server.createContext("/v1/status", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"version\":8,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":1,\"snapshotRevision\":1,\"message\":\"ok\",\"payload\":null,\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1)); client.setToken("secret-token");
            assertThrows(BridgeClient.BridgeException.class, client::status);
        } finally { server.stop(0); }
        HttpServer closed = start(0); int port = closed.getAddress().getPort(); closed.stop(0);
        assertThrows(BridgeClient.BridgeException.class, () -> new BridgeClient(URI.create("http://127.0.0.1:"+port), Duration.ofMillis(100)).status());
    }

    @Test void enforcesTimeoutAndValidatesImageSignatureAndSize() throws Exception {
        var server = start(0);
        server.createContext("/v1/status", exchange -> { try { Thread.sleep(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } exchange.close(); });
        server.createContext("/v1/capture", exchange -> {
            byte[] bytes = ("{\"version\":1,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":1,\"snapshotRevision\":1,\"message\":\"ok\",\"payload\":null,\"imageBase64\":\""+PNG+"\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            BridgeClient timedClient = new BridgeClient(base(server), Duration.ofMillis(80)); timedClient.setToken("secret-token");
            assertThrows(BridgeClient.BridgeException.class, timedClient::status);
            BridgeClient imageClient = new BridgeClient(base(server), Duration.ofSeconds(1)); imageClient.setToken("secret-token");
            assertEquals(8, imageClient.captureView(0,0).png().length);
        } finally { server.stop(0); }
    }

    @Test void performsExplicitPairingAndKeepsBearerOutOfReturnedResult() throws Exception {
        var server = start(0);
        server.createContext("/v1/pair", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"state\":\"WAITING\",\"code\":\"315902\",\"expiresAtMillis\":9999999999999,\"token\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/v1/pair/status", exchange -> {
            byte[] body = "{\"state\":\"APPROVED\",\"code\":\"315902\",\"expiresAtMillis\":0,\"token\":\"0123456789abcdef0123456789abcdef\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1));
            var result = client.pair(Duration.ofSeconds(1));
            assertEquals("APPROVED", result.state());
            assertFalse(result.message().contains("0123456789abcdef"));
            assertNull(client.pendingPairCode());
        } finally { server.stop(0); }
    }

    private static HttpServer start(int port) throws Exception { return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0); }
    private static URI base(HttpServer server) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
}

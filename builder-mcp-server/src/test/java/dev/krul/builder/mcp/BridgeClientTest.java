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

    @Test void defaultRequestTimeoutExceedsMinecraftCaptureDeadline() {
        assertEquals(Duration.ofSeconds(10), BridgeClient.defaultRequestTimeout());
    }

    @Test void allowsPrivateWslBridgeAddressButRejectsPublicAndDnsHosts() {
        assertDoesNotThrow(() -> new BridgeClient(URI.create("http://172.28.144.1:47653"), Duration.ofSeconds(1)));
        assertDoesNotThrow(() -> new BridgeClient(URI.create("http://192.168.1.4:47653"), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new BridgeClient(URI.create("http://203.0.113.5:47653"), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new BridgeClient(URI.create("http://example.com:47653"), Duration.ofSeconds(1)));
    }

    @Test void discoversTheWslDefaultGatewayWithoutChangingNativeLoopbackDefault() {
        String routes = "Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT\n"
            + "eth0 00000000 01901CAC 0003 0 0 0 00000000 0 0 0\n";
        assertEquals("http://172.28.144.1:47653", BridgeClient.defaultBridgeUri(null,
            "6.6.87.2-microsoft-standard-WSL2", routes).toString());
        assertEquals("http://127.0.0.1:47653", BridgeClient.defaultBridgeUri(null, "6.8.0", routes).toString());
    }

    @Test void sendsBoundedReadAndCarriesSnapshotIdentity() throws Exception {
        var server = start(0);
        server.createContext("/v1/selection", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("expectedWorldGeneration\":7"));
            byte[] response = "{\"version\":1,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":7,\"snapshotRevision\":9,\"message\":\"ok\",\"payload\":\"{}\",\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1));
            var bounds = new BridgeClient.Bounds(new BridgeClient.Coordinate(0,0,0), new BridgeClient.Coordinate(1,0,0));
            var reply = client.readSelection(bounds, 0, 20, 7, 9);
            assertEquals(7, reply.worldGeneration()); assertEquals(9, reply.snapshotRevision());
        } finally { server.stop(0); }
    }

    @Test void searchesPagedItemCatalogAndReturnsOptionalPngWithoutAuthorization() throws Exception {
        var server = start(0);
        server.createContext("/v1/catalog/items/mods", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"method\":\"LIST_ITEM_MODS\""));
            assertTrue(body.contains("\"page\":1"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"version\":1,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":0,\"snapshotRevision\":0,\"message\":\"ok\",\"payload\":\"{\\\"namespaces\\\":[\\\"mod\\\"]}\",\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/v1/catalog/items/search", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"method\":\"SEARCH_ITEMS\""));
            assertTrue(body.contains("\"page\":2"));
            byte[] response = "{\"version\":1,\"id\":\"r2\",\"ok\":true,\"worldGeneration\":0,\"snapshotRevision\":0,\"message\":\"ok\",\"payload\":\"{}\",\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/v1/catalog/items/describe", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"itemId\":\"mod:widget\""));
            byte[] response = ("{\"version\":1,\"id\":\"r3\",\"ok\":true,\"worldGeneration\":0,\"snapshotRevision\":0,\"message\":\"ok\",\"payload\":\"{\\\"id\\\":\\\"mod:widget\\\"}\",\"imageBase64\":\""+PNG+"\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new BridgeClient(base(server), Duration.ofSeconds(1));
            assertTrue(client.listItemMods(1).payload().contains("mod"));
            assertTrue(client.searchItems("widget", "mod", 2).ok());
            var description = client.describeItem("mod:widget");
            assertNotNull(description.png());
            assertEquals(8, description.png().length);
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
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1));
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
            BridgeClient timedClient = new BridgeClient(base(server), Duration.ofMillis(80));
            assertThrows(BridgeClient.BridgeException.class, timedClient::status);
            BridgeClient imageClient = new BridgeClient(base(server), Duration.ofSeconds(1));
            assertEquals(8, imageClient.captureView(0,0).png().length);
        } finally { server.stop(0); }
    }

    @Test void submitsPlanBoundToExactSnapshotAndPublishesOnlyWithValidationToken() throws Exception {
        var server = start(0);
        server.createContext("/v1/plans/inspect", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"method\":\"INSPECT_PLAN\""));
            assertTrue(body.contains("\"expectedWorldGeneration\":7"));
            assertTrue(body.contains("\"expectedSnapshotRevision\":9"));
            assertTrue(body.contains("\\\"schemaVersion\\\":1"));
            byte[] response = ("{\"version\":1,\"id\":\"r1\",\"ok\":true,\"worldGeneration\":7,\"snapshotRevision\":9,\"message\":\"ok\",\"payload\":\"{\\\"valid\\\":true,\\\"validationToken\\\":\\\"once\\\"}\",\"imageBase64\":\""+PNG+"\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/v1/plans/publish", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"validationToken\":\"once\""));
            byte[] response = "{\"version\":1,\"id\":\"r2\",\"ok\":true,\"worldGeneration\":7,\"snapshotRevision\":9,\"message\":\"ok\",\"payload\":\"{\\\"published\\\":true,\\\"worldMutation\\\":false}\",\"imageBase64\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            BridgeClient client = new BridgeClient(base(server), Duration.ofSeconds(1));
            var inspected = client.inspectPlan("{\"schemaVersion\":1}", 7, 9);
            assertNotNull(inspected.previewPng());
            assertTrue(inspected.metadata().payload().contains("validationToken"));
            var published = client.publishPlan("once", 7, 9);
            assertTrue(published.payload().contains("worldMutation"));
        } finally { server.stop(0); }
    }

    @Test void archivesOnlyCompleteBoundedSnapshotPages() throws Exception {
        var server = start(0);
        server.createContext("/v1/selection", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("\"pageSize\":1024"));
            var payload = new com.google.gson.JsonObject(); payload.addProperty("snapshotRevision", 9); payload.addProperty("total", 2);
            var cells = new com.google.gson.JsonArray(); cells.add(new com.google.gson.JsonObject()); cells.add(new com.google.gson.JsonObject()); payload.add("cells", cells);
            var response = new com.google.gson.JsonObject(); response.addProperty("version", 1); response.addProperty("id", "r");
            response.addProperty("ok", true); response.addProperty("worldGeneration", 7); response.addProperty("snapshotRevision", 9);
            response.addProperty("message", "ok"); response.addProperty("payload", payload.toString()); response.add("imageBase64", com.google.gson.JsonNull.INSTANCE);
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new BridgeClient(base(server), Duration.ofSeconds(1));
            var bounds = new BridgeClient.Bounds(new BridgeClient.Coordinate(0, 64, 0), new BridgeClient.Coordinate(1, 64, 0));
            String archived = client.readSelectionSnapshot(bounds, 7, 9);
            var result = com.google.gson.JsonParser.parseString(archived).getAsJsonObject();
            assertEquals(2, result.get("total").getAsInt()); assertEquals(2, result.getAsJsonArray("cells").size());
            assertThrows(IllegalArgumentException.class, () -> client.readSelectionSnapshot(
                new BridgeClient.Bounds(new BridgeClient.Coordinate(0, 0, 0), new BridgeClient.Coordinate(32_768, 0, 0)), 7, 9));
        } finally { server.stop(0); }
    }

    private static HttpServer start(int port) throws Exception { return HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0); }
    private static URI base(HttpServer server) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
}

package dev.krul.builder.mcp.viewer;

import dev.krul.builder.mcp.archive.PlanArchiveStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlanViewerServerTest {
    @TempDir Path temp;

    @Test void servesOnlyLoopbackTokenizedReadOnlyPlanApi() throws Exception {
        var archive = new PlanArchiveStore(temp.resolve("archive"));
        var planId = UUID.randomUUID();
        var record = new PlanArchiveStore.Record(planId, "Deck", Instant.now(), "test-world", 2, 3,
            "{\"schemaVersion\":1}", "{\"valid\":true}", "{\"cells\":[]}", null);
        archive.save(record);
        var server = new PlanViewerServer(archive, 0); String base = server.start();
        try {
            assertTrue(base.startsWith("http://127.0.0.1:"));
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            var list = client.send(HttpRequest.newBuilder(URI.create(base + "api/plans")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, list.statusCode()); assertTrue(list.body().contains(planId.toString()));
            assertTrue(list.headers().firstValue("Content-Security-Policy").orElseThrow().contains("default-src 'self'"));
            var detail = client.send(HttpRequest.newBuilder(URI.create(base + "api/plans/" + planId)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, detail.statusCode()); assertTrue(detail.body().contains("snapshotJson"));
            var unknown = client.send(HttpRequest.newBuilder(URI.create(base + "api/plans/not-an-id")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, unknown.statusCode());
            var untrusted = client.send(HttpRequest.newBuilder(URI.create(base.replace("/v/", "/v/not-the-token/"))).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, untrusted.statusCode());
        } finally { server.close(); }
    }
}

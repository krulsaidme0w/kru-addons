package dev.krul.builder.visual;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentPolicyTest {
    @TempDir Path temp;

    private Path image(String name, int width, int height) throws Exception {
        var path = temp.resolve(name);
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", path.toFile()));
        return path;
    }

    @Test void fourImagesAcceptedFifthRejectedWithoutDroppingContext() throws Exception {
        var source = image("input.png", 1280, 1);
        try (var policy = new AttachmentPolicy(temp)) {
            for (int i = 0; i < 4; i++) policy.add(source);
            assertEquals(4, policy.draft().size());
            assertThrows(IllegalArgumentException.class, () -> policy.add(source));
            assertEquals(4, policy.draft().size());
            assertEquals(1280, policy.draft().getFirst().width());
            assertThrows(UnsupportedOperationException.class, () -> policy.draft().clear());
        }
        assertTrue(Files.exists(source));
    }

    @Test void rejectsDimensionsInvalidUnsupportedAndTruncatedImages() throws Exception {
        try (var policy = new AttachmentPolicy(temp)) {
            assertThrows(IllegalArgumentException.class, () -> policy.add(image("wide.png", 1281, 1)));
            assertThrows(IllegalArgumentException.class, () -> policy.add(image("tall.png", 1, 1281)));
            var text = Files.writeString(temp.resolve("fake.png"), "not an image");
            assertThrows(IllegalArgumentException.class, () -> policy.add(text));
            var gif = temp.resolve("unsupported.gif");
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "gif", gif.toFile());
            assertThrows(IllegalArgumentException.class, () -> policy.add(gif));
            var png = Files.readAllBytes(image("original.png", 10, 10));
            var truncated = Files.write(temp.resolve("truncated.png"), Arrays.copyOf(png, 40));
            assertThrows(IllegalArgumentException.class, () -> policy.add(truncated));
            assertTrue(policy.draft().isEmpty());
        }
    }

    @Test void combinedEncodedBytesIncludeEveryFileAndAcceptExactLimit() throws Exception {
        var small = Files.readAllBytes(image("source.png", 1, 1));
        // PNG permits trailing bytes: preserves a real decodable image, tests encoded rather than pixel size.
        var half = Files.write(temp.resolve("half.png"), Arrays.copyOf(small, 4 * 1024 * 1024));
        var tooBig = Files.write(temp.resolve("large.png"), Arrays.copyOf(small, 8 * 1024 * 1024 + 1));
        try (var policy = new AttachmentPolicy(temp)) {
            assertThrows(IllegalArgumentException.class, () -> policy.add(tooBig));
            policy.add(half); policy.add(half);
            assertEquals(8 * 1024 * 1024, policy.draft().stream().mapToLong(AttachmentPolicy.Attachment::encodedBytes).sum());
            assertThrows(IllegalArgumentException.class, () -> policy.add(temp.resolve("source.png")));
            assertEquals(2, policy.draft().size());
        }
    }

    @Test void removedDraftSurvivesUntilEveryRequestCompletesAndNeverDeletesOriginal() throws Exception {
        var source = image("reference.png", 8, 4);
        var policy = new AttachmentPolicy(temp);
        var attachment = policy.add(source);
        var request1 = policy.retainForRequest(); var request2 = policy.retainForRequest();
        var finish = new CompletableFuture<Void>();
        request1.releaseWhen(finish);
        policy.remove(attachment.id());
        assertTrue(policy.draft().isEmpty());
        policy.close();
        assertTrue(Files.exists(attachment.path()));
        assertEquals(List.of(attachment.path()), request1.paths());
        finish.completeExceptionally(new IllegalStateException("request failed"));
        assertTrue(Files.exists(attachment.path()));
        request2.close(); request2.close();
        assertFalse(Files.exists(attachment.path()));
        assertTrue(Files.exists(source));
        assertThrows(IllegalStateException.class, policy::retainForRequest);
    }

    @Test void importOwnsImmutableCopyAndCaptureReleaseOnlyTouchesOwnedCaptures() throws Exception {
        var source = image("reference.png", 8, 4);
        try (var policy = new AttachmentPolicy(temp)) {
            var attachment = policy.add(source);
            Files.writeString(source, "changed by user");
            assertNotNull(ImageIO.read(attachment.path().toFile()));
            var capture = policy.storeCapture(new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB));
            var attachedCapture = policy.add(capture);
            policy.releaseCapture(capture);
            policy.releaseCapture(source);
            assertFalse(Files.exists(capture));
            assertTrue(Files.exists(attachedCapture.path()));
            assertTrue(Files.exists(source));
        }
    }

    @Test void crashCleanupIsBoundedSkipsActiveStoresForeignFilesAndSymlinks() throws Exception {
        var source = image("foreign.png", 1, 1);
        try (var live = new AttachmentPolicy(temp)) {
            var livePath = live.add(source).path();
            var stale = Files.createDirectory(temp.resolve("kru-builder-images-123456789"));
            Files.createFile(stale.resolve(".owner.lock"));
            var orphan = Files.copy(source, stale.resolve("image-" + UUID.randomUUID() + ".png"));
            Files.setLastModifiedTime(stale, FileTime.from(Instant.EPOCH));
            var foreignDir = Files.createDirectory(temp.resolve("foreign-dir"));
            var foreign = Files.copy(source, foreignDir.resolve("image-" + UUID.randomUUID() + ".png"));
            Files.createSymbolicLink(temp.resolve("kru-builder-images-987654321"), foreignDir);
            AttachmentPolicy.cleanupStale(temp, Instant.now().plus(Duration.ofDays(2)));
            assertFalse(Files.exists(orphan));
            assertTrue(Files.exists(livePath));
            assertTrue(Files.exists(foreign));
            assertTrue(Files.exists(source));
        }
    }
}

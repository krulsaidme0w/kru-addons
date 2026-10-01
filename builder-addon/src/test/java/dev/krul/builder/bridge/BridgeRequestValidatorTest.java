package dev.krul.builder.bridge;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeRequestValidatorTest {
    private static Bounds bounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(new Cell(x1, y1, z1), new Cell(x2, y2, z2));
    }

    @Test void acceptsSubBoundsAndRejectsBoundsOutsideSelection() {
        assertDoesNotThrow(() -> BridgeRequestValidator.validateSelection(bounds(1, 1, 1, 2, 2, 2), bounds(0, 0, 0, 3, 3, 3), 32_768));
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validateSelection(bounds(-1, 1, 1, 2, 2, 2), bounds(0, 0, 0, 3, 3, 3), 32_768));
    }

    @Test void rejectsSelectionAndRequestVolumeOverflowOrOverLimit() {
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validateSelection(
            bounds(0, 0, 0, 32_768, 0, 0), bounds(0, 0, 0, 32_768, 0, 0), 32_768));
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validateSelection(
            bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0), bounds(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 0, 0), Long.MAX_VALUE));
    }

    @Test void rejectsNegativeAndOutOfRangePages() {
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validatePage(-1, 20, 100));
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validatePage(5, 20, 100));
        assertDoesNotThrow(() -> BridgeRequestValidator.validatePage(4, 20, 100));
    }

    @Test void enforcesPngByteAndSignatureBounds() {
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validatePng(new byte[] {1, 2}, 128));
        assertThrows(IllegalArgumentException.class, () -> BridgeRequestValidator.validatePng(new byte[129], 128));
        assertDoesNotThrow(() -> BridgeRequestValidator.validatePng(new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10}, 128));
    }
}

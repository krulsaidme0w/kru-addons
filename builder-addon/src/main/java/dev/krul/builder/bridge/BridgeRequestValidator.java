package dev.krul.builder.bridge;

import dev.krul.builder.context.ScanCursor;
import dev.krul.builder.model.Bounds;

import java.util.Objects;

/** Fail-closed bounds for requests crossing the local bridge boundary. */
public final class BridgeRequestValidator {
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private BridgeRequestValidator() {}

    public static void validateSelection(Bounds requested, Bounds selected, long maxCells) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(selected, "selected");
        if (maxCells < 1 || maxCells > ScanCursor.MAX_CELLS)
            throw new IllegalArgumentException("Invalid maximum cell count");
        if (!selected.contains(requested.min()) || !selected.contains(requested.max()))
            throw new IllegalArgumentException("Requested bounds exceed selected bounds");
        long volume;
        try { volume = requested.volume(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Requested bounds volume overflow", e); }
        if (volume < 1 || volume > maxCells) throw new IllegalArgumentException("Requested bounds exceed cell limit");
    }

    /** page is zero-based; totalItems may be zero, in which case only page zero exists. */
    public static void validatePage(int page, int pageSize, long totalItems) {
        if (page < 0 || pageSize < 1 || pageSize > 20 || totalItems < 0)
            throw new IllegalArgumentException("Invalid page request");
        long pageCount = totalItems == 0 ? 1 : (totalItems + pageSize - 1) / pageSize;
        if (page >= pageCount) throw new IllegalArgumentException("Page is outside available results");
    }

    public static void validatePng(byte[] png, int maxBytes) {
        if (maxBytes < PNG_SIGNATURE.length || png == null || png.length < PNG_SIGNATURE.length || png.length > maxBytes)
            throw new IllegalArgumentException("PNG is missing or exceeds byte limit");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (png[i] != PNG_SIGNATURE[i])
            throw new IllegalArgumentException("Invalid PNG signature");
    }
}

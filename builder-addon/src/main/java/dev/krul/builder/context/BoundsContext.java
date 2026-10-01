package dev.krul.builder.context;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Stable identity for selection persistence, scoped to one world and dimension. */
public record BoundsContext(Kind kind, String identity, String dimension) {
    public enum Kind { LOCAL_SAVE, REMOTE_SERVER }

    public BoundsContext {
        Objects.requireNonNull(kind, "kind");
        identity = Objects.requireNonNull(identity, "identity").trim();
        dimension = Objects.requireNonNull(dimension, "dimension").trim();
        if (identity.isEmpty() || dimension.isEmpty()) throw new IllegalArgumentException("Context identity and dimension are required");
    }

    public static BoundsContext local(String savePath, String dimension) {
        String normalized = Path.of(savePath).toAbsolutePath().normalize().toString();
        return new BoundsContext(Kind.LOCAL_SAVE, normalized, dimension);
    }

    public static BoundsContext remote(String address, String dimension) {
        return new BoundsContext(Kind.REMOTE_SERVER, address.trim().toLowerCase(Locale.ROOT), dimension);
    }

    String storageKey() {
        return "v1\n" + kind.name() + "\n" + identity.length() + ":" + identity + "\n"
            + dimension.length() + ":" + dimension;
    }
}

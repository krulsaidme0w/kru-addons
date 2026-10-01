package dev.krul.builder.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Stable identity for registry metadata and rendered previews. */
public record BlockCatalogFingerprint(String minecraftVersion, String loaderVersion,
                                      Map<String, String> mods, List<String> resourcePacks, String sha256) {
    public BlockCatalogFingerprint {
        Objects.requireNonNull(minecraftVersion); Objects.requireNonNull(loaderVersion);
        mods = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(mods)));
        resourcePacks = Objects.requireNonNull(resourcePacks).stream().sorted().distinct().toList();
        Objects.requireNonNull(sha256);
        if (!sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid catalog fingerprint");
    }

    public static BlockCatalogFingerprint create(String minecraftVersion, String loaderVersion,
                                                  Map<String, String> mods, List<String> resourcePacks) {
        Objects.requireNonNull(minecraftVersion); Objects.requireNonNull(loaderVersion);
        var sortedMods = new TreeMap<>(Objects.requireNonNull(mods));
        var sortedPacks = Objects.requireNonNull(resourcePacks).stream().sorted().distinct().toList();
        StringBuilder canonical = new StringBuilder("minecraft=").append(minecraftVersion).append('\n')
            .append("loader=").append(loaderVersion).append('\n');
        sortedMods.forEach((id, version) -> canonical.append("mod=").append(id).append('@').append(version).append('\n'));
        sortedPacks.forEach(pack -> canonical.append("pack=").append(pack).append('\n'));
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return new BlockCatalogFingerprint(minecraftVersion, loaderVersion, sortedMods, sortedPacks, hash);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}

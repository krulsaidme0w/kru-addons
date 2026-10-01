package dev.krul.builder.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** In-memory, instance-local one-time pairing. Neither challenges nor tokens are persisted. */
public final class BridgePairing {
    public record Challenge(String code, long expiresAtMillis) {}
    public enum State { NONE, WAITING, APPROVED, REJECTED, EXPIRED }

    private final long lifetimeMillis;
    private final LongSupplier clock;
    private final Supplier<String> challengeFactory, tokenFactory;
    private Challenge pending;
    private State state = State.NONE;
    private String approvedChallenge, token;
    private boolean tokenAvailable;

    public BridgePairing(Duration lifetime, LongSupplier clock, Supplier<String> challengeFactory, Supplier<String> tokenFactory) {
        this.lifetimeMillis = Objects.requireNonNull(lifetime).toMillis();
        if (lifetimeMillis < 1) throw new IllegalArgumentException("Pairing lifetime must be positive");
        this.clock = Objects.requireNonNull(clock);
        this.challengeFactory = Objects.requireNonNull(challengeFactory);
        this.tokenFactory = Objects.requireNonNull(tokenFactory);
    }

    public static BridgePairing secureDefault() {
        SecureRandom random = new SecureRandom();
        return new BridgePairing(Duration.ofMinutes(2), () -> System.nanoTime() / 1_000_000L,
            () -> String.format("%06d", random.nextInt(1_000_000)),
            () -> HexFormat.of().formatHex(random.generateSeed(32)));
    }

    public synchronized Challenge begin() {
        expireIfNeeded();
        if (state == State.WAITING) throw new IllegalStateException("A pairing request is already pending");
        String code = Objects.requireNonNull(challengeFactory.get());
        if (!code.matches("[0-9]{6}")) throw new IllegalStateException("Pairing challenge generator returned an invalid code");
        pending = new Challenge(code, Math.addExact(clock.getAsLong(), lifetimeMillis));
        approvedChallenge = null; token = null; tokenAvailable = false; state = State.WAITING;
        return pending;
    }

    public synchronized boolean approve(String code) {
        expireIfNeeded();
        if (state != State.WAITING || pending == null || !pending.code().equals(code)) return false;
        token = Objects.requireNonNull(tokenFactory.get());
        if (token.length() < 32 || token.length() > 256) throw new IllegalStateException("Pairing token generator returned an invalid token");
        approvedChallenge = code; tokenAvailable = true; pending = null; state = State.APPROVED;
        return true;
    }

    public synchronized boolean reject(String code) {
        expireIfNeeded();
        if (state != State.WAITING || pending == null || !pending.code().equals(code)) return false;
        pending = null; state = State.REJECTED; return true;
    }

    /** The companion polls its pending challenge and receives the bearer token exactly once. */
    public synchronized String takeApprovedToken(String code) {
        expireIfNeeded();
        if (state != State.APPROVED || !tokenAvailable || !Objects.equals(approvedChallenge, code)) return null;
        tokenAvailable = false; approvedChallenge = null;
        return token;
    }

    public synchronized boolean authenticate(String candidate) {
        return candidate != null && token != null && MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized void revoke() {
        pending = null; approvedChallenge = null; token = null; tokenAvailable = false; state = State.NONE;
    }

    public synchronized State state() { expireIfNeeded(); return state; }
    public synchronized Challenge pending() { expireIfNeeded(); return pending; }

    private void expireIfNeeded() {
        if (state == State.WAITING && pending != null && clock.getAsLong() >= pending.expiresAtMillis()) {
            pending = null; state = State.EXPIRED;
        }
    }
}

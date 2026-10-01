package dev.krul.builder.bridge;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class BridgePairingTest {
    @Test void approvalIssuesReusableTokenUntilRevoked() {
        AtomicLong now = new AtomicLong(100);
        var pairing = new BridgePairing(Duration.ofSeconds(60), now::get, () -> "123456", () -> "0123456789abcdef0123456789abcdef");
        var challenge = pairing.begin();
        assertTrue(pairing.approve(challenge.code()));
        String token = pairing.takeApprovedToken(challenge.code());
        assertTrue(pairing.authenticate(token));
        assertTrue(pairing.authenticate(token));
        pairing.revoke();
        assertFalse(pairing.authenticate(challenge.code()));
    }

    @Test void challengeIsSingleUseAndExpires() {
        AtomicLong now = new AtomicLong(100);
        var pairing = new BridgePairing(Duration.ofSeconds(1), now::get, () -> "123456", () -> "pairing-token");
        var challenge = pairing.begin();
        now.set(1_101);
        assertFalse(pairing.approve(challenge.code()));
        assertFalse(pairing.approve(challenge.code()));
    }

    @Test void rejectionAndReplayCannotAuthorize() {
        var pairing = new BridgePairing(Duration.ofSeconds(30), () -> 0L, () -> "123456", () -> "unique-secret-token-value");
        var challenge = pairing.begin();
        assertTrue(pairing.reject(challenge.code()));
        assertFalse(pairing.approve(challenge.code()));
        assertFalse(pairing.authenticate(challenge.code()));
    }
}
